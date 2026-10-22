package com.echotimer.core.voice.offline

import kotlin.math.sqrt

/**
 * 动态时间规整（DTW）。
 *
 * 同一句口令每次说的长短都不一样，逐帧对齐没有意义；DTW 允许时间轴拉伸，
 * 找一条累计代价最小的对齐路径。返回的代价按路径长度归一化，便于跨长度比较。
 *
 * 只保留两行滚动数组，空间 O(m)；口令模板只有几十帧，时间开销可忽略。
 */
object Dtw {

    /** 两段特征序列的平均距离；任一侧为空时返回 [Float.MAX_VALUE]。 */
    fun distance(a: List<FloatArray>, b: List<FloatArray>): Float {
        if (a.isEmpty() || b.isEmpty()) return Float.MAX_VALUE
        val width = b.size
        var previous = FloatArray(width + 1)
        var current = FloatArray(width + 1)
        previous.fill(Float.MAX_VALUE)
        previous[0] = 0f

        for (i in 1..a.size) {
            current[0] = Float.MAX_VALUE
            val frameA = a[i - 1]
            for (j in 1..width) {
                val cost = frameDistance(frameA, b[j - 1])
                val best = minOf(previous[j - 1], previous[j], current[j - 1])
                current[j] = if (best == Float.MAX_VALUE) Float.MAX_VALUE else cost + best
            }
            val swap = previous
            previous = current
            current = swap
        }

        val total = previous[width]
        return if (total == Float.MAX_VALUE) Float.MAX_VALUE else total / (a.size + width)
    }

    /** 两帧的欧氏距离，按维数开方归一化，避免特征维数影响阈值。 */
    private fun frameDistance(a: FloatArray, b: FloatArray): Float {
        val dim = minOf(a.size, b.size)
        if (dim == 0) return 0f
        var sum = 0f
        for (i in 0 until dim) {
            val diff = a[i] - b[i]
            sum += diff * diff
        }
        return sqrt(sum / dim)
    }
}
