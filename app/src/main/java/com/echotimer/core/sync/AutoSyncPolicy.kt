package com.echotimer.core.sync

import com.echotimer.core.settings.AppSettings

/**
 * 自动同步的触发规则（纯函数，便于单测）。
 *
 * 榜单数据一变（挑战结束、手工录入、删除）就安排一次同步，但要做两件事：
 * - 只有**开启同步且配置完整**时才触发，否则本地照常用，不产生无谓的网络请求；
 * - 连续操作（比如连删三条成绩）要合并成一次，靠 [DEBOUNCE_MS] 的延迟窗口实现。
 */
object AutoSyncPolicy {

    /** 最后一次改动之后等这么久再同步，把连续操作合并成一次。 */
    const val DEBOUNCE_MS = 4_000L

    fun shouldSync(settings: AppSettings): Boolean {
        if (!settings.syncEnabled) return false
        return SyncConfig(
            repo = settings.syncRepo,
            branch = settings.syncBranch,
            path = settings.syncPath,
            token = settings.syncToken,
        ).problem() == null
    }
}
