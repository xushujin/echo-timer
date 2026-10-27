package com.echotimer.core.sync

import com.echotimer.core.settings.AppSettings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoSyncPolicyTest {

    private val ready = AppSettings(
        syncEnabled = true,
        syncRepo = "owner/repo",
        syncToken = "token",
    )

    @Test
    fun disabledNeverSyncs() {
        assertFalse(AutoSyncPolicy.shouldSync(ready.copy(syncEnabled = false)))
    }

    @Test
    fun enabledButIncompleteConfigDoesNotSync() {
        // 没填令牌：自动同步会一直失败，不如不发请求。
        assertFalse(AutoSyncPolicy.shouldSync(ready.copy(syncToken = "")))
        // 仓库写法不合法
        assertFalse(AutoSyncPolicy.shouldSync(ready.copy(syncRepo = "onlyname")))
        assertFalse(AutoSyncPolicy.shouldSync(ready.copy(syncRepo = "")))
    }

    @Test
    fun enabledWithFullConfigSyncs() {
        assertTrue(AutoSyncPolicy.shouldSync(ready))
        // 粘贴完整地址也认
        assertTrue(
            AutoSyncPolicy.shouldSync(ready.copy(syncRepo = "https://github.com/owner/repo")),
        )
    }

    @Test
    fun debounceWindowIsPositive() {
        assertTrue("防抖窗口必须为正，否则会每次改动都立刻发请求", AutoSyncPolicy.DEBOUNCE_MS > 0)
    }
}
