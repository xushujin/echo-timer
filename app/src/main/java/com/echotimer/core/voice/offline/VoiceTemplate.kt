package com.echotimer.core.voice.offline

import com.echotimer.core.voice.VoiceCommand
import java.util.Base64

/**
 * 一条口令模板：某条指令的特征帧序列。
 *
 * 只存特征（MFCC），不存原始音频——原始音频体积大，而且没有保留的必要（隐私考虑）。
 */
data class VoiceTemplate(
    val command: VoiceCommand,
    val frames: List<FloatArray>,
    val updatedAt: Long,
)

/**
 * 模板的序列化。
 *
 * 格式：`[version:1][dim:1][frameCount:2][int16 * dim * frameCount]`，整体 Base64。
 * 特征值放大 1000 倍存成 int16（CMN 之后取值范围远小于 ±32），
 * 5 条口令合计约 15KB，放 SharedPreferences 完全够用。
 *
 * 自己写而不是用 JSON：体积小一个数量级，且反序列化能严格校验脏数据。
 */
object VoiceTemplateCodec {

    private const val VERSION = 1
    private const val SCALE = 1000f
    private const val HEADER_BYTES = 4

    fun encode(frames: List<FloatArray>): String {
        if (frames.isEmpty()) return ""
        val dim = frames[0].size
        val count = frames.size
        val bytes = ByteArray(HEADER_BYTES + dim * count * 2)
        bytes[0] = VERSION.toByte()
        bytes[1] = dim.toByte()
        bytes[2] = (count shr 8).toByte()
        bytes[3] = count.toByte()

        var index = HEADER_BYTES
        for (frame in frames) {
            for (i in 0 until dim) {
                val value = (frame[i] * SCALE).toInt().coerceIn(-32768, 32767)
                bytes[index++] = (value shr 8).toByte()
                bytes[index++] = value.toByte()
            }
        }
        return Base64.getEncoder().encodeToString(bytes)
    }

    /** 解析失败（空串、版本不符、长度对不上）一律返回 null，调用方按"未训练"处理。 */
    fun decode(raw: String?): List<FloatArray>? {
        if (raw.isNullOrBlank()) return null
        val bytes = try {
            Base64.getDecoder().decode(raw)
        } catch (_: IllegalArgumentException) {
            return null
        }
        if (bytes.size < HEADER_BYTES) return null
        if (bytes[0].toInt() and 0xFF != VERSION) return null

        val dim = bytes[1].toInt() and 0xFF
        val count = ((bytes[2].toInt() and 0xFF) shl 8) or (bytes[3].toInt() and 0xFF)
        if (dim <= 0 || count <= 0) return null
        if (bytes.size != HEADER_BYTES + dim * count * 2) return null

        val frames = ArrayList<FloatArray>(count)
        var index = HEADER_BYTES
        for (frameIndex in 0 until count) {
            val frame = FloatArray(dim)
            for (i in 0 until dim) {
                val high = bytes[index++].toInt() and 0xFF
                val low = bytes[index++].toInt() and 0xFF
                val value = ((high shl 8) or low).toShort()
                frame[i] = value / SCALE
            }
            frames.add(frame)
        }
        return frames
    }
}
