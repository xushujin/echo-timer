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
        /** 本次是「以本地为准」的覆盖模式（没有做合并）。 */
        val forced: Boolean = false,
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

        // 「以本地为准」：不做合并，直接把本地写上去（仓库历史里仍可找回旧版本）。
        if (settings.syncForceLocal) return@withContext overwriteRemote(config)

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

    /**
     * 覆盖模式：本地 → 仓库，单向写入。
     *
     * 两条保护：本地为空时拒绝（否则等于清空仓库）；内容与仓库一致时不提交。
     */
    private suspend fun overwriteRemote(config: SyncConfig): SyncResult {
        val local = repository.snapshot()
        if (!SyncPolicy.canOverwrite(local)) {
            return SyncResult.Failed("本地还没有数据，覆盖会把仓库清空，已取消")
        }

        val fetched = client.fetch(config)
        val sha = when (fetched) {
            is GitHubClient.FetchResult.Found -> fetched.sha
            GitHubClient.FetchResult.Missing -> null
            is GitHubClient.FetchResult.Error -> return SyncResult.Failed(fetched.message)
        }

        if (fetched is GitHubClient.FetchResult.Found &&
            SyncMerger.sameData(local, SyncCodec.decode(fetched.content))
        ) {
            return SyncResult.Success(
                pushed = false,
                boards = local.boards.size,
                entries = local.entries.size,
                addedEntries = 0,
                improvedEntries = 0,
                fileUrl = fileUrl(config),
                forced = true,
            )
        }

        val content = SyncCodec.encode(local.copy(updatedAt = System.currentTimeMillis()))
        var push = client.push(config, content, sha, "sync: 用本地覆盖仓库（EchoTimer）")
        if (push == GitHubClient.PushResult.Conflict) {
            val retry = client.fetch(config)
            if (retry is GitHubClient.FetchResult.Error) return SyncResult.Failed(retry.message)
            val retrySha = (retry as? GitHubClient.FetchResult.Found)?.sha
            push = client.push(config, content, retrySha, "sync: 用本地覆盖仓库（EchoTimer）")
        }

        return when (push) {
            GitHubClient.PushResult.Success -> SyncResult.Success(
                pushed = true,
                boards = local.boards.size,
                entries = local.entries.size,
                addedEntries = 0,
                improvedEntries = 0,
                fileUrl = fileUrl(config),
                forced = true,
            )

            GitHubClient.PushResult.Conflict -> SyncResult.Failed("仓库内容刚被其它提交修改，请再同步一次")
            is GitHubClient.PushResult.Error -> SyncResult.Failed(push.message)
        }
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
