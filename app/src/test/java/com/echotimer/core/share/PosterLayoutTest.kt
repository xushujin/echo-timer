package com.echotimer.core.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PosterLayoutTest {

    @Test
    fun emptyBoardStillHasHeaderAndFooter() {
        assertEquals((PosterLayout.HEADER + PosterLayout.FOOTER).toInt(), PosterLayout.heightFor(0))
    }

    @Test
    fun heightGrowsByExactlyOneRowPerEntry() {
        val one = PosterLayout.heightFor(1)
        val five = PosterLayout.heightFor(5)
        assertEquals(PosterLayout.ROW.toInt() * 4, five - one)
    }

    @Test
    fun negativeCountIsTreatedAsZero() {
        assertEquals(PosterLayout.heightFor(0), PosterLayout.heightFor(-3))
    }

    @Test
    fun visibleRowsIsCappedForHugeBoards() {
        assertEquals(0, PosterLayout.visibleRows(0))
        assertEquals(3, PosterLayout.visibleRows(3))
        assertEquals(PosterLayout.MAX_ROWS, PosterLayout.visibleRows(PosterLayout.MAX_ROWS))
        assertEquals(PosterLayout.MAX_ROWS, PosterLayout.visibleRows(10_000))
    }

    @Test
    fun cappedPosterStaysWithinReasonableBitmapSize() {
        val height = PosterLayout.heightFor(PosterLayout.visibleRows(1_000))
        val bytes = PosterLayout.WIDTH.toLong() * height * 4
        // 40 行时约 24MB，仍低于常见 Android 单张位图的安全上限（32MB 左右）。
        assertTrue("位图过大：$bytes", bytes < 32L * 1024 * 1024)
    }
}
