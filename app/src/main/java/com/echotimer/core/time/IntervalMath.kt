package com.echotimer.core.time

/** 下一个播报点及其间隔。 */
data class NextMark(
    /** 时间点（毫秒）。 */
    val atMs: Long,
    /** 所属间隔（秒）。 */
    val intervalSeconds: Int,
)

/**
 * 播报间隔的纯数学部分：界面用它画"距下一次播报还剩多少"的进度环。
 * 与 [AnnouncementScheduler] 共享同一套"绝对时间点"语义，避免两处规则不一致。
 */
object IntervalMath {

    /** 返回严格大于 [elapsedMs] 的最近一个播报点；间隔非法时返回 null。 */
    fun nextMark(elapsedMs: Long, intervalSeconds: Int): NextMark? {
        if (intervalSeconds <= 0) return null
        val safeElapsed = elapsedMs.coerceAtLeast(0L)
        val step = intervalSeconds * 1_000L
        return NextMark(atMs = (safeElapsed / step + 1) * step, intervalSeconds = intervalSeconds)
    }

    /** 距下一个播报点的进度，0f..1f；间隔非法时返回 0f。 */
    fun progressToNextMark(elapsedMs: Long, intervalSeconds: Int): Float {
        val mark = nextMark(elapsedMs, intervalSeconds) ?: return 0f
        val step = mark.intervalSeconds * 1_000L
        val start = mark.atMs - step
        val done = (elapsedMs - start).coerceIn(0L, step)
        return done.toFloat() / step.toFloat()
    }
}
