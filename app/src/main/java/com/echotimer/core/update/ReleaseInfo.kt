package com.echotimer.core.update

/** 发布页里的一个附件（安装包或校验文件）。 */
data class ReleaseAsset(
    val name: String,
    val downloadUrl: String,
    val sizeBytes: Long,
)

/**
 * 一次 GitHub Release 的关键信息。
 *
 * [apk] 为空表示这个 release 没有可安装的 APK（比如只发了说明），
 * 此时界面会明确说"最新版本没有提供安装包"，而不是假装已是最新。
 */
data class ReleaseInfo(
    val tagName: String,
    val version: AppVersion,
    val title: String,
    val notes: String,
    val pageUrl: String,
    val publishedAt: String,
    val apk: ReleaseAsset?,
    /** `.apk.sha256` 附件的下载地址；没有就不做校验（仍会校验包名与 versionCode）。 */
    val checksumUrl: String?,
)
