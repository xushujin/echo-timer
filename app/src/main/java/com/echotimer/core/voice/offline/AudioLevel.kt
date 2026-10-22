package com.echotimer.core.voice.offline

import kotlin.math.log10
import kotlin.math.sqrt

/** 音频电平计算，两个控制器与训练流程共用。 */
object AudioLevel {

    fun rms(chunk: ShortArray): Double {
        if (chunk.isEmpty()) return 0.0
        var sum = 0.0
        for (sample in chunk) {
            val value = sample.toDouble()
            sum += value * value
        }
        return sqrt(sum / chunk.size)
    }

    /**
     * 映射成界面用的"分贝"刻度。
     * 系统识别上报的 rmsdB 大约在 -2..10 之间，这里对齐到同一量级，
     * 界面上的呼吸动画不需要为两种引擎写两套。
     */
    fun toUiDb(rms: Double): Float {
        if (rms <= 1.0) return -2f
        val db = 20.0 * log10(rms / 1_000.0)
        return db.toFloat().coerceIn(-2f, 12f)
    }
}
