package com.echotimer.ui.share

import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException

/**
 * 把榜单图片交出去：分享给其它应用，或存进系统相册。
 *
 * 走 FileProvider（`com.echotimer.fileprovider`）而不是 `file://`：
 * Android 7.0 起直接用 file:// 会抛 FileUriExposedException。
 */
object ImageSharer {

    private const val AUTHORITY_SUFFIX = ".fileprovider"
    private const val CACHE_DIR = "shared"

    /** 系统相册写入用 MediaStore 的 RELATIVE_PATH，需要 Android 10（API 29）起。 */
    val canSaveToGallery: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    /** 写入缓存目录并返回可对外授权的 Uri；失败返回 null。 */
    fun cacheFileUri(context: Context, bitmap: Bitmap, fileName: String): Uri? {
        return try {
            val dir = File(context.cacheDir, CACHE_DIR).apply { mkdirs() }
            val file = File(dir, fileName)
            file.outputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
            FileProvider.getUriForFile(context, context.packageName + AUTHORITY_SUFFIX, file)
        } catch (_: IOException) {
            null
        }
    }

    /** 打开系统分享面板。返回是否成功唤起（没有可分享的应用时返回 false）。 */
    fun shareBitmap(context: Context, bitmap: Bitmap, fileName: String, chooserTitle: String): Boolean {
        val uri = cacheFileUri(context, bitmap, fileName) ?: return false
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, chooserTitle).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(chooser)
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }

    /**
     * 存进系统相册（Pictures/EchoTimer）。仅在 Android 10+ 可用，
     * 低版本写相册需要 WRITE_EXTERNAL_STORAGE，应用不申请这个权限。
     */
    fun saveToGallery(context: Context, bitmap: Bitmap, fileName: String): Boolean {
        if (!canSaveToGallery) return false
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                Environment.DIRECTORY_PICTURES + File.separator + "EchoTimer",
            )
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = try {
            resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        } catch (_: IOException) {
            null
        } ?: return false

        val written = try {
            resolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            } ?: false
        } catch (_: IOException) {
            false
        }
        if (!written) {
            resolver.delete(uri, null, null)
            return false
        }
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return true
    }
}
