package com.echotimer.core.update

/**
 * 什么时候检查、什么时候打扰用户——纯规则，便于单测。
 *
 * 自动检查刻意做得很克制：一天最多一次，而且用户点过「稍后」的版本不再自动弹窗
 * （设置页仍然能看到"有新版本"，随时可以手动更新）。
 */
object UpdatePolicy {

    /** 自动检查的最小间隔：一天。 */
    const val AUTO_CHECK_INTERVAL_MS = 24L * 60 * 60 * 1_000

    /** 远端版本比当前新才提示；同版本或更低一律不动。 */
    fun shouldOffer(remote: AppVersion, current: AppVersion): Boolean = remote > current

    /**
     * 是否该做一次自动检查。
     *
     * [lastCheckAt] 为 0 表示从未检查过；系统时间被往回拨时（差值为负）也放行一次，
     * 否则会永远卡在"还没到时间"。
     */
    fun shouldAutoCheck(enabled: Boolean, lastCheckAt: Long, nowMs: Long): Boolean {
        if (!enabled) return false
        if (lastCheckAt <= 0L) return true
        val elapsed = nowMs - lastCheckAt
        return elapsed < 0L || elapsed >= AUTO_CHECK_INTERVAL_MS
    }

    /**
     * 是否应该弹出更新弹窗。
     *
     * 手动检查（[auto] = false）总是弹；自动检查时，用户已经点过「稍后」的版本不再弹，
     * 避免每次启动都打扰。
     */
    fun shouldPrompt(version: AppVersion, skippedVersion: String?, auto: Boolean): Boolean {
        if (!auto) return true
        val skipped = AppVersion.parse(skippedVersion) ?: return true
        return skipped != version
    }
}
