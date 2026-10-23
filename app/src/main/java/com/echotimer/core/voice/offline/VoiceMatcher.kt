package com.echotimer.core.voice.offline

import com.echotimer.core.voice.VoiceCommand

/** 一次匹配的结果。 */
data class VoiceMatch(
    val command: VoiceCommand,
    val distance: Float,
    val threshold: Float,
)

/** 模板之间的统计量，用于设置门限与在界面上解释"为什么没匹配"。 */
data class MatchStats(
    /** 不同口令之间的最小距离；不足两条命令时为 null。 */
    val interClass: Float?,
    /** 同一口令两遍录音之间的距离；没有任何命令录过两遍时为 null。 */
    val sameClass: Float?,
    val threshold: Float,
    val bestCommand: VoiceCommand?,
    val bestDistance: Float,
)

/**
 * 模板匹配。
 *
 * 门限不再是一个拍脑袋的常数，而是**用你自己的模板量出来**的：
 *
 * - `类间距离` = 不同口令之间的最小距离（决定"最多能放宽到多少"）；
 * - `类内距离` = 同一口令两遍录音之间的距离（决定"至少要放宽到多少"）。
 *
 * 规则：
 * 1. 有类内距离（某条口令录了 2 遍）→ 门限 = min(类内 × 2.0, 类间 × 0.8)；
 *    这样既能容忍同一句话的正常波动，又不会跨到别的口令上。
 * 2. 只有单遍模板 → 退回 类间 × 灵敏度系数。
 * 3. 只录了 1 条命令 → 没有类间距离可用，用兜底门限。
 *
 * 训练弹窗会把这三个数字都显示出来，所以"为什么没匹配"是可以被解释的，
 * 而不是只有一句"没识别到"。
 */
object VoiceMatcher {

    /** 只有单遍模板时，类间距离的多少比例以内算命中。 */
    const val DEFAULT_RATIO = 0.65f

    /** 阈值的安全上下限，防止极端模板把门限推到不可用的位置。 */
    const val MIN_THRESHOLD = 1.0f
    const val MAX_THRESHOLD = 6f

    /** 只有一条命令（或一条模板）时的兜底门限。 */
    const val FALLBACK_THRESHOLD = 2.5f

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

    /** 同一指令两遍录音之间的最小距离；没有任何指令录过两遍时返回 null。 */
    fun sameClassDistance(templates: List<VoiceTemplate>): Float? {
        var min = Float.MAX_VALUE
        for (i in templates.indices) {
            for (j in i + 1 until templates.size) {
                if (templates[i].command != templates[j].command) continue
                val distance = Dtw.distance(templates[i].frames, templates[j].frames)
                if (distance < min) min = distance
            }
        }
        return if (min == Float.MAX_VALUE) null else min
    }

    fun threshold(templates: List<VoiceTemplate>, ratio: Float = DEFAULT_RATIO): Float {
        val interClass = interClassDistance(templates) ?: return FALLBACK_THRESHOLD
        val sameClass = sameClassDistance(templates)
        return if (sameClass != null) {
            minOf(sameClass * 2.0f, interClass * 0.8f).coerceIn(MIN_THRESHOLD, MAX_THRESHOLD)
        } else {
            (interClass * ratio).coerceIn(MIN_THRESHOLD, MAX_THRESHOLD)
        }
    }

    /** 诊断信息：门限 + 最近的一条模板。 */
    fun stats(
        frames: List<FloatArray>,
        templates: List<VoiceTemplate>,
        ratio: Float = DEFAULT_RATIO,
    ): MatchStats {
        val best = ranking(frames, templates).firstOrNull()
        return MatchStats(
            interClass = interClassDistance(templates),
            sameClass = sameClassDistance(templates),
            threshold = threshold(templates, ratio),
            bestCommand = best?.first,
            bestDistance = best?.second ?: Float.MAX_VALUE,
        )
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

    /** 调试/训练页用：列出每条模板的距离（升序），方便判断哪条容易混。 */
    fun ranking(frames: List<FloatArray>, templates: List<VoiceTemplate>): List<Pair<VoiceCommand, Float>> =
        templates
            .map { it.command to Dtw.distance(frames, it.frames) }
            .sortedBy { it.second }
}
