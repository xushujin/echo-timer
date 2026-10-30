package com.echotimer.core.update

/**
 * 版本号，只比较「主.次.修」三段。
 *
 * 为什么不直接用 versionCode：GitHub 的 release 元数据里没有 versionCode，
 * 只有 tag（`v1.6.0`）。发布流程保证了 tag 与 `appVersionName` 一致、versionCode 单调递增，
 * 所以按语义化版本比较就够了；下载完还会再用 `PackageManager` 校验一次真实 versionCode。
 *
 * 解析规则：允许 `v` / `V` 前缀、忽略 `-debug` / `+build` 这类后缀、缺的段补 0。
 */
data class AppVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
) : Comparable<AppVersion> {

    override fun compareTo(other: AppVersion): Int {
        if (major != other.major) return major.compareTo(other.major)
        if (minor != other.minor) return minor.compareTo(other.minor)
        return patch.compareTo(other.patch)
    }

    override fun toString(): String = "$major.$minor.$patch"

    companion object {

        /** 解析失败返回 null（不做"猜一个版本"的兜底，避免把错误 tag 当成新版本）。 */
        fun parse(raw: String?): AppVersion? {
            val text = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            val core = text
                .removePrefix("v")
                .removePrefix("V")
                .substringBefore('-')
                .substringBefore('+')
            if (core.isEmpty()) return null

            val parts = core.split('.')
            val numbers = ArrayList<Int>(3)
            for (part in parts.take(3)) {
                numbers.add(part.toIntOrNull() ?: return null)
            }
            return AppVersion(
                major = numbers.getOrElse(0) { 0 },
                minor = numbers.getOrElse(1) { 0 },
                patch = numbers.getOrElse(2) { 0 },
            )
        }
    }
}
