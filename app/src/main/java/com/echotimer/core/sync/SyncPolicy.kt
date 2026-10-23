package com.echotimer.core.sync

/**
 * 覆盖模式的保护规则。
 *
 * "以本地为准"意味着仓库文件会被整份替换。如果本地一条数据都没有，
 * 覆盖就等于把仓库清空——这几乎不可能是用户的意图，所以直接拒绝。
 */
object SyncPolicy {

    fun canOverwrite(local: SyncSnapshot): Boolean = !local.isEmpty
}
