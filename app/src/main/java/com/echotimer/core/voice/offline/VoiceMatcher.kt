package com.echotimer.core.voice.offline

import com.echotimer.core.voice.VoiceCommand

/** 一次匹配的结果。 */
data class VoiceMatch(
    val command: VoiceCommand,
    val distance: Float,
    val threshold: Float,
)

/** 没匹配上的原因，界面据此给出不同的解释。 */
enum class MatchRejection {
    /** 还没有任何模板。 */
    NO_TEMPLATES,

    /** 最近的距离也超过门限——不像任何一条口令。 */
    TOO_FAR,

    /** 两个口令都很像，不敢认（乱说话时最常见）。 */
    AMBIGUOUS,
}

/** 匹配的完整判定，包含"为什么没认"的信息。 */
sealed interface MatchDecision {
    data class Accepted(
        val command: VoiceCommand,
        val distance: Float,
        val threshold: Float,
    ) : MatchDecision

    data class Rejected(
        val reason: MatchRejection,
        val nearest: VoiceCommand?,
        val distance: Float,
        val threshold: Float,
        /** 除最近那条之外，第二像的**不同口令**的距离；没有其它口令时为 null。 */
        val runnerUpDistance: Float?,
    ) : MatchDecision
}

/** 模板之间的统计量，用于设置门限与在界面上解释"为什么没匹配"。 */
data class MatchStats(
    /** 不同口令之间的最小距离；不足两条命令时为 null。 */
    val interClass: Float?,
    /** 同一口令两遍录音之间的距离；没有任何命令录过两遍时为 null。 */
    val sameClass: Float?,
    val threshold: Float,
    val bestCommand: VoiceCommand?,
    val bestDistance: Float,
    /** 第二像的**不同口令**的距离。 */
    val runnerUpDistance: Float?,
)

/**
 * 模板匹配。
 *
 * 门限用你自己的模板量出来，并且**必须"明显更近"才算命中**：
 *
 * 1. 距离 ≤ 门限。门限 = min(类内 × 2.0, 类间 × 0.8)，只有单遍模板时退回 类间 × 系数；
 * 2. 距离 ≤ 第二像的**不同口令** × [MARGIN]。
 *
 * 第 2 条是关键：乱说话时所有口令的距离都差不多（比如 0.87 / 0.95），
 * 只看绝对门限很容易误命中；要求"至少比第二像的近 28%"能挡掉绝大多数这种情况。
 *
 * DTW 本身还有长度闸门与带宽约束（见 [Dtw]），避免把不像的语音硬凑成一样。
 */
object VoiceMatcher {

    /** 只有单遍模板时，类间距离的多少比例以内算命中。 */
    const val DEFAULT_RATIO = 0.55f

    /** 阈值的安全上下限。下限不能太高，否则会盖掉"类内 × 2"的数据驱动结果。 */
    const val MIN_THRESHOLD = 0.5f
    const val MAX_THRESHOLD = 6f

    /**
     * 只有一条命令（或一条模板）时的兜底门限。
     * 没有第二个类就没有参照，只能取一个偏保守的值——录满 5 条口令才准。
     */
    const val FALLBACK_THRESHOLD = 1.0f

    /** 最近的距离必须 ≤ 第二像的不同口令 × 该系数，否则判为"太接近、不敢认"。 */
    const val MARGIN = 0.72f

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

    /** 完整判定：命中 / 太远 / 太接近。 */
    fun decide(
        frames: List<FloatArray>,
        templates: List<VoiceTemplate>,
        ratio: Float = DEFAULT_RATIO,
    ): MatchDecision {
        if (frames.isEmpty() || templates.isEmpty()) {
            return MatchDecision.Rejected(
                reason = MatchRejection.NO_TEMPLATES,
                nearest = null,
                distance = Float.MAX_VALUE,
                threshold = threshold(templates, ratio),
                runnerUpDistance = null,
            )
        }

        val limit = threshold(templates, ratio)
        var best: VoiceTemplate? = null
        var bestDistance = Float.MAX_VALUE
        for (template in templates) {
            val distance = Dtw.distance(frames, template.frames)
            if (distance < bestDistance) {
                bestDistance = distance
                best = template
            }
        }

        val winner = best
        if (winner == null || bestDistance == Float.MAX_VALUE) {
            return MatchDecision.Rejected(
                reason = MatchRejection.TOO_FAR,
                nearest = null,
                distance = Float.MAX_VALUE,
                threshold = limit,
                runnerUpDistance = null,
            )
        }

        // 第二像的"不同口令"：同一口令的另一遍不算，否则永远比不过自己。
        var runnerUp = Float.MAX_VALUE
        for (template in templates) {
            if (template.command == winner.command) continue
            val distance = Dtw.distance(frames, template.frames)
            if (distance < runnerUp) runnerUp = distance
        }
        val runnerUpOrNull = if (runnerUp == Float.MAX_VALUE) null else runnerUp

        if (bestDistance > limit) {
            return MatchDecision.Rejected(
                reason = MatchRejection.TOO_FAR,
                nearest = winner.command,
                distance = bestDistance,
                threshold = limit,
                runnerUpDistance = runnerUpOrNull,
            )
        }
        if (runnerUpOrNull != null && bestDistance > runnerUpOrNull * MARGIN) {
            return MatchDecision.Rejected(
                reason = MatchRejection.AMBIGUOUS,
                nearest = winner.command,
                distance = bestDistance,
                threshold = limit,
                runnerUpDistance = runnerUpOrNull,
            )
        }
        return MatchDecision.Accepted(winner.command, bestDistance, limit)
    }

    /** 只要结果：命中返回 [VoiceMatch]，否则 null（宁可不响应，也不要误触发）。 */
    fun match(
        frames: List<FloatArray>,
        templates: List<VoiceTemplate>,
        ratio: Float = DEFAULT_RATIO,
    ): VoiceMatch? = when (val decision = decide(frames, templates, ratio)) {
        is MatchDecision.Accepted -> VoiceMatch(decision.command, decision.distance, decision.threshold)
        is MatchDecision.Rejected -> null
    }

    /** 诊断信息：门限 + 最近与次近。 */
    fun stats(
        frames: List<FloatArray>,
        templates: List<VoiceTemplate>,
        ratio: Float = DEFAULT_RATIO,
    ): MatchStats {
        val ranking = ranking(frames, templates)
        val best = ranking.firstOrNull()
        val runnerUp = ranking.firstOrNull { it.first != best?.first }?.second
        return MatchStats(
            interClass = interClassDistance(templates),
            sameClass = sameClassDistance(templates),
            threshold = threshold(templates, ratio),
            bestCommand = best?.first,
            bestDistance = best?.second ?: Float.MAX_VALUE,
            runnerUpDistance = runnerUp,
        )
    }

    /** 调试/训练页用：列出每条模板的距离（升序），方便判断哪条容易混。 */
    fun ranking(frames: List<FloatArray>, templates: List<VoiceTemplate>): List<Pair<VoiceCommand, Float>> =
        templates
            .map { it.command to Dtw.distance(frames, it.frames) }
            .sortedBy { it.second }
}
