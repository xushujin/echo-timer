package com.echotimer.core.update

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/** 更新源的固定配置：更新只认官方发布页，不跟随同步仓库设置（那可能是用户的私有仓库）。 */
object UpdateConfig {
    const val REPO = "xushujin/echo-timer"

    /** 发布页（给"打开发布页"用）。 */
    const val RELEASES_PAGE = "https://github.com/$REPO/releases"
}

/**
 * 读 GitHub 的最新发布。
 *
 * 主路径是 API（`releases/latest`，字段最全）；被限流或解析失败时回退到
 * 发布页的 Atom 订阅——未认证的 API 每小时只有 60 次配额且按出口 IP 计算，
 * 移动网络共用 NAT 时很容易被别人的请求吃光，没有兜底就等于"更新永远失败"。
 *
 * 只用 `HttpsURLConnection`，不引第三方 HTTP 库。
 */
class UpdateClient(
    private val repo: String = UpdateConfig.REPO,
    private val apiBase: String = "https://api.github.com",
    private val connectTimeoutMs: Int = 15_000,
    private val readTimeoutMs: Int = 20_000,
) {

    sealed interface Result {
        data class Found(val info: ReleaseInfo) : Result

        /** [retryWithAtom] 为 true 表示值得再用 Atom 试一次（限流、解析失败、网络抖动）。 */
        data class Error(val message: String, val retryWithAtom: Boolean = false) : Result
    }

    fun fetchLatest(): Result {
        val primary = fetchFromApi()
        if (primary is Result.Error && primary.retryWithAtom) {
            val fallback = fetchFromAtom()
            if (fallback is Result.Found) return fallback
        }
        return primary
    }

    private fun fetchFromApi(): Result {
        val connection = try {
            open("$apiBase/repos/$repo/releases/latest", accept = "application/vnd.github+json")
        } catch (_: IOException) {
            return Result.Error("网络连接失败，请检查网络", retryWithAtom = true)
        }
        return try {
            val code = connection.responseCode
            val body = readBody(connection)
            when (code) {
                HttpURLConnection.HTTP_OK -> {
                    val info = UpdateCodec.parseRelease(body)
                    if (info == null) {
                        Result.Error("发布信息解析失败", retryWithAtom = true)
                    } else {
                        Result.Found(info)
                    }
                }

                // 还没有发布过任何正式版本，回退也查不到。
                HttpURLConnection.HTTP_NOT_FOUND -> Result.Error("发布页还没有正式版本")
                403, 429 -> Result.Error(
                    "GitHub 暂时限流，请稍后再试",
                    retryWithAtom = true,
                )

                else -> Result.Error("检查更新失败（HTTP $code）", retryWithAtom = true)
            }
        } catch (_: IOException) {
            Result.Error("检查更新失败，请检查网络", retryWithAtom = true)
        } finally {
            connection.disconnect()
        }
    }

    private fun fetchFromAtom(): Result {
        val connection = try {
            open("https://github.com/$repo/releases.atom", accept = "application/atom+xml")
        } catch (_: IOException) {
            return Result.Error("网络连接失败，请检查网络")
        }
        return try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                Result.Error("检查更新失败（HTTP ${connection.responseCode}）")
            } else {
                val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                val info = UpdateCodec.parseAtomFeed(body, repo)
                if (info == null) Result.Error("发布信息解析失败") else Result.Found(info)
            }
        } catch (_: IOException) {
            Result.Error("检查更新失败，请检查网络")
        } finally {
            connection.disconnect()
        }
    }

    private fun open(url: String, accept: String): HttpsURLConnection =
        (URL(url).openConnection() as HttpsURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            setRequestProperty("Accept", accept)
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            // GitHub 要求带 User-Agent，否则直接 403。
            setRequestProperty("User-Agent", "EchoTimer")
        }

    private fun readBody(connection: HttpsURLConnection): String {
        val stream = if (connection.responseCode in 200..299) {
            connection.inputStream
        } else {
            connection.errorStream
        } ?: return ""
        return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }
}
