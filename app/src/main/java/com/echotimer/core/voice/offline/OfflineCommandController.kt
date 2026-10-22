package com.echotimer.core.voice.offline

import com.echotimer.core.settings.OfflineSensitivity
import com.echotimer.core.voice.VoiceCommand
import com.echotimer.core.voice.VoiceCommandSource
import com.echotimer.core.voice.VoiceStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 自研离线口令识别控制器。
 *
 * 不依赖任何系统语音服务：麦克风采集 → 端点检测 → MFCC → 与模板做 DTW → 命中即派发指令。
 * 适合国行 ColorOS / HyperOS 这类没有 `RecognitionService` 的设备。
 *
 * 线程模型：采集与特征计算在后台线程（[Dispatchers.Default]），
 * 指令回调切回主线程，避免调用方（秒表引擎）被并发访问。
 */
class OfflineCommandController(
    private val store: OfflineVoiceStore,
    private val sensitivity: () -> OfflineSensitivity,
    private val onCommand: (VoiceCommand) -> Unit,
    private val mainDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.Main.immediate,
) : VoiceCommandSource {

    private val _status = MutableStateFlow(VoiceStatus.OFF)
    override val status: StateFlow<VoiceStatus> = _status.asStateFlow()

    private val _rmsLevel = MutableStateFlow(0f)
    override val rmsLevel: StateFlow<Float> = _rmsLevel.asStateFlow()

    /** 离线匹配与语言无关，保留这个属性只是为了统一接口。 */
    override var languageTag: String = "zh-CN"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    private var capture: AudioCapture? = null

    @Volatile
    private var suspended = false

    override fun start() {
        if (job != null) return
        val templates = store.templates.value
        if (templates.isEmpty()) {
            // 还没录过口令：不启动麦克风，直接给出"不可用"，由界面引导去训练。
            _status.value = VoiceStatus.UNAVAILABLE
            return
        }
        // 注意：这里不重置 suspended。重建来源时 ViewModel 会先把"是否正在播报"写进来，
        // 如果这里强行置 false，TTS 播报期间就会把自己的声音当口令。
        _status.value = if (suspended) VoiceStatus.SUSPENDED else VoiceStatus.LISTENING
        job = scope.launch { listenLoop() }
    }

    override fun stop() {
        job?.cancel()
        job = null
        capture?.stop()
        capture = null
        _rmsLevel.value = 0f
        _status.value = VoiceStatus.OFF
    }

    override fun release() {
        stop()
        scope.cancel()
    }

    override fun setSuspended(value: Boolean) {
        if (suspended == value) return
        suspended = value
        if (job == null) return
        _status.value = if (value) VoiceStatus.SUSPENDED else VoiceStatus.LISTENING
        if (value) _rmsLevel.value = 0f
    }

    private suspend fun listenLoop() {
        // 记下"我是哪个 job"：stop() 之后可能立刻 start()，
        // 旧循环的 finally 不能把新 job 的引用清掉，否则会留下没人管的采集线程。
        val thisJob = kotlinx.coroutines.currentCoroutineContext()[Job]
        val audio = AudioCapture()
        capture = audio
        if (!audio.start()) {
            _status.value = VoiceStatus.PERMISSION_DENIED
            capture = null
            job = null
            return
        }

        // 先听 1 秒环境噪声：室内和户外的底噪能差一个数量级，
        // 用固定初值会让户外一直"以为有人在说话"。
        val noiseFloor = calibrateNoiseFloor(audio)
        val buffer = UtteranceBuffer(
            UtteranceSegmenter(
                chunkMs = audio.chunkDurationMs,
                initialNoiseFloor = noiseFloor,
            ),
        )
        var lastCommandAtMs = 0L

        try {
            while (currentCoroutineContext().isActive) {
                val chunk = audio.readChunk() ?: break
                if (suspended) {
                    // 播报期间继续读，但直接丢弃，避免音频缓冲堆积。
                    buffer.reset()
                    _rmsLevel.value = 0f
                    continue
                }
                val rms = AudioLevel.rms(chunk)
                _rmsLevel.value = AudioLevel.toUiDb(rms)
                val utterance = buffer.accept(chunk, rms) ?: continue
                val trimmed = Mfcc.trimSilence(utterance)
                if (trimmed.size < MIN_SAMPLES) continue
                val frames = Mfcc.extract(trimmed)
                val templates = store.templates.value
                if (templates.isEmpty()) continue
                val match = VoiceMatcher.match(frames, templates, sensitivity().ratio) ?: continue
                val now = System.currentTimeMillis()
                if (now - lastCommandAtMs < COMMAND_DEBOUNCE_MS) continue
                lastCommandAtMs = now
                withContextMain { onCommand(match.command) }
            }
        } finally {
            audio.stop()
            capture = null
            _rmsLevel.value = 0f
            if (job === thisJob) {
                job = null
                _status.value = VoiceStatus.OFF
            }
        }
    }

    /** 静音 1 秒取平均 RMS 作为噪声底；读不到就回落到默认值。 */
    private fun calibrateNoiseFloor(audio: AudioCapture): Double {
        var sum = 0.0
        var count = 0
        repeat(CALIBRATION_CHUNKS) {
            val chunk = audio.readChunk() ?: return@repeat
            sum += AudioLevel.rms(chunk)
            count++
        }
        if (count == 0) return DEFAULT_NOISE_FLOOR
        // 取平均再抬一点，避免把偶发的环境声当成语音。
        return (sum / count * 1.2).coerceIn(MIN_NOISE_FLOOR, MAX_NOISE_FLOOR)
    }

    private suspend fun withContextMain(block: () -> Unit) {
        kotlinx.coroutines.withContext(mainDispatcher) { block() }
    }

    private companion object {
        /** 太短的片段（<0.12s）不送匹配，避免咳嗽、碰麦被当成口令。 */
        const val MIN_SAMPLES = 1_920
        const val COMMAND_DEBOUNCE_MS = 1_200L
        const val CALIBRATION_CHUNKS = 50
        const val DEFAULT_NOISE_FLOOR = 250.0
        const val MIN_NOISE_FLOOR = 60.0
        const val MAX_NOISE_FLOOR = 2_000.0
    }
}
