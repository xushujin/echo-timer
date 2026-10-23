package com.echotimer.core.sync

import com.echotimer.core.settings.SettingsRepository
import com.echotimer.data.LeaderboardRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 一次同步的结果，界面直接拿来显示原因。 */
sealed interface SyncResult {
    data class Success(
        /** 这次是否真的写了仓库（数据没变化就不提交，避免刷出一堆空 commit）。 */
        val pushed: Boolean,
        val boards: Int,
        val entries: Int,
        /** 本次从仓库拿到、本地新增的成绩条数。 */
        val addedEntries: Int,
        /** 仓库里的成绩比本地更好、被采纳的条数。 */
        val improvedEntries: Int,
        val fileUrl: String,
    ) : SyncResult

    data class NotConfigured(val message: String) : SyncResult

    data class Failed(val message: String) : SyncResult
}

/**
 * 榜单同步：本地 ↔ GitHub 仓库里的一个 NDJSON 文件。
 *
 * 每次同步都是"读远端 → 双向合并 → 写远端 → 写回本地"，
 * 所以多台设备各自同步也能收敛：成绩只增不减，删掉的记录下次会从远端回来。
 *
 * 内容没变化时**不会提交**（否则每点一次同步就多一个空 commit）。
 */
class LeaderboardSyncManager(
    private val settingsRepository: SettingsRepository,
    private val repository: LeaderboardRepository,
    private val client: GitHubClient = GitHubClient(),
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {

    suspend fun sync(): SyncResult = withContext(io) {
        val settings = settingsRepository.settings.value
        val config = SyncConfig(
            repo = settings.syncRepo,
            branch = settings.syncBranch,
            path = settings.syncPath,
            token = settings.syncToken,
        )
        config.problem()?.let { return@withContext SyncResult.NotConfigured(it) }

        val local = repository.snapshot()
        var remoteFile = client.fetch(config)
        var remote = when (remoteFile) {
            is GitHubClient.FetchResult.Found -> SyncCodec.decode(remoteFile.content)
            GitHubClient.FetchResult.Missing -> SyncSnapshot()
            is GitHubClient.FetchResult.Error -> return@withContext SyncResult.Failed(remoteFile.message)
        }

        var merged = SyncMerger.merge(local, remote, now = 0L)
        if (local.isEmpty && remote.isEmpty) {
            return@withContext SyncResult.Success(
                pushed = false,
                boards = 0,
                entries = 0,
                addedEntries = 0,
                improvedEntries = 0,
                fileUrl = fileUrl(config),
            )
        }

        val needsPush = !SyncMerger.sameData(merged.snapshot, remote)
        var pushed = false
        if (needsPush) {
            val snapshot = merged.snapshot.copy(updatedAt = System.currentTimeMillis())
            val content = SyncCodec.encode(snapshot)
            var sha = (remoteFile as? GitHubClient.FetchResult.Found)?.sha
            var push = client.push(config, content, sha, commitMessage(merged))

            if (push == GitHubClient.PushResult.Conflict) {
                // 远端在我们读取后被改过：重新读一次，再合并再写。
                remoteFile = client.fetch(config)
                if (remoteFile is GitHubClient.FetchResult.Found) {
                    remote = SyncCodec.decode(remoteFile.content)
                    merged = SyncMerger.merge(merged.snapshot, remote, now = 0L)
                    sha = remoteFile.sha
                    push = client.push(
                        config = config,
                        content = SyncCodec.encode(merged.snapshot.copy(updatedAt = System.currentTimeMillis())),
                        sha = sha,
                        message = commitMessage(merged),
                    )
                }
            }

            when (push) {
                GitHubClient.PushResult.Success -> pushed = true
                GitHubClient.PushResult.Conflict -> return@withContext SyncResult.Failed(
                    "仓库内容刚被其它提交修改，请再同步一次",
                )

                is GitHubClient.PushResult.Error -> return@withContext SyncResult.Failed(push.message)
            }
        }

        // 把合并结果写回本地（远端独有的榜单/成绩也要落到 SQLite）。
        repository.mergeRemote(merged.snapshot)

        SyncResult.Success(
            pushed = pushed,
            boards = merged.snapshot.boards.size,
            entries = merged.snapshot.entries.size,
            addedEntries = merged.addedEntries,
            improvedEntries = merged.improvedEntries,
            fileUrl = fileUrl(config),
        )
    }

    private fun fileUrl(config: SyncConfig): String =
        "https://github.com/${config.normalizedRepo}/blob/${config.normalizedBranch}/${config.normalizedPath}"

    private fun commitMessage(result: MergeResult): String {
        val parts = ArrayList<String>()
        if (result.addedBoards > 0) parts.add("新增 ${result.addedBoards} 个榜单")
        if (result.addedEntries > 0) parts.add("新增 ${result.addedEntries} 条成绩")
        if (result.improvedEntries > 0) parts.add("刷新 ${result.improvedEntries} 条成绩")
        val detail = if (parts.isEmpty()) "同步榜单" else parts.joinToString("、")
        return "sync: $detail（EchoTimer）"
    }
}
