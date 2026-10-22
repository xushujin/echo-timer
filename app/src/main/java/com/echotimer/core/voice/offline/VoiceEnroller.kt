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
 */
class VoiceEnroller {

    private val _level = MutableStateFlow(-2f)

    /** 实时电平（与界面呼吸动画同一刻度），录音时用来给用户反馈。 */
    val level: StateFlow<Float> = _level.asStateFlow()

    sealed interface Result {
        data class Success(val frames: List<FloatArray>, val durationMs: Int) : Result

        /** 一直没检测到说话（或太短）。 */
        data object NoSpeech : Result

        /** 麦克风打不开。 */
        data object Failed : Result
    }

    /**
     * 录一段，最多等 [waitSpeechMs] 让人开口。
     * 返回的 [Result.Success.frames] 已经是可直接入库的 MFCC 特征。
     */
    suspend fun record(waitSpeechMs: Long = 4_000): Result = withContext(Dispatchers.Default) {
        val audio = AudioCapture()
        if (!audio.start()) return@withContext Result.Failed
        val buffer = UtteranceBuffer(UtteranceSegmenter(chunkMs = audio.chunkDurationMs))
        val startedAt = SystemClock.elapsedRealtime()
        try {
            while (currentCoroutineContext().isActive) {
                val chunk = audio.readChunk() ?: return@withContext Result.Failed
                val rms = AudioLevel.rms(chunk)
                _level.value = AudioLevel.toUiDb(rms)
                val utterance = buffer.accept(chunk, rms)
                if (utterance != null) {
                    return@withContext toResult(utterance)
                }
                val elapsed = SystemClock.elapsedRealtime() - startedAt
                if (!buffer.isSpeaking && elapsed > waitSpeechMs) return@withContext Result.NoSpeech
                if (elapsed > HARD_TIMEOUT_MS) return@withContext Result.NoSpeech
            }
            Result.NoSpeech
        } finally {
            audio.stop()
            _level.value = -2f
        }
    }

    private fun toResult(utterance: ShortArray): Result {
        val trimmed = Mfcc.trimSilence(utterance)
        if (trimmed.size < MIN_SAMPLES) return Result.NoSpeech
        val frames = Mfcc.extract(trimmed)
        if (frames.size < MIN_FRAMES) return Result.NoSpeech
        val durationMs = trimmed.size * 1_000 / AudioCapture.SAMPLE_RATE
        return Result.Success(frames, durationMs)
    }

    private companion object {
        /** 少于 0.12 秒的片段没有训练价值。 */
        const val MIN_SAMPLES = 1_920
        const val MIN_FRAMES = 8
        const val HARD_TIMEOUT_MS = 9_000L
    }
}
