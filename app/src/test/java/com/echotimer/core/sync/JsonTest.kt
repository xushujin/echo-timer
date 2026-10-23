package com.echotimer.core.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class JsonTest {

    @Test
    fun escapeAndParseRoundTrip() {
        val raw = "带\"引号\" 和 \\ 反斜杠\n换行\t制表"
        val line = "{\"text\":\"" + Json.escape(raw) + "\"}"
        val fields = Json.parseFlatObject(line)
        requireNotNull(fields)
        assertEquals(raw, fields["text"])
    }

    @Test
    fun parsesStringsNumbersAndBooleans() {
        val fields = Json.parseFlatObject("{\"s\":\"v\",\"n\":42,\"neg\":-7,\"b\":true,\"nil\":null}")
        requireNotNull(fields)
        assertEquals("v", fields["s"])
        assertEquals(42L, fields["n"])
        assertEquals(-7L, fields["neg"])
        assertEquals(true, fields["b"])
        assertEquals(Json.NULL, fields["nil"])
    }

    @Test
    fun skipsNestedObjectsLikeGithubResponse() {
        // GitHub 的 contents 响应里有 _links / git 等嵌套结构，解析器要能跳过而不是整体失败。
        val body = """
            {"name":"leaderboard.ndjson","sha":"abc123","size":99,
             "content":"aGVsbG8=","encoding":"base64",
             "_links":{"self":"https://api.github.com/x","git":"https://api.github.com/y"},
             "git":{"sha":"abc123","type":"blob"}}
        """.trimIndent()
        val fields = Json.parseFlatObject(body)
        requireNotNull(fields)
        assertEquals("abc123", fields["sha"])
        assertEquals("aGVsbG8=", fields["content"])
        assertEquals(99L, fields["size"])
    }

    @Test
    fun rejectsInvalidInput() {
        assertNull(Json.parseFlatObject("not json"))
        assertNull(Json.parseFlatObject("{\"a\":}"))
        assertNull(Json.parseFlatObject("{\"a\":1"))
        assertNull(Json.parseFlatObject("[1,2,3]"))
    }

    @Test
    fun decodesUnicodeEscapes() {
        val fields = Json.parseFlatObject("{\"name\":\"\\u5355\\u6760\"}")
        requireNotNull(fields)
        assertEquals("单杠", fields["name"])
    }

    @Test
    fun nestedArrayIsSkipped() {
        val fields = Json.parseFlatObject("{\"tags\":[1,2,[3]],\"sha\":\"s\"}")
        requireNotNull(fields)
        assertEquals("s", fields["sha"])
        assertNotNull(fields["tags"])
    }
}
