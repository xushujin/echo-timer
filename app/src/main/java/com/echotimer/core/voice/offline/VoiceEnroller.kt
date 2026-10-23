package com.echotimer.core.voice.offline

import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/**
 * 录一条口令模板（训练用）。
 *
 * 与实时监听共用 [UtteranceBuffer]，保证"训练时怎么切"和"识别时怎么切"完全一致——
 * 这一点很重要，切法不同会让模板和实际输入的特征分布对不上。
 *
 * 录音前先静音 0.3 秒估噪声底，并且用比实时监听更宽松的触发门限：
 * 训练时用户是主动开口的，宁可多录一点，也不要把"说话声音偏小"判成没说话。
 */
class VoiceEnroller {

    private val _level = MutableStateFlow(-2f)

    /** 实时电平（与界面呼吸动画同一刻度），录音时用来给用户反馈。 */
    val level: StateFlow<Float> = _level.asStateFlow()

    private val _peakDbfs = MutableStateFlow(-90.0)

    /** 本次录音的峰值（dBFS，-90..0）：界面直接显示，用来判断麦克风有没有收到声音。 */
    val peakDbfs: StateFlow<Double> = _peakDbfs.asStateFlow()

    private val _triggerDbfs = MutableStateFlow(-90.0)

    /** 当前端点检测的触发门限（dBFS）：峰值到不了这个值就不会开始录音。 */
    val triggerDbfs: StateFlow<Double> = _triggerDbfs.asStateFlow()

    sealed interface Result {
        data class Success(
            val frames: List<FloatArray>,
            val durationMs: Int,
            /** 这次录音的峰值 RMS，用于判断麦克风到底有没有收到声音。 */
            val peakRms: Double,
            /** 端点检测使用的触发门限 RMS。 */
            val triggerRms: Double,
        ) : Result

        /** 没检测到说话。带上峰值与门限，界面才能说清是"没声音"还是"太小声"。 */
        data class NoSpeech(
            val peakRms: Double,
            val triggerRms: Double,
            val waitedMs: Long,
        ) : Result

        /** 麦克风打不开（权限被拒，或被其它应用占用）。 */
        data object Failed : Result
    }

    /**
     * 录一段，最多等 [waitSpeechMs] 让人开口。
     * 返回的 [Result.Success.frames] 已经是可直接入库的 MFCC 特征。
     */
    suspend fun record(waitSpeechMs: Long = 5_000): Result = withContext(Dispatchers.Default) {
        val audio = AudioCapture()
        if (!audio.start()) return@withContext Result.Failed

        _peakDbfs.value = -90.0
        try {
            val noiseFloor = calibrateNoiseFloor(audio)
            val segmenter = UtteranceSegmenter(
                chunkMs = audio.chunkDurationMs,
                triggerRatio = ENROLL_TRIGGER_RATIO,
                minTriggerRms = ENROLL_MIN_TRIGGER_RMS,
                initialNoiseFloor = noiseFloor,
            )
            _triggerDbfs.value = AudioLevel.toDbfs(segmenter.triggerLevel)
            val buffer = UtteranceBuffer(segmenter)
            val startedAt = SystemClock.elapsedRealtime()
            var peakRms = 0.0

            while (currentCoroutineContext().isActive) {
                val chunk = audio.readChunk() ?: return@withContext Result.Failed
                val rms = AudioLevel.rms(chunk)
                if (rms > peakRms) {
                    peakRms = rms
                    _peakDbfs.value = AudioLevel.toDbfs(rms)
                }
                _level.value = AudioLevel.toUiDb(rms)

                val utterance = buffer.accept(chunk, rms)
                if (utterance != null) {
                    return@withContext toResult(utterance, peakRms, segmenter.triggerLevel)
                }

                val elapsed = SystemClock.elapsedRealtime() - startedAt
                if (!buffer.isSpeaking && elapsed > waitSpeechMs) {
                    return@withContext Result.NoSpeech(peakRms, segmenter.triggerLevel, elapsed)
                }
                if (elapsed > HARD_TIMEOUT_MS) {
                    return@withContext Result.NoSpeech(peakRms, segmenter.triggerLevel, elapsed)
                }
            }
            Result.NoSpeech(peakRms, segmenter.triggerLevel, waitSpeechMs)
        } finally {
            audio.stop()
            _level.value = -2f
        }
    }

    /** 静音 0.3 秒估噪声底；读不到就回落到默认值。 */
    private fun calibrateNoiseFloor(audio: AudioCapture): Double {
        var sum = 0.0
        var count = 0
        repeat(CALIBRATION_CHUNKS) {
            val chunk = audio.readChunk() ?: return@repeat
            sum += AudioLevel.rms(chunk)
            count++
        }
        if (count == 0) return DEFAULT_NOISE_FLOOR
        return (sum / count).coerceIn(MIN_NOISE_FLOOR, MAX_NOISE_FLOOR)
    }

    private fun toResult(utterance: ShortArray, peakRms: Double, triggerRms: Double): Result {
        val trimmed = Mfcc.trimSilence(utterance, threshold = TRIM_THRESHOLD)
        if (trimmed.size < MIN_SAMPLES) {
            return Result.NoSpeech(peakRms, triggerRms, 0L)
        }
        val frames = Mfcc.extract(trimmed)
        if (frames.size < MIN_FRAMES) {
            return Result.NoSpeech(peakRms, triggerRms, 0L)
        }
        val durationMs = trimmed.size * 1_000 / AudioCapture.SAMPLE_RATE
        return Result.Success(frames, durationMs, peakRms, triggerRms)
    }

    private companion object {
        /** 训练时比实时监听更灵敏：用户就在等提示音开口。 */
        const val ENROLL_TRIGGER_RATIO = 2.0
        const val ENROLL_MIN_TRIGGER_RMS = 250.0

        /** 少于 0.12 秒的片段没有训练价值。 */
        const val MIN_SAMPLES = 1_920
        const val MIN_FRAMES = 8
        const val HARD_TIMEOUT_MS = 9_000L

        const val CALIBRATION_CHUNKS = 15
        const val DEFAULT_NOISE_FLOOR = 120.0
        const val MIN_NOISE_FLOOR = 40.0
        const val MAX_NOISE_FLOOR = 800.0

        /** 裁剪静音的幅度阈值；训练时放宽一点，避免轻声说话被裁光。 */
        const val TRIM_THRESHOLD = 300
    }
}
