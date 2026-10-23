package com.echotimer.core.voice.offline

import com.echotimer.core.voice.VoiceCommand
import java.util.Base64

/**
 * 一条口令模板：某条指令的**一次**录音特征帧序列。
 *
 * 同一条口令可以有多条模板（多录几遍），匹配时取距离最近的那条——
 * 同一句话说两遍本来就不一样，单条模板对语速/音量的变化很敏感。
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
 * v2 格式（一条命令可存多遍）：
 * `[version=2][dim:1][takeCount:1]` + 每遍 `[frameCount:2][int16 * dim * frameCount]`，整体 Base64。
 *
 * 仍然能读 v1（`[version=1][dim][frameCount][int16…]`），按"单遍"处理，
 * 这样已经录过口令的用户升级后不用重录。
 *
 * 自己写而不是用 JSON：体积小一个数量级，且反序列化能严格校验脏数据。
 */
object VoiceTemplateCodec {

    private const val VERSION_LEGACY = 1
    private const val VERSION = 2
    private const val SCALE = 1000f

    /** 单条命令最多保留几遍录音；再多收益很小，反而会拖慢匹配。 */
    const val MAX_TAKES = 2

    /** 编码多条模板；空列表返回空串。 */
    fun encode(takes: List<List<FloatArray>>): String {
        val valid = takes.filter { it.isNotEmpty() }
        if (valid.isEmpty()) return ""
        val dim = valid[0][0].size
        val takeCount = valid.size.coerceAtMost(255)

        var size = 3
        for (index in 0 until takeCount) size += 2 + valid[index].size * dim * 2
        val bytes = ByteArray(size)
        bytes[0] = VERSION.toByte()
        bytes[1] = dim.toByte()
        bytes[2] = takeCount.toByte()

        var offset = 3
        for (index in 0 until takeCount) {
            val frames = valid[index]
            bytes[offset++] = (frames.size shr 8).toByte()
            bytes[offset++] = frames.size.toByte()
            for (frame in frames) {
                for (i in 0 until dim) {
                    val value = (frame[i] * SCALE).toInt().coerceIn(-32768, 32767)
                    bytes[offset++] = (value shr 8).toByte()
                    bytes[offset++] = value.toByte()
                }
            }
        }
        return Base64.getEncoder().encodeToString(bytes)
    }

    /** 解析成"每遍一组帧"；失败返回 null。 */
    fun decodeTakes(raw: String?): List<List<FloatArray>>? {
        if (raw.isNullOrBlank()) return null
        val bytes = try {
            Base64.getDecoder().decode(raw)
        } catch (_: IllegalArgumentException) {
            return null
        }
        if (bytes.size < 3) return null

        return when (bytes[0].toInt() and 0xFF) {
            VERSION_LEGACY -> decodeLegacy(bytes)
            VERSION -> decodeV2(bytes)
            else -> null
        }
    }

    private fun decodeLegacy(bytes: ByteArray): List<List<FloatArray>>? {
        if (bytes.size < 4) return null
        val dim = bytes[1].toInt() and 0xFF
        val count = ((bytes[2].toInt() and 0xFF) shl 8) or (bytes[3].toInt() and 0xFF)
        if (dim <= 0 || count <= 0) return null
        if (bytes.size != 4 + dim * count * 2) return null
        val frames = readFrames(bytes, 4, dim, count) ?: return null
        return listOf(frames)
    }

    private fun decodeV2(bytes: ByteArray): List<List<FloatArray>>? {
        if (bytes.size < 3) return null
        val dim = bytes[1].toInt() and 0xFF
        val takeCount = bytes[2].toInt() and 0xFF
        if (dim <= 0 || takeCount <= 0) return null

        var offset = 3
        val takes = ArrayList<List<FloatArray>>(takeCount)
        repeat(takeCount) {
            if (offset + 2 > bytes.size) return null
            val count = ((bytes[offset].toInt() and 0xFF) shl 8) or (bytes[offset + 1].toInt() and 0xFF)
            offset += 2
            if (count <= 0) return null
            if (offset + count * dim * 2 > bytes.size) return null
            val frames = readFrames(bytes, offset, dim, count) ?: return null
            offset += count * dim * 2
            takes.add(frames)
        }
        return takes.takeIf { offset == bytes.size }
    }

    private fun readFrames(bytes: ByteArray, start: Int, dim: Int, count: Int): List<FloatArray>? {
        if (start + count * dim * 2 > bytes.size) return null
        var offset = start
        val frames = ArrayList<FloatArray>(count)
        for (frameIndex in 0 until count) {
            val frame = FloatArray(dim)
            for (i in 0 until dim) {
                val high = bytes[offset++].toInt() and 0xFF
                val low = bytes[offset++].toInt() and 0xFF
                val value = ((high shl 8) or low).toShort()
                frame[i] = value / SCALE
            }
            frames.add(frame)
        }
        return frames
    }
}
