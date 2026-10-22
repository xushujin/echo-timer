package com.echotimer.core.voice.offline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DtwTest {

    private fun frames(vararg values: Float): List<FloatArray> =
        values.map { floatArrayOf(it, it * 0.5f, -it) }

    @Test
    fun identicalSequencesHaveZeroDistance() {
        val sequence = frames(1f, 2f, 3f, 4f)
        assertEquals(0f, Dtw.distance(sequence, sequence), 1e-5f)
    }

    @Test
    fun distanceIsSymmetric() {
        val a = frames(1f, 2f, 3f)
        val b = frames(1.5f, 2.5f, 2.8f, 3.4f)
        assertEquals(Dtw.distance(a, b), Dtw.distance(b, a), 1e-4f)
    }

    @Test
    fun timeShiftIsTolerated() {
        val a = frames(1f, 2f, 3f, 4f, 5f)
        // 中间插入一帧重复，模拟说得慢一点
        val b = frames(1f, 2f, 2f, 3f, 4f, 5f)
        val shifted = Dtw.distance(a, b)
        val different = Dtw.distance(a, frames(9f, 9f, 9f, 9f, 9f))
        assertTrue("时间拉伸应该比内容不同更接近：$shifted vs $different", shifted < different)
    }

    @Test
    fun emptyInputIsRejected() {
        assertEquals(Float.MAX_VALUE, Dtw.distance(emptyList(), frames(1f)), 0f)
        assertEquals(Float.MAX_VALUE, Dtw.distance(frames(1f), emptyList()), 0f)
    }
}
