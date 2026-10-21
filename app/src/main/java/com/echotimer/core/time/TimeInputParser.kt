package com.echotimer.core.time

/**
 * 手工录入成绩时的时间解析。
 *
 * 支持三种写法（都按"总时长"理解）：
 * - 纯秒数：`12`、`12.5`、`12.34`
 * - 分:秒：`1:23.45`
 * - 时:分:秒：`1:02:03.5`
 *
 * 全角数字与全角冒号/句点会被归一化；非法输入、非正数、超过 24 小时返回 null。
 */
object TimeInputParser {

    private const val SECONDS_PER_MINUTE = 60.0
    private const val MAX_SECONDS = 24 * 60 * 60.0

    fun parse(raw: String): Long? {
        val text = normalize(raw)
        if (text.isEmpty()) return null

        val parts = text.split(':')
        if (parts.size > 3) return null

        var totalSeconds = 0.0
        parts.forEachIndexed { index, part ->
            val isLast = index == parts.lastIndex
            val value = if (isLast) {
                part.toDoubleOrNull()
            } else {
                part.toIntOrNull()?.toDouble()
            }
            if (value == null || value < 0.0) return null
            totalSeconds = totalSeconds * SECONDS_PER_MINUTE + value
        }

        if (totalSeconds <= 0.0 || totalSeconds > MAX_SECONDS) return null
        return Math.round(totalSeconds * 1000.0)
    }

    /** 全角数字/冒号/句点转半角，去掉首尾空白与内部的空格。 */
    private fun normalize(raw: String): String {
        val builder = StringBuilder(raw.length)
        for (ch in raw) {
            when {
                ch.code in FULL_WIDTH_ZERO..FULL_WIDTH_NINE ->
                    builder.append('0' + (ch.code - FULL_WIDTH_ZERO))

                ch == '：' -> builder.append(':')
                ch == '．' -> builder.append('.')
                ch.isWhitespace() -> Unit
                else -> builder.append(ch)
            }
        }
        return builder.toString()
    }

    private const val FULL_WIDTH_ZERO = 0xFF10
    private const val FULL_WIDTH_NINE = 0xFF19
}
