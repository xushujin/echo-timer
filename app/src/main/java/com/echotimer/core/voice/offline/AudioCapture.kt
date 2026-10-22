package com.echotimer.core.voice.offline

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder

/**
 * 麦克风采集：16kHz / 单声道 / PCM16，按 20ms 一块返回。
 *
 * 音源用 `VOICE_RECOGNITION`：系统会做降噪与自动增益，比 `MIC` 更适合口令识别，
 * 且不会像 `VOICE_COMMUNICATION` 那样强制走通话回声消除。
 */
class AudioCapture(val sampleRate: Int = SAMPLE_RATE) {

    private var record: AudioRecord? = null

    /** 每块采样数（20ms）。 */
    val chunkSamples: Int = sampleRate / CHUNK_PER_SECOND

    val chunkDurationMs: Int = CHUNK_MS

    /**
     * 打开麦克风。返回 false 表示设备打不开或没有权限；
     * 这里不抛异常，交给调用方根据返回值设置 [com.echotimer.core.voice.VoiceStatus]。
     */
    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        if (record != null) return true
        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuffer <= 0) return false
        val bufferBytes = maxOf(minBuffer, chunkSamples * 2 * 8)
        val created = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferBytes,
            )
        } catch (_: IllegalArgumentException) {
            return false
        } catch (_: SecurityException) {
            return false
        }
        if (created.state != AudioRecord.STATE_INITIALIZED) {
            created.release()
            return false
        }
        return try {
            created.startRecording()
            record = created
            true
        } catch (_: SecurityException) {
            created.release()
            false
        } catch (_: IllegalStateException) {
            created.release()
            false
        }
    }

    /** 阻塞读取一块；出错返回 null。 */
    fun readChunk(): ShortArray? {
        val current = record ?: return null
        val buffer = ShortArray(chunkSamples)
        val read = try {
            current.read(buffer, 0, buffer.size)
        } catch (_: IllegalStateException) {
            return null
        }
        return if (read > 0) buffer else null
    }

    fun stop() {
        val current = record ?: return
        record = null
        try {
            current.stop()
        } catch (_: IllegalStateException) {
            // 已经停了，忽略
        } finally {
            current.release()
        }
    }

    companion object {
        const val SAMPLE_RATE = 16_000
        private const val CHUNK_PER_SECOND = 50
        const val CHUNK_MS = 20
    }
}
