package com.echotimer.core.update

import com.echotimer.core.sync.Json
import java.util.Locale

/**
 * 解析 GitHub Releases API 的响应，以及发布说明的纯文本化。
 *
 * 只用自写的 [Json]（`org.json` 在 JVM 单测里是空实现），
 * 所以整条解析链路都能被单测覆盖——这里的字段一旦读错，用户就永远看不到更新。
 */
object UpdateCodec {

    /** 解析 `releases/latest` 的响应；草稿 / 预发布 / 没有 tag 都返回 null。 */
    fun parseRelease(json: String): ReleaseInfo? {
        val root = Json.parse(json) as? Map<*, *> ?: return null
        if (root["draft"] == true || root["prerelease"] == true) return null

        val tag = root.string("tag_name") ?: return null
        val version = AppVersion.parse(tag) ?: return null

        val assets = (root["assets"] as? List<*>)
            ?.mapNotNull { it as? Map<*, *> }
            .orEmpty()

        // 附件名形如 EchoTimer-v1.6.0.apk / EchoTimer-v1.6.0.apk.sha256；
        // 后者以 .sha256 结尾，不会被当成安装包。
        val apkAsset = assets.firstOrNull { asset ->
            asset.string("name")?.endsWith(".apk", ignoreCase = true) == true &&
                asset.string("browser_download_url") != null
        }
        val apk = apkAsset?.let { asset ->
            val name = asset.string("name") ?: return@let null
            val url = asset.string("browser_download_url") ?: return@let null
            ReleaseAsset(name = name, downloadUrl = url, sizeBytes = asset.long("size") ?: 0L)
        }

        val checksumUrl = assets
            .firstOrNull { it.string("name")?.endsWith(".apk.sha256", ignoreCase = true) == true }
            ?.string("browser_download_url")

        return ReleaseInfo(
            tagName = tag,
            version = version,
            title = root.string("name")?.takeIf { it.isNotBlank() } ?: tag,
            notes = cleanNotes(root.string("body").orEmpty()),
            pageUrl = root.string("html_url").orEmpty(),
            publishedAt = root.string("published_at").orEmpty(),
            apk = apk,
            checksumUrl = checksumUrl,
        )
    }

    /**
     * 解析 `releases.atom`（发布页的 Atom 订阅）。
     *
     * 为什么不只用 API：未认证的 GitHub API 每小时只有 60 次配额，而且是按**出口 IP** 算的，
     * 移动网络共用 NAT 时很容易被别人的请求耗尽。Atom 是普通网页，没有这个配额，
     * 所以 API 被限流时用它兜底。代价是拿不到附件大小，安装包地址要按发布规则拼出来。
     */
    fun parseAtomFeed(xml: String, repo: String): ReleaseInfo? {
        val entry = firstEntry(xml) ?: return null
        val link = firstMatch(entry, "<link[^>]*href=\"([^\"]+)\"") ?: return null
        val tag = link.substringAfterLast('/').takeIf { it.isNotBlank() } ?: return null
        val version = AppVersion.parse(tag) ?: return null

        val title = firstMatch(entry, "<title>([^<]*)</title>")?.trim()?.takeIf { it.isNotEmpty() }
        val notes = firstMatch(entry, "<content[^>]*>(.*?)</content>")
            ?.let { cleanNotes(stripHtml(unescapeHtml(it))) }
            .orEmpty()

        // 发布产物名由构建脚本固定为 EchoTimer-v<version>.apk，因此可以拼出下载地址。
        val apkName = "EchoTimer-v$version.apk"
        val downloadBase = "https://github.com/$repo/releases/download/$tag"
        return ReleaseInfo(
            tagName = tag,
            version = version,
            title = title ?: tag,
            notes = notes,
            pageUrl = "https://github.com/$repo/releases/tag/$tag",
            publishedAt = firstMatch(entry, "<updated>([^<]*)</updated>").orEmpty(),
            apk = ReleaseAsset(
                name = apkName,
                downloadUrl = "$downloadBase/$apkName",
                sizeBytes = 0L,
            ),
            checksumUrl = "$downloadBase/$apkName.sha256",
        )
    }

    /**
     * 把 release 正文（Markdown）压成能直接显示的纯文本。
     *
     * 不做完整 Markdown 渲染：只去掉标题井号、加粗/代码标记，折叠空行，超长截断。
     * 目的是"看得懂"，不是"排得漂亮"。
     */
    fun cleanNotes(raw: String, maxChars: Int = 900): String {
        if (raw.isBlank()) return ""
        val lines = raw.replace("\r\n", "\n").replace('\r', '\n')
            .split('\n')
            .map { line ->
                line.trimEnd()
                    .removePrefix("#### ")
                    .removePrefix("### ")
                    .removePrefix("## ")
                    .removePrefix("# ")
                    .replace("**", "")
                    .replace("__", "")
                    .replace("`", "")
                    .trim()
            }

        val builder = StringBuilder()
        var blank = 0
        for (line in lines) {
            if (line.isEmpty()) {
                blank++
                if (blank > 1 || builder.isEmpty()) continue
            } else {
                blank = 0
            }
            builder.append(line).append('\n')
        }

        val text = builder.toString().trim()
        return if (text.length <= maxChars) text else text.take(maxChars).trimEnd() + "…"
    }

    /** 安装包体积，给用户一个"要下多少"的预期。 */
    fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "—"
        if (bytes < 1_024) return "$bytes B"
        val kb = bytes / 1_024.0
        if (kb < 1_024) return String.format(Locale.US, "%.0f KB", kb)
        return String.format(Locale.US, "%.1f MB", kb / 1_024.0)
    }

    /** `.sha256` 附件的内容形如 `<64 位十六进制>  EchoTimer-v1.6.0.apk`。 */
    fun parseChecksum(text: String): String? =
        Regex("[0-9a-fA-F]{64}").find(text)?.value?.lowercase(Locale.US)

    private fun Map<*, *>.string(key: String): String? = this[key] as? String

    private fun Map<*, *>.long(key: String): Long? =
        (this[key] as? Long) ?: (this[key] as? Double)?.toLong()

    /** Atom 里第一个 `<entry>`（GitHub 按时间倒序，第一条就是最新发布）。 */
    private fun firstEntry(xml: String): String? {
        val start = xml.indexOf("<entry>").takeIf { it >= 0 } ?: return null
        val end = xml.indexOf("</entry>", start).takeIf { it > start } ?: return null
        return xml.substring(start, end)
    }

    private fun firstMatch(text: String, pattern: String): String? =
        Regex(pattern, RegexOption.DOT_MATCHES_ALL).find(text)?.groupValues?.getOrNull(1)

    private fun unescapeHtml(raw: String): String = raw
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
        // &amp; 必须最后替换，否则 &amp;lt; 会被二次解码成 <。
        .replace("&amp;", "&")

    /**
     * 去标签：块级标签换成换行（保住段落），其余直接删掉
     * （保留成空格会让"</strong>："变成" ："）。
     */
    private fun stripHtml(raw: String): String = raw
        // 段落结束留一个空行（cleanNotes 会把连续空行折成一个），换行标签只留一个换行。
        .replace(Regex("(?i)</p\\s*>|</div\\s*>"), "\n\n")
        .replace(Regex("(?i)<br\\s*/?>|</li\\s*>"), "\n")
        .replace(Regex("<[^>]+>"), "")
        .replace(Regex("[ \\t]{2,}"), " ")
}
