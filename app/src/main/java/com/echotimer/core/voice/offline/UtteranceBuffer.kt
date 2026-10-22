package com.echotimer.core.voice.offline

/**
 * 把音频块拼成一段完整的话。
 *
 * 保留了 [preRollChunks] 块"开口前"的音频：端点检测靠能量触发，等检测到开口时
 * 声母往往已经过去 100~200ms，不留前摇会把"开"字切掉，直接影响 MFCC 形状。
 */
class UtteranceBuffer(
    private val segmenter: UtteranceSegmenter = UtteranceSegmenter(),
    private val preRollChunks: Int = 10,
) {

    private val preRoll = ArrayDeque<ShortArray>()
    private var active: MutableList<ShortArray>? = null

    val isSpeaking: Boolean get() = active != null

    fun reset() {
        preRoll.clear()
        active = null
        segmenter.reset()
    }

    /**
     * 喂一块音频（以及它的 RMS）。
     * 返回非 null 表示刚刚说完，值为整段 PCM。
     */
    fun accept(chunk: ShortArray, rms: Double): ShortArray? {
        return when (val event = segmenter.accept(rms)) {
            UtteranceSegmenter.Event.Idle -> {
                preRoll.addLast(chunk)
                while (preRoll.size > preRollChunks) preRoll.removeFirst()
                null
            }

            UtteranceSegmenter.Event.Started -> {
                val buffer = ArrayList<ShortArray>(preRoll.size + 1)
                preRoll.forEach { buffer.add(it) }
                preRoll.clear()
                buffer.add(chunk)
                active = buffer
                null
            }

            UtteranceSegmenter.Event.Ongoing -> {
                active?.add(chunk)
                null
            }

            is UtteranceSegmenter.Event.Finished -> {
                active?.add(chunk)
                val samples = concat(active)
                active = null
                preRoll.clear()
                samples
            }
        }
    }

    private fun concat(chunks: List<ShortArray>?): ShortArray {
        if (chunks.isNullOrEmpty()) return ShortArray(0)
        val total = chunks.sumOf { it.size }
        val out = ShortArray(total)
        var offset = 0
        for (chunk in chunks) {
            chunk.copyInto(out, offset)
            offset += chunk.size
        }
        return out
    }
}
