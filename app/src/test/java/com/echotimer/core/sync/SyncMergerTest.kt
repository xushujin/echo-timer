package com.echotimer.core.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncMergerTest {

    private fun board(name: String, createdAt: Long = 1L) = SyncBoard(name, createdAt)
    private fun entry(board: String, player: String, ms: Long, at: Long = 1L) =
        SyncEntry(board, player, ms, at)

    @Test
    fun mergesBothSides() {
        val local = SyncSnapshot(
            boards = listOf(board("单杠")),
            entries = listOf(entry("单杠", "张三", 100)),
        )
        val remote = SyncSnapshot(
            boards = listOf(board("单杠"), board("短跑")),
            entries = listOf(entry("单杠", "李四", 200), entry("短跑", "Tom", 50)),
        )

        val result = SyncMerger.merge(local, remote, now = 999L)
        assertEquals(setOf("单杠", "短跑"), result.snapshot.boards.map { it.name }.toSet())
        assertEquals(3, result.snapshot.entries.size)
        assertEquals(1, result.addedBoards)
        assertEquals(2, result.addedEntries)
        assertEquals(0, result.improvedEntries)
        assertEquals(999L, result.snapshot.updatedAt)
    }

    @Test
    fun keepsTheBetterScore() {
        val local = SyncSnapshot(entries = listOf(entry("B", "P", 300)))
        val remote = SyncSnapshot(entries = listOf(entry("B", "P", 500)))

        val forward = SyncMerger.merge(local, remote, now = 0L)
        assertEquals(500L, forward.snapshot.entries.single().bestMs)
        assertEquals(1, forward.improvedEntries)

        val backward = SyncMerger.merge(remote, local, now = 0L)
        assertEquals(500L, backward.snapshot.entries.single().bestMs)
        assertEquals(0, backward.improvedEntries)
    }

    @Test
    fun boardNamesAreCaseInsensitive() {
        val local = SyncSnapshot(boards = listOf(board("Sprint")))
        val remote = SyncSnapshot(boards = listOf(board("SPRINT")))
        val merged = SyncMerger.merge(local, remote, now = 0L)
        assertEquals(1, merged.snapshot.boards.size)
        assertEquals("Sprint", merged.snapshot.boards.single().name)
    }

    @Test
    fun orphanEntriesGetTheirBoardCreated() {
        val remote = SyncSnapshot(entries = listOf(entry("新榜", "P", 42)))
        val merged = SyncMerger.merge(SyncSnapshot(), remote, now = 0L)
        assertEquals(1, merged.snapshot.boards.size)
        assertEquals("新榜", merged.snapshot.boards.single().name)
        assertEquals(1, merged.addedBoards)
    }

    @Test
    fun mergeIsIdempotent() {
        val local = SyncSnapshot(
            boards = listOf(board("B")),
            entries = listOf(entry("B", "P", 100)),
        )
        val remote = SyncSnapshot(
            boards = listOf(board("B")),
            entries = listOf(entry("B", "Q", 200)),
        )
        val once = SyncMerger.merge(local, remote, now = 0L).snapshot
        val twice = SyncMerger.merge(once, remote, now = 0L).snapshot
        assertTrue(SyncMerger.sameData(once, twice))
    }

    @Test
    fun sameDataIgnoresOrderAndTimestamp() {
        val a = SyncSnapshot(
            boards = listOf(board("B", 1), board("A", 2)),
            entries = listOf(entry("B", "P", 1), entry("A", "Q", 2)),
            updatedAt = 100,
        )
        val b = SyncSnapshot(
            boards = listOf(board("A", 9), board("B", 8)),
            entries = listOf(entry("A", "Q", 2), entry("B", "P", 1)),
            updatedAt = 999,
        )
        assertTrue(SyncMerger.sameData(a, b))

        val c = b.copy(entries = listOf(entry("A", "Q", 3), entry("B", "P", 1)))
        assertFalse(SyncMerger.sameData(a, c))
    }

    @Test
    fun invalidEntriesAreDropped() {
        val remote = SyncSnapshot(entries = listOf(entry("B", "P", 0), entry("", "Q", 5)))
        val merged = SyncMerger.merge(SyncSnapshot(), remote, now = 0L)
        assertTrue(merged.snapshot.entries.isEmpty())
        assertTrue(merged.snapshot.boards.isEmpty())
    }
}
