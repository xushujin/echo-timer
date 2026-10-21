package com.echotimer.core.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 麦克风监听状态，用于界面上的状态提示。 */
enum class VoiceStatus {
    /** 未开启。 */
    OFF,

    /** 正在监听。 */
    LISTENING,

    /** 播报期间临时让出麦克风。 */
    SUSPENDED,

    /** 设备不支持语音识别。 */
    UNAVAILABLE,

    /** 缺少录音权限。 */
    PERMISSION_DENIED,

    /** 反复出错，已暂停重试。 */
    ERROR,
}

/**
 * 语音指令控制器。
 *
 * 职责边界（与 README「语音指令」一节一致）：
 * - 识别引擎用系统 [SpeechRecognizer]（平台 API，非第三方库），它只负责"把声音变成文本"；
 * - 文本怎么变成动作（归一化、否定词、优先级、去抖）由 [VoiceCommandParser] 与本类负责；
 * - 本类还负责：连续监听的重启调度、错误退避、播报期间让出麦克风、结果去重。
 *
 * 线程约束：所有方法必须在主线程调用（[SpeechRecognizer] 的要求）。
 */
class SpeechCommandController(
    private val context: Context,
    languageTag: String,
    private val onCommand: (VoiceCommand) -> Unit,
) {

    private val handler = Handler(Looper.getMainLooper())

    private val _status = MutableStateFlow(VoiceStatus.OFF)
    val status: StateFlow<VoiceStatus> = _status.asStateFlow()

    /** 识别引擎上报的麦克风音量（dB），用于界面上的呼吸动画。 */
    private val _rmsLevel = MutableStateFlow(0f)
    val rmsLevel: StateFlow<Float> = _rmsLevel.asStateFlow()

    /** 识别语言，可在设置里切换，无需重建控制器。 */
    var languageTag: String = languageTag
        set(value) {
            if (field == value) return
            field = value
            if (running) restartSoon(RESTART_IMMEDIATELY_MS)
        }

    private var recognizer: SpeechRecognizer? = null
    private var running = false
    private var suspended = false
    private var consecutiveFailures = 0
    private var lastCommand: VoiceCommand? = null
    private var lastCommandAtMs = 0L

    private val restartTask = Runnable { listenNow() }

    fun start() {
        if (running) return
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _status.value = VoiceStatus.UNAVAILABLE
            return
        }
        running = true
        consecutiveFailures = 0
        ensureRecognizer()
        handler.post(restartTask)
    }

    fun stop() {
        running = false
        suspended = false
        handler.removeCallbacks(restartTask)
        recognizer?.cancel()
        _rmsLevel.value = 0f
        _status.value = VoiceStatus.OFF
    }

    fun release() {
        stop()
        recognizer?.destroy()
        recognizer = null
    }

    /**
     * 播报期间让出麦克风：置 true 立即取消当前识别，置 false 冷却一小段时间后重新监听。
     * 冷却期是必要的——TTS 尾音和扬声器余响会被麦克风拾到，否则会把"10 秒"识别成指令。
     */
    fun setSuspended(value: Boolean) {
        if (suspended == value) return
        suspended = value
        if (!running) return
        handler.removeCallbacks(restartTask)
        if (value) {
            recognizer?.cancel()
            _rmsLevel.value = 0f
            _status.value = VoiceStatus.SUSPENDED
        } else {
            handler.postDelayed(restartTask, RESUME_COOLDOWN_MS)
        }
    }

    private fun ensureRecognizer() {
        if (recognizer != null) return
        recognizer = SpeechRecognizer.createSpeechRecognizer(context).also {
            it.setRecognitionListener(recognitionListener)
        }
    }

    private fun listenNow() {
        if (!running || suspended) return
        val recognizer = this.recognizer ?: return
        try {
            recognizer.startListening(buildIntent())
            _status.value = VoiceStatus.LISTENING
        } catch (_: SecurityException) {
            running = false
            _status.value = VoiceStatus.PERMISSION_DENIED
        }
    }

    private fun restartSoon(delayMs: Long) {
        if (!running) return
        handler.removeCallbacks(restartTask)
        handler.postDelayed(restartTask, delayMs)
    }

    private fun buildIntent(): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, MAX_RESULTS)
    }

    /** 识别结果去重：部分结果与最终结果可能给出同一条指令。 */
    private fun dispatch(command: VoiceCommand) {
        val now = SystemClock.elapsedRealtime()
        if (command == lastCommand && now - lastCommandAtMs < COMMAND_DEBOUNCE_MS) return
        lastCommand = command
        lastCommandAtMs = now
        onCommand(command)
    }

    private fun handleTexts(texts: List<String>?) {
        if (texts.isNullOrEmpty()) return
        for (text in texts) {
            val command = VoiceCommandParser.parse(text)
            if (command != null) {
                dispatch(command)
                return
            }
        }
    }

    private val recognitionListener = object : RecognitionListener {

        override fun onReadyForSpeech(params: Bundle?) {
            consecutiveFailures = 0
            _status.value = VoiceStatus.LISTENING
        }

        override fun onBeginningOfSpeech() = Unit

        override fun onRmsChanged(rmsdB: Float) {
            _rmsLevel.value = rmsdB
        }

        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onEndOfSpeech() = Unit

        override fun onError(error: Int) {
            _rmsLevel.value = 0f
            when (error) {
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                    running = false
                    _status.value = VoiceStatus.PERMISSION_DENIED
                }

                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
                -> {
                    // 没人说话是常态，立刻重新监听即可，不算失败。
                    _status.value = VoiceStatus.LISTENING
                    restartSoon(RESTART_IMMEDIATELY_MS)
                }

                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                    consecutiveFailures++
                    restartSoon(BUSY_BACKOFF_MS * consecutiveFailures.coerceAtMost(4))
                }

                else -> {
                    consecutiveFailures++
                    if (consecutiveFailures >= MAX_FAILURES) {
                        running = false
                        _status.value = VoiceStatus.ERROR
                    } else {
                        _status.value = VoiceStatus.LISTENING
                        restartSoon(NETWORK_BACKOFF_MS)
                    }
                }
            }
        }

        override fun onResults(results: Bundle?) {
            _rmsLevel.value = 0f
            handleTexts(results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION))
            restartSoon(RESULT_RESTART_MS)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            // 部分结果命中就立即响应，比等最终结果更快（说"结束"不用等静音判定）。
            handleTexts(partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION))
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    private companion object {
        const val MAX_RESULTS = 3
        const val COMMAND_DEBOUNCE_MS = 1_200L
        const val RESULT_RESTART_MS = 120L
        const val RESTART_IMMEDIATELY_MS = 80L
        const val RESUME_COOLDOWN_MS = 320L
        const val BUSY_BACKOFF_MS = 400L
        const val NETWORK_BACKOFF_MS = 1_500L
        const val MAX_FAILURES = 5
    }
}
