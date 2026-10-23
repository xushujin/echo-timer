package com.echotimer.core.sync

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Base64
import javax.net.ssl.HttpsURLConnection

/**
 * 用 GitHub Contents API 读写仓库里的一个文件。
 *
 * 为什么不用 JGit：Android 上要引入完整 git 实现，体积和复杂度都不划算；
 * 这里只需要"读一个文件、写一个文件"，REST API 足够，而且天然带版本历史。
 *
 * 只用 `HttpsURLConnection`，不引第三方 HTTP 库。
 */
class GitHubClient(
    private val apiBase: String = "https://api.github.com",
    private val connectTimeoutMs: Int = 15_000,
    private val readTimeoutMs: Int = 20_000,
) {

    sealed interface FetchResult {
        data class Found(val content: String, val sha: String) : FetchResult

        /** 文件还不存在（第一次同步）。 */
        data object Missing : FetchResult

        data class Error(val message: String) : FetchResult
    }

    sealed interface PushResult {
        data object Success : PushResult

        /** 远端在我们读取之后被改过，sha 失效，需要重新读取再合并。 */
        data object Conflict : PushResult

        data class Error(val message: String) : PushResult
    }

    fun fetch(config: SyncConfig): FetchResult {
        val url = contentsUrl(config) + "?ref=" + encode(config.normalizedBranch)
        val connection = try {
            open(url, "GET", config.token)
        } catch (_: IOException) {
            return FetchResult.Error("网络连接失败，请检查网络")
        }
        return try {
            val code = connection.responseCode
            val body = readBody(connection)
            when (code) {
                HttpURLConnection.HTTP_OK -> parseFile(body)
                HttpURLConnection.HTTP_NOT_FOUND -> FetchResult.Missing
                else -> FetchResult.Error(describe(code, body))
            }
        } catch (_: IOException) {
            FetchResult.Error("读取仓库失败，请检查网络")
        } finally {
            connection.disconnect()
        }
    }

    fun push(config: SyncConfig, content: String, sha: String?, message: String): PushResult {
        val payload = buildString {
            append("{\"message\":\"").append(Json.escape(message)).append("\",")
            append("\"content\":\"")
                .append(Base64.getEncoder().encodeToString(content.toByteArray(Charsets.UTF_8)))
                .append("\",")
            append("\"branch\":\"").append(Json.escape(config.normalizedBranch)).append("\"")
            if (sha != null) append(",\"sha\":\"").append(Json.escape(sha)).append("\"")
            append("}")
        }

        val connection = try {
            open(contentsUrl(config), "PUT", config.token)
        } catch (_: IOException) {
            return PushResult.Error("网络连接失败，请检查网络")
        }
        return try {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val body = readBody(connection)
            when (code) {
                HttpURLConnection.HTTP_OK, HttpURLConnection.HTTP_CREATED -> PushResult.Success
                HttpURLConnection.HTTP_CONFLICT -> PushResult.Conflict
                422 -> PushResult.Error("参数被拒绝（422）：可能是分支不存在或路径非法")
                else -> PushResult.Error(describe(code, body))
            }
        } catch (_: IOException) {
            PushResult.Error("写入仓库失败，请检查网络")
        } finally {
            connection.disconnect()
        }
    }

    private fun parseFile(body: String): FetchResult {
        val fields = Json.parseFlatObject(body)
            ?: return FetchResult.Error("无法解析 GitHub 响应")
        val sha = fields["sha"] as? String ?: return FetchResult.Error("响应缺少 sha")
        val encoded = (fields["content"] as? String)?.replace("\n", "")?.replace("\r", "")
            ?: return FetchResult.Error("响应缺少 content")
        val text = try {
            String(Base64.getDecoder().decode(encoded), Charsets.UTF_8)
        } catch (_: IllegalArgumentException) {
            return FetchResult.Error("文件内容不是合法的 base64")
        }
        return FetchResult.Found(text, sha)
    }

    private fun open(url: String, method: String, token: String): HttpsURLConnection {
        val connection = URL(url).openConnection() as HttpsURLConnection
        connection.requestMethod = method
        connection.connectTimeout = connectTimeoutMs
        connection.readTimeout = readTimeoutMs
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        connection.setRequestProperty("User-Agent", "EchoTimer")
        return connection
    }

    private fun readBody(connection: HttpsURLConnection): String {
        val stream = if (connection.responseCode in 200..299) {
            connection.inputStream
        } else {
            connection.errorStream
        } ?: return ""
        return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    private fun contentsUrl(config: SyncConfig): String {
        val path = config.normalizedPath.split("/").joinToString("/") { encode(it) }
        return "$apiBase/repos/${config.normalizedRepo}/contents/$path"
    }

    private fun describe(code: Int, body: String): String {
        val reason = when (code) {
            401 -> "Token 无效或已过期"
            403 -> "没有仓库写入权限，或触发了 GitHub 限流"
            404 -> "仓库或分支不存在"
            409 -> "远端刚被其它提交修改"
            422 -> "参数不合法（分支或路径）"
            else -> "请求失败"
        }
        val detail = body.trim().take(160)
        return if (detail.isEmpty()) "$reason（HTTP $code）" else "$reason（HTTP $code）：$detail"
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}
