package com.echotimer.core.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncCodecTest {

    private val snapshot = SyncSnapshot(
        boards = listOf(
            SyncBoard("单杠悬垂", 1_700_000_000_000L),
            SyncBoard("Sprint", 1_700_000_100_000L),
        ),
        entries = listOf(
            SyncEntry("单杠悬垂", "张三", 63_000L, 1_700_000_200_000L),
            SyncEntry("单杠悬垂", "李四", 71_500L, 1_700_000_300_000L),
            SyncEntry("Sprint", "Tom", 9_870L, 1_700_000_400_000L),
        ),
        updatedAt = 1_700_000_500_000L,
    )

    @Test
    fun roundTripKeepsEverything() {
        val decoded = SyncCodec.decode(SyncCodec.encode(snapshot))
        assertEquals(2, decoded.boards.size)
        assertEquals(3, decoded.entries.size)
        assertEquals(snapshot.updatedAt, decoded.updatedAt)
        assertEquals(setOf("单杠悬垂", "Sprint"), decoded.boards.map { it.name }.toSet())
        val entry = decoded.entries.first { it.player == "张三" }
        assertEquals(63_000L, entry.bestMs)
        assertEquals("单杠悬垂", entry.board)
    }

    @Test
    fun encodedFileStartsWithMetaAndEndsWithNewline() {
        val text = SyncCodec.encode(snapshot)
        assertTrue(text.startsWith("{\"type\":\"meta\",\"version\":1,"))
        assertTrue(text.endsWith("\n"))
        // 每行一个对象
        assertEquals(1 + 2 + 3, text.trim().lines().size)
    }

    @Test
    fun chineseAndQuotesSurvive() {
        val tricky = SyncSnapshot(
            boards = listOf(SyncBoard("带\"引号\"的榜", 1L)),
            entries = listOf(SyncEntry("带\"引号\"的榜", "张三\n李四", 1_000L, 2L)),
            updatedAt = 3L,
        )
        val decoded = SyncCodec.decode(SyncCodec.encode(tricky))
        assertEquals("带\"引号\"的榜", decoded.boards.single().name)
        assertEquals("张三\n李四", decoded.entries.single().player)
    }

    @Test
    fun malformedLinesAreSkipped() {
        val text = """
            {"type":"meta","version":1,"updatedAt":10}
            this line is not json
            {"type":"entry","board":"B","player":"P","bestMs":not-a-number}
            {"type":"board","name":"B","createdAt":5}
            {"type":"entry","board":"B","player":"P","bestMs":1234,"updatedAt":6}
        """.trimIndent()
        val decoded = SyncCodec.decode(text)
        assertEquals(1, decoded.boards.size)
        assertEquals(1, decoded.entries.size)
        assertEquals(1234L, decoded.entries.single().bestMs)
        assertEquals(10L, decoded.updatedAt)
    }

    @Test
    fun unknownTypesAndEmptyInputAreIgnored() {
        assertTrue(SyncCodec.decode("").isEmpty)
        assertTrue(SyncCodec.decode("{\"type\":\"whatever\",\"x\":1}").isEmpty)
    }

    @Test
    fun entriesWithoutBoardOrPlayerAreDropped() {
        val text = """
            {"type":"entry","board":"","player":"P","bestMs":10}
            {"type":"entry","board":"B","player":"","bestMs":10}
            {"type":"entry","board":"B","player":"P","bestMs":0}
        """.trimIndent()
        assertTrue(SyncCodec.decode(text).entries.isEmpty())
    }
}
