package com.echotimer.core.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCodecTest {

    private val releaseJson = """
        {
          "url": "https://api.github.com/repos/xushujin/echo-timer/releases/7",
          "tag_name": "v1.6.0",
          "name": "EchoTimer v1.6.0",
          "draft": false,
          "prerelease": false,
          "published_at": "2026-10-30T05:10:00Z",
          "html_url": "https://github.com/xushujin/echo-timer/releases/tag/v1.6.0",
          "body": "## 新功能\n\n- **应用内更新**\n- 修了点小问题\n",
          "author": {"login": "xushujin", "id": 42, "avatar_url": "https://example.com/a.png"},
          "assets": [
            {
              "url": "https://api.github.com/assets/1",
              "name": "EchoTimer-v1.6.0.apk",
              "size": 7340032,
              "download_count": 3,
              "browser_download_url": "https://github.com/xushujin/echo-timer/releases/download/v1.6.0/EchoTimer-v1.6.0.apk"
            },
            {
              "url": "https://api.github.com/assets/2",
              "name": "EchoTimer-v1.6.0.apk.sha256",
              "size": 96,
              "browser_download_url": "https://github.com/xushujin/echo-timer/releases/download/v1.6.0/EchoTimer-v1.6.0.apk.sha256"
            }
          ]
        }
    """.trimIndent()

    @Test
    fun parsesReleaseWithAssets() {
        val info = UpdateCodec.parseRelease(releaseJson)
        requireNotNull(info)

        assertEquals("v1.6.0", info.tagName)
        assertEquals(AppVersion(1, 6, 0), info.version)
        assertEquals("EchoTimer v1.6.0", info.title)
        assertEquals("https://github.com/xushujin/echo-timer/releases/tag/v1.6.0", info.pageUrl)
        assertEquals("2026-10-30T05:10:00Z", info.publishedAt)

        val apk = info.apk
        requireNotNull(apk)
        assertEquals("EchoTimer-v1.6.0.apk", apk.name)
        assertEquals(7_340_032L, apk.sizeBytes)
        assertTrue(apk.downloadUrl.endsWith("EchoTimer-v1.6.0.apk"))
        assertTrue(info.checksumUrl.orEmpty().endsWith(".apk.sha256"))
    }

    @Test
    fun skipsDraftAndPrerelease() {
        assertNull(UpdateCodec.parseRelease(releaseJson.replace("\"draft\": false", "\"draft\": true")))
        assertNull(UpdateCodec.parseRelease(releaseJson.replace("\"prerelease\": false", "\"prerelease\": true")))
    }

    @Test
    fun returnsNullWithoutTagOrWithGarbage() {
        assertNull(UpdateCodec.parseRelease("""{"name":"no tag","assets":[]}"""))
        assertNull(UpdateCodec.parseRelease("not json"))
        assertNull(UpdateCodec.parseRelease("""{"tag_name":"nightly"}"""))
    }

    @Test
    fun keepsReleaseEvenWithoutApk() {
        // 只发了说明、没有安装包：要能识别出来，界面才好说"没有提供安装包"。
        val json = """{"tag_name":"v2.0.0","name":"v2","body":"","assets":[]}"""
        val info = UpdateCodec.parseRelease(json)
        requireNotNull(info)
        assertNull(info.apk)
        assertNull(info.checksumUrl)
    }

    @Test
    fun cleansMarkdownNotes() {
        val notes = UpdateCodec.cleanNotes(
            "# 标题\n\n\n- 第一行\n- 第二行\n\n**加粗** 与 `代码`\n",
        )
        assertEquals("标题\n\n- 第一行\n- 第二行\n\n加粗 与 代码", notes)
    }

    @Test
    fun truncatesLongNotes() {
        val notes = UpdateCodec.cleanNotes("x".repeat(20), maxChars = 10)
        assertEquals("xxxxxxxxxx…", notes)
        assertEquals("", UpdateCodec.cleanNotes("   \n\n  "))
    }

    @Test
    fun formatsSize() {
        assertEquals("—", UpdateCodec.formatSize(0))
        assertEquals("500 B", UpdateCodec.formatSize(500))
        assertEquals("2 KB", UpdateCodec.formatSize(2_048))
        assertEquals("7.0 MB", UpdateCodec.formatSize(7_340_032))
    }

    @Test
    fun parsesChecksumFile() {
        val hash = "A1B2C3D4E5F60718293A4B5C6D7E8F90112233445566778899AABBCCDDEEFF00"
        assertEquals(
            hash.lowercase(),
            UpdateCodec.parseChecksum("$hash  EchoTimer-v1.6.0.apk\n"),
        )
        assertNull(UpdateCodec.parseChecksum("没有哈希"))
        assertNotNull(UpdateCodec.parseChecksum(hash))
    }

    // ---------------- Atom 兜底（API 被限流时用） ----------------

    private val atomFeed = """
        <?xml version="1.0" encoding="UTF-8"?>
        <feed xmlns="http://www.w3.org/2005/Atom">
          <title>Release notes from echo-timer</title>
          <entry>
            <id>tag:github.com,2008:Repository/1/v1.7.0</id>
            <updated>2026-11-01T02:58:48Z</updated>
            <link rel="alternate" type="text/html" href="https://github.com/xushujin/echo-timer/releases/tag/v1.7.0"/>
            <title>v1.7.0</title>
            <content type="html">&lt;p&gt;&lt;strong&gt;新功能&lt;/strong&gt;：离线识别&lt;/p&gt;&lt;p&gt;修了点小问题&lt;/p&gt;</content>
          </entry>
          <entry>
            <id>tag:github.com,2008:Repository/1/v1.6.0</id>
            <updated>2026-10-30T02:58:48Z</updated>
            <link rel="alternate" type="text/html" href="https://github.com/xushujin/echo-timer/releases/tag/v1.6.0"/>
            <title>v1.6.0</title>
            <content type="html">旧版本</content>
          </entry>
        </feed>
    """.trimIndent()

    @Test
    fun parsesAtomFeed() {
        val info = UpdateCodec.parseAtomFeed(atomFeed, "xushujin/echo-timer")
        requireNotNull(info)

        assertEquals("v1.7.0", info.tagName)
        assertEquals(AppVersion(1, 7, 0), info.version)
        assertEquals("v1.7.0", info.title)
        assertEquals("2026-11-01T02:58:48Z", info.publishedAt)
        assertEquals("https://github.com/xushujin/echo-timer/releases/tag/v1.7.0", info.pageUrl)

        val apk = info.apk
        requireNotNull(apk)
        assertEquals("EchoTimer-v1.7.0.apk", apk.name)
        assertEquals(
            "https://github.com/xushujin/echo-timer/releases/download/v1.7.0/EchoTimer-v1.7.0.apk",
            apk.downloadUrl,
        )
        assertEquals("${apk.downloadUrl}.sha256", info.checksumUrl)
    }

    @Test
    fun atomFeedUsesOnlyLatestEntry() {
        val info = UpdateCodec.parseAtomFeed(atomFeed, "xushujin/echo-timer")
        requireNotNull(info)
        // 第二条是 v1.6.0，不能被误当成最新版本。
        assertEquals(AppVersion(1, 7, 0), info.version)
    }

    @Test
    fun atomFeedStripsHtml() {
        val info = UpdateCodec.parseAtomFeed(atomFeed, "xushujin/echo-timer")
        requireNotNull(info)
        assertEquals("新功能：离线识别\n\n修了点小问题", info.notes)
    }

    @Test
    fun atomFeedWithoutEntryIsNull() {
        assertNull(UpdateCodec.parseAtomFeed("<feed></feed>", "xushujin/echo-timer"))
        assertNull(UpdateCodec.parseAtomFeed("", "xushujin/echo-timer"))
    }
}
