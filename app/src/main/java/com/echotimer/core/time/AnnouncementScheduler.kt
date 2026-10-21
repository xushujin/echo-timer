package com.echotimer.core.time

/** 一次到点的播报。 */
data class Announcement(
    /** 触发播报的时间点（毫秒，是间隔的整数倍）。 */
    val atMs: Long,
    /** 触发它的播报间隔（秒）。 */
    val intervalSeconds: Int,
)

/**
 * 播报调度器：把"当前累计时长"换算成"这一次该播报哪个时间点"。
 *
 * 语义（与 README「播报规则」一节逐条对应）：
 * 1. 间隔是**绝对时间点**：设置 5 秒就在 5s、10s、15s… 播报，而不是"距上次播报 5 秒"。
 *    设置 1 秒就是每秒播报一次。
 * 2. 同一个时间点只播报一次。
 * 3. 时间大幅跳跃（主线程卡顿、切后台回来）只播最新到点的那个，不补播中间每一个点。
 * 4. 暂停期间累计时长不变，因此不会播报；继续后从当前时间点接着算。
 * 5. 间隔改小/改大后按新间隔重新计算，不会回头补播已经过去的时间点。
 */
class AnnouncementScheduler {

    private var intervalSeconds: Int = 0
    private var lastElapsedMs: Long = 0L
    private var lastSpokenMarkMs: Long = 0L

    /** 更新播报间隔（秒）。非法值（<=0）等价于关闭播报。 */
    fun setInterval(seconds: Int) {
        intervalSeconds = seconds.coerceAtLeast(0)
    }

    fun interval(): Int = intervalSeconds

    /** 重新开始一轮计时时调用，清空已播报标记。 */
    fun reset() {
        lastElapsedMs = 0L
        lastSpokenMarkMs = 0L
    }

    /**
     * 推进到 [elapsedMs] 并返回本次需要播报的点；无需播报时返回 null。
     *
     * 必须按时间递增调用；检测到时间回退（例如重置后未调用 [reset]）时自动重新同步。
     */
    fun advanceTo(elapsedMs: Long): Announcement? {
        if (elapsedMs < lastElapsedMs) {
            lastElapsedMs = elapsedMs
            lastSpokenMarkMs = 0L
            return null
        }
        lastElapsedMs = elapsedMs
        if (intervalSeconds <= 0) return null

        val step = intervalSeconds * 1_000L
        val latestMark = elapsedMs / step * step
        if (latestMark < step || latestMark <= lastSpokenMarkMs) return null

        lastSpokenMarkMs = latestMark
        return Announcement(atMs = latestMark, intervalSeconds = intervalSeconds)
    }
}
