package com.echotimer.core.share

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 分享图片的文件名处理。
 *
 * 榜单名是用户输入，可能含 `/`、`:`、空白等文件系统不接受的字符，
 * 直接拼进文件名会写失败，所以统一做一次清洗。
 */
object ShareNaming {

    private val stampFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.US)

    private val illegal = Regex("[\\\\/:*?\"<>|\\s]+")

    /** 清掉非法字符并截断；清洗后为空时回落到 [fallback]。 */
    fun safeName(raw: String, fallback: String = "leaderboard"): String {
        val cleaned = raw.replace(illegal, "-").trim('-', '.', ' ')
        return cleaned.take(40).ifBlank { fallback }
    }

    /** 生成形如 `EchoTimer-单杠悬垂-20261022-090543.png` 的文件名。 */
    fun fileNameFor(boardName: String, epochMs: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val stamp = stampFormatter.format(Instant.ofEpochMilli(epochMs).atZone(zone))
        return "EchoTimer-${safeName(boardName)}-$stamp.png"
    }
}
