package com.echotimer.core.leaderboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OvertakeTrackerTest {

    private fun entry(id: Long, player: String, bestMs: Long) =
        LeaderboardEntry(id = id, boardId = 1L, player = player, bestMs = bestMs, updatedAt = 0L)

    /** 榜单按成绩降序展示：王五 80s > 李四 71.5s > 张三 63s。 */
    private val board = listOf(
        entry(1, "王五", 80_000),
        entry(2, "李四", 71_500),
        entry(3, "张三", 63_000),
    )

    @Test
    fun passesFromLowestRecordUpwards() {
        val tracker = OvertakeTracker(board, selfName = "我")

        assertTrue("62 秒还没超过任何人", tracker.poll(62_000).isEmpty())
        assertEquals(listOf("张三"), tracker.poll(64_000).map { it.player })
        assertEquals(listOf("李四"), tracker.poll(72_000).map { it.player })
        assertEquals(listOf("王五"), tracker.poll(81_000).map { it.player })
        assertEquals(3, tracker.passedCount)
        assertEquals(3, tracker.total)
    }

    @Test
    fun jumpReportsEveryoneInOrder() {
        val tracker = OvertakeTracker(board, selfName = "我")
        // 界面卡顿或切后台回来，一次跨过三个人：要按成绩升序全部补上，不能只报最后一个。
        assertEquals(listOf("张三", "李四", "王五"), tracker.poll(120_000).map { it.player })
    }

    @Test
    fun doesNotRepeat() {
        val tracker = OvertakeTracker(board, selfName = "我")
        assertEquals(3, tracker.poll(120_000).size)
        assertTrue(tracker.poll(120_000).isEmpty())
        assertTrue(tracker.poll(200_000).isEmpty())
    }

    @Test
    fun tieIsNotAnOvertake() {
        val tracker = OvertakeTracker(board, selfName = "我")
        // 打平不算超越，与「只有更大才更新」保持一致。
        assertTrue(tracker.poll(63_000).isEmpty())
        assertEquals(listOf("张三"), tracker.poll(63_001).map { it.player })
    }

    @Test
    fun skipsOwnRecord() {
        val withSelf = board + entry(4, "我", 70_000)
        val tracker = OvertakeTracker(withSelf, selfName = "我")

        assertEquals(3, tracker.total)
        // 70s 之前只会越过张三；自己的 70s 不该弹「超越我」。
        assertEquals(listOf("张三"), tracker.poll(71_000).map { it.player })
        assertEquals(listOf("李四"), tracker.poll(72_000).map { it.player })
    }

    @Test
    fun resetStartsOver() {
        val tracker = OvertakeTracker(board, selfName = "我")
        tracker.poll(120_000)
        tracker.reset()
        assertEquals(0, tracker.passedCount)
        assertEquals(3, tracker.poll(120_000).size)
    }

    @Test
    fun emptyBoardNeverReports() {
        val tracker = OvertakeTracker(emptyList(), selfName = "我")
        assertEquals(0, tracker.total)
        assertTrue(tracker.poll(999_000).isEmpty())
    }
}
