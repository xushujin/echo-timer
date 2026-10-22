package com.echotimer.core.voice.offline

import com.echotimer.core.voice.VoiceCommand

/** 一次匹配的结果。 */
data class VoiceMatch(
    val command: VoiceCommand,
    val distance: Float,
    val threshold: Float,
)

/**
 * 模板匹配。
 *
 * 阈值不是写死的常数，而是**按模板之间的类间距离自适应**：
 * 先算"不同口令之间"的最小距离，再取它的一个比例作为门限。
 * 这样换人、换手机、换麦克风增益都不需要重新调参——只要用户自己的 5 条口令
 * 彼此是可区分的，门限就自动落在合理位置。
 *
 * 只训练了 1 条口令时没有类间距离可用，回落到一个经验值。
 */
object VoiceMatcher {

    /** 类间距离的多少比例以内算命中。 */
    const val DEFAULT_RATIO = 0.52f

    /** 阈值的安全上下限，防止极端模板把门限推到不可用的位置。 */
    const val MIN_THRESHOLD = 0.8f
    const val MAX_THRESHOLD = 6f

    /** 只有一条模板时的兜底门限。 */
    const val FALLBACK_THRESHOLD = 2.2f

    /** 不同指令之间的最小距离；不足两条指令时返回 null。 */
    fun interClassDistance(templates: List<VoiceTemplate>): Float? {
        if (templates.size < 2) return null
        var min = Float.MAX_VALUE
        for (i in templates.indices) {
            for (j in i + 1 until templates.size) {
                if (templates[i].command == templates[j].command) continue
                val distance = Dtw.distance(templates[i].frames, templates[j].frames)
                if (distance < min) min = distance
            }
        }
        return if (min == Float.MAX_VALUE) null else min
    }

    fun threshold(templates: List<VoiceTemplate>, ratio: Float = DEFAULT_RATIO): Float {
        val interClass = interClassDistance(templates) ?: return FALLBACK_THRESHOLD
        return (interClass * ratio).coerceIn(MIN_THRESHOLD, MAX_THRESHOLD)
    }

    /** 找出最接近的模板；超过门限返回 null（宁可不响应，也不要误触发）。 */
    fun match(
        frames: List<FloatArray>,
        templates: List<VoiceTemplate>,
        ratio: Float = DEFAULT_RATIO,
    ): VoiceMatch? {
        if (frames.isEmpty() || templates.isEmpty()) return null
        var best: VoiceTemplate? = null
        var bestDistance = Float.MAX_VALUE
        for (template in templates) {
            val distance = Dtw.distance(frames, template.frames)
            if (distance < bestDistance) {
                bestDistance = distance
                best = template
            }
        }
        val winner = best ?: return null
        val limit = threshold(templates, ratio)
        if (bestDistance > limit) return null
        return VoiceMatch(command = winner.command, distance = bestDistance, threshold = limit)
    }

    /** 调试/训练页用：列出每条模板的距离，方便判断哪条容易混。 */
    fun ranking(frames: List<FloatArray>, templates: List<VoiceTemplate>): List<Pair<VoiceCommand, Float>> =
        templates
            .map { it.command to Dtw.distance(frames, it.frames) }
            .sortedBy { it.second }
}
