package com.echotimer.core.voice.offline

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * 动态时间规整（DTW）。
 *
 * 同一句口令每次说的长短都不一样，逐帧对齐没有意义；DTW 允许时间轴拉伸，
 * 找一条累计代价最小的对齐路径。返回的代价按路径长度归一化，便于跨长度比较。
 *
 * 两个约束（都是为了"别把不像的东西硬凑成一样"）：
 * - **长度闸门**：两段帧数相差超过 [MAX_LENGTH_RATIO] 倍直接判为不相关，不浪费计算；
 * - **带宽约束**（Sakoe-Chiba）：对齐路径偏离对角线不超过 30%，防止 DTW 通过
 *   极端扭曲把两段无关语音"拉"到一起——这是"乱说也能匹配"的常见原因。
 */
object Dtw {

    /** 长度比超过这个倍数就认为不可能是同一个词。 */
    const val MAX_LENGTH_RATIO = 2.2f

    /** 对齐路径相对对角线的最大偏移比例。 */
    private const val BAND_RATIO = 0.3f

    /** 带宽下限，避免极短语音（几帧）带宽过窄导致无法对齐。 */
    private const val MIN_BAND = 6

    /** 两段特征序列的平均距离；长度差太多或任一侧为空时返回 [Float.MAX_VALUE]。 */
    fun distance(a: List<FloatArray>, b: List<FloatArray>): Float {
        if (a.isEmpty() || b.isEmpty()) return Float.MAX_VALUE
        val longer = max(a.size, b.size).toFloat()
        val shorter = min(a.size, b.size).toFloat()
        if (longer / shorter > MAX_LENGTH_RATIO) return Float.MAX_VALUE

        val n = a.size
        val m = b.size
        val band = max(MIN_BAND, (max(n, m) * BAND_RATIO).toInt())

        var previous = FloatArray(m + 1)
        var current = FloatArray(m + 1)
        previous.fill(Float.MAX_VALUE)
        previous[0] = 0f

        for (i in 1..n) {
            current.fill(Float.MAX_VALUE)
            current[0] = Float.MAX_VALUE
            val frameA = a[i - 1]
            val from = max(1, i - band)
            val to = min(m, i + band)
            for (j in from..to) {
                val cost = frameDistance(frameA, b[j - 1])
                val best = minOf(previous[j - 1], previous[j], current[j - 1])
                if (best != Float.MAX_VALUE) current[j] = cost + best
            }
            val swap = previous
            previous = current
            current = swap
        }

        val total = previous[m]
        return if (total == Float.MAX_VALUE) Float.MAX_VALUE else total / (n + m)
    }

    /** 两帧的欧氏距离，按维数开方归一化，避免特征维数影响阈值。 */
    private fun frameDistance(a: FloatArray, b: FloatArray): Float {
        val dim = min(a.size, b.size)
        if (dim == 0) return 0f
        var sum = 0f
        for (i in 0 until dim) {
            val diff = a[i] - b[i]
            sum += diff * diff
        }
        return sqrt(sum / dim)
    }
}
