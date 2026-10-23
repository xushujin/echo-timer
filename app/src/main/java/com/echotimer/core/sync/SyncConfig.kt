package com.echotimer.core.sync

/**
 * 同步目标配置。
 *
 * [repo] 允许粘贴完整地址（`https://github.com/owner/name`、`owner/name.git` 等），
 * 统一在 [normalizedRepo] 里收敛成 `owner/name`，避免用户复制浏览器地址后同步失败。
 */
data class SyncConfig(
    val repo: String,
    val branch: String = "main",
    val path: String = DEFAULT_PATH,
    val token: String = "",
) {

    val normalizedRepo: String get() = normalizeRepo(repo)

    val normalizedBranch: String get() = branch.trim().ifBlank { "main" }

    val normalizedPath: String get() = path.trim().trim('/').ifBlank { DEFAULT_PATH }

    /** 配置是否完整到可以发起同步。 */
    fun problem(): String? = when {
        normalizedRepo.isEmpty() -> "仓库地址不完整，应形如 owner/name"
        token.isBlank() -> "缺少访问令牌（Token）"
        else -> null
    }

    companion object {
        const val DEFAULT_PATH = "echotimer/leaderboard.ndjson"

        /** 把各种写法的仓库地址收敛成 `owner/name`；无法识别时返回空串。 */
        fun normalizeRepo(raw: String): String {
            var value = raw.trim()
            if (value.isEmpty()) return ""
            // 兼容 git@github.com:owner/repo 这种 SSH 写法
            if (value.startsWith("git@")) {
                val colon = value.indexOf(':')
                if (colon > 0) value = value.substring(colon + 1)
            }
            value = value.removePrefix("https://").removePrefix("http://")
            value = value.removePrefix("www.")
            value = value.removePrefix("github.com/")
            value = value.removeSuffix("/")
            value = value.removeSuffix(".git")
            value = value.trim('/')
            val parts = value.split("/").filter { it.isNotBlank() }
            if (parts.size != 2) return ""
            return "${parts[0]}/${parts[1]}"
        }
    }
}
