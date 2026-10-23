package com.echotimer.core.sync

import com.echotimer.core.settings.AppSettings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncPolicyTest {

    @Test
    fun refusesToOverwriteWhenLocalIsEmpty() {
        // 本地为空时覆盖会把仓库清空，必须拒绝。
        assertFalse(SyncPolicy.canOverwrite(SyncSnapshot()))
    }

    @Test
    fun allowsOverwriteWithOnlyBoards() {
        val local = SyncSnapshot(boards = listOf(SyncBoard("单杠", 1L)))
        assertTrue(SyncPolicy.canOverwrite(local))
    }

    @Test
    fun allowsOverwriteWithEntries() {
        val local = SyncSnapshot(entries = listOf(SyncEntry("单杠", "张三", 1_000L, 1L)))
        assertTrue(SyncPolicy.canOverwrite(local))
    }

    @Test
    fun forceLocalIsOffByDefault() {
        assertFalse("覆盖模式默认关闭", AppSettings().syncForceLocal)
    }
}
