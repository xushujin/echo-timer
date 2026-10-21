package com.echotimer.core.time

import java.util.Locale

/**
 * 时间文本格式化。纯函数、无 Android 依赖，便于单元测试。
 *
 * 设计取舍：
 * - 屏幕显示一律向下取整（1_999ms 显示 00:01.99 而不是 00:02.00），
 *   避免"秒数还没到就先跳"的观感问题。
 * - 播报文本按人耳习惯省略为 0 的位（"1 分"而不是"0 小时 1 分 0 秒"）。
 */
object ElapsedFormatter {

    private const val MS_PER_SECOND = 1_000L
    private const val MS_PER_MINUTE = 60L * MS_PER_SECOND
    private const val MS_PER_HOUR = 60L * MS_PER_MINUTE

    /** 计时主显示：`mm:ss.SS`，超过 1 小时切换为 `h:mm:ss.SS`。 */
    fun clock(elapsedMs: Long): String {
        val safe = elapsedMs.coerceAtLeast(0L)
        val hours = safe / MS_PER_HOUR
        val minutes = safe % MS_PER_HOUR / MS_PER_MINUTE
        val seconds = safe % MS_PER_MINUTE / MS_PER_SECOND
        val centiseconds = safe % MS_PER_SECOND / 10
        return if (hours > 0L) {
            String.format(Locale.ROOT, "%d:%02d:%02d.%02d", hours, minutes, seconds, centiseconds)
        } else {
            String.format(Locale.ROOT, "%02d:%02d.%02d", minutes, seconds, centiseconds)
        }
    }

    /** 不含百分秒的显示：`mm:ss` 或 `h:mm:ss`。用于历史列表等紧凑场景。 */
    fun clockShort(elapsedMs: Long): String {
        val safe = elapsedMs.coerceAtLeast(0L)
        val hours = safe / MS_PER_HOUR
        val minutes = safe % MS_PER_HOUR / MS_PER_MINUTE
        val seconds = safe % MS_PER_MINUTE / MS_PER_SECOND
        return if (hours > 0L) {
            String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.ROOT, "%02d:%02d", minutes, seconds)
        }
    }

    /**
     * 定时播报用的文本：只读秒数，不带单位（5、90、3600）。
     * 每秒播报一次时加"秒"会很啰嗦，用户要求只报数字。
     */
    fun spokenSeconds(elapsedMs: Long): String =
        (elapsedMs.coerceAtLeast(0L) / MS_PER_SECOND).toString()

    /**
     * 结束提示用的播报文本。
     *
     * 只报到"分"：90_000ms -> "1 分"。秒级播报在连续报时里太吵，而且秒数每次都在变，
     * 用户听到的是一串数字而不是"过了多久"。不足 1 分钟时才退回报秒（否则会念成"0 分"）。
     */
    fun spoken(elapsedMs: Long): String {
        val safe = elapsedMs.coerceAtLeast(0L)
        val hours = safe / MS_PER_HOUR
        val minutes = safe % MS_PER_HOUR / MS_PER_MINUTE
        val seconds = safe % MS_PER_MINUTE / MS_PER_SECOND
        val parts = ArrayList<String>(2)
        if (hours > 0L) parts.add("$hours 小时")
        if (minutes > 0L) parts.add("$minutes 分")
        if (parts.isEmpty()) parts.add("$seconds 秒")
        return parts.joinToString(" ")
    }
}
