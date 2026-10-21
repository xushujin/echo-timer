package com.echotimer.ui

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 历史记录里用到的日期格式化。统一走 java.time，避免 SimpleDateFormat 的默认 Locale 问题。 */
object DateFormats {

    private val dateTimeFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("M月d日 HH:mm", Locale.CHINA)

    private val timeFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("HH:mm:ss", Locale.CHINA)

    fun dateTime(epochMs: Long): String =
        dateTimeFormatter.format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()))

    fun time(epochMs: Long): String =
        timeFormatter.format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()))
}
