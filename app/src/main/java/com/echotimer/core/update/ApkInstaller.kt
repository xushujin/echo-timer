package com.echotimer.core.update

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import javax.net.ssl.HttpsURLConnection

/**
 * 下载并安装新版本 APK。
 *
 * 不用 `DownloadManager`：它把文件放在系统下载目录，安装时还得把 `content://downloads/...`
 * 交给系统安装器（部分 ROM 不给读权限，表现为"解析安装包失败"）。这里自己下到
 * `cache/updates/`，用已有的 FileProvider 授权，路径与权限都在自己手里。
 *
 * 三道校验，任何一道不过就删掉文件、不安装：
 * 1. 发布页的 `.apk.sha256`（有的话）；
 * 2. APK 里的包名必须与当前应用一致；
 * 3. APK 的 versionCode 必须大于当前版本（防止被降级替换）。
 */
object ApkInstaller {

    private const val UPDATE_DIR = "updates"
    private const val MIME_APK = "application/vnd.android.package-archive"
    private const val HEX = "0123456789abcdef"

    sealed interface DownloadResult {
        data class Ready(val file: File) : DownloadResult

        data class Failed(val message: String) : DownloadResult
    }

    /** Android 8 起安装 APK 需要用户为该应用打开"允许安装未知应用"。 */
    fun canInstall(context: Context): Boolean =
        context.packageManager.canRequestPackageInstalls()

    /** 跳到本应用的"安装未知应用"授权页；个别 ROM 没有这个页面时退回系统设置。 */
    fun requestInstallPermission(context: Context) {
        val direct = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(direct)
        } catch (_: ActivityNotFoundException) {
            context.startActivity(
                Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    suspend fun download(
        context: Context,
        info: ReleaseInfo,
        onProgress: (Float) -> Unit,
    ): DownloadResult = withContext(Dispatchers.IO) {
        val asset = info.apk ?: return@withContext DownloadResult.Failed("这个版本没有提供安装包")

        val dir = File(context.cacheDir, UPDATE_DIR)
        dir.mkdirs()
        // 只保留本次下载，避免缓存目录里堆旧包（每个包几 MB）。
        dir.listFiles()?.forEach { it.delete() }
        val target = File(dir, safeFileName(asset.name))

        val connection = try {
            open(asset.downloadUrl, connectTimeoutMs = 20_000, readTimeoutMs = 60_000)
        } catch (_: IOException) {
            return@withContext DownloadResult.Failed("下载失败，请检查网络")
        }

        try {
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                return@withContext DownloadResult.Failed("下载失败（HTTP $code）")
            }
            val total = connection.contentLengthLong.takeIf { it > 0 } ?: asset.sizeBytes
            var written = 0L
            var lastPercent = -1
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        written += read
                        if (total > 0) {
                            val percent = (written * 100 / total).toInt().coerceIn(0, 100)
                            if (percent != lastPercent) {
                                lastPercent = percent
                                onProgress(percent / 100f)
                            }
                        }
                    }
                }
            }
            if (written <= 0L) {
                target.delete()
                return@withContext DownloadResult.Failed("下载到的文件为空，请重试")
            }

            if (info.checksumUrl != null) {
                val expected = fetchChecksum(info.checksumUrl)
                if (expected == null) {
                    target.delete()
                    return@withContext DownloadResult.Failed("校验文件读取失败，已取消安装")
                }
                if (!expected.equals(sha256(target), ignoreCase = true)) {
                    target.delete()
                    return@withContext DownloadResult.Failed("安装包校验失败（SHA-256 不一致），已丢弃")
                }
            }

            val problem = verifyPackage(context, target)
            if (problem != null) {
                target.delete()
                return@withContext DownloadResult.Failed(problem)
            }
            DownloadResult.Ready(target)
        } catch (_: IOException) {
            target.delete()
            DownloadResult.Failed("下载中断，请重试")
        } finally {
            connection.disconnect()
        }
    }

    /** 调起系统安装器；返回是否成功打开（失败时界面提示用户手动去发布页下载）。 */
    fun install(context: Context, file: File): Boolean {
        val uri = try {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } catch (_: IllegalArgumentException) {
            return false
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, MIME_APK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }

    private fun verifyPackage(context: Context, file: File): String? {
        val archive = context.packageManager.getPackageArchiveInfo(file.absolutePath, 0)
            ?: return "安装包无法解析，已丢弃"
        if (archive.packageName != context.packageName) {
            return "安装包与当前应用不匹配，已丢弃"
        }
        val downloaded = PackageInfoCompat.getLongVersionCode(archive)
        val installed = PackageInfoCompat.getLongVersionCode(
            context.packageManager.getPackageInfo(context.packageName, 0),
        )
        if (downloaded <= installed) {
            return "安装包版本（$downloaded）不高于当前版本（$installed），已取消"
        }
        return null
    }

    private fun fetchChecksum(url: String): String? {
        val connection = try {
            open(url, connectTimeoutMs = 15_000, readTimeoutMs = 20_000)
        } catch (_: IOException) {
            return null
        }
        return try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                null
            } else {
                val text = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                UpdateCodec.parseChecksum(text)
            }
        } catch (_: IOException) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        val bytes = digest.digest()
        val builder = StringBuilder(bytes.size * 2)
        for (byte in bytes) {
            val value = byte.toInt()
            builder.append(HEX[(value shr 4) and 0xF]).append(HEX[value and 0xF])
        }
        return builder.toString()
    }

    /** 只取文件名本身并过滤特殊字符，防止附件名里带路径分隔符。 */
    private fun safeFileName(raw: String): String {
        val name = raw.substringAfterLast('/').substringAfterLast('\\')
        val cleaned = name.map { ch ->
            if (ch.isLetterOrDigit() || ch == '.' || ch == '-' || ch == '_') ch else '_'
        }.joinToString("")
        return if (cleaned.endsWith(".apk", ignoreCase = true)) cleaned else "$cleaned.apk"
    }

    private fun open(url: String, connectTimeoutMs: Int, readTimeoutMs: Int): HttpsURLConnection =
        (URL(url).openConnection() as HttpsURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "EchoTimer")
        }
}
