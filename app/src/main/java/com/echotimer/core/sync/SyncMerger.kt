package com.echotimer.core.sync

/** 合并结果：合并后的全量数据 + 相对本地/远端的变化量，用于界面上说明"同步做了什么"。 */
data class MergeResult(
    val snapshot: SyncSnapshot,
    /** 本地没有、来自远端的榜单数。 */
    val addedBoards: Int,
    /** 本地没有、来自远端的成绩数。 */
    val addedEntries: Int,
    /** 远端成绩比本地更好、被采用的条数。 */
    val improvedEntries: Int,
)

/**
 * 榜单数据的合并规则（纯函数）。
 *
 * 同步是**双向**的：先把本地和仓库里的数据合并成一份，再写回仓库、也写回本地。
 * 因此合并必须满足三条：
 *
 * 1. **幂等**：merge(merge(a,b),b) == merge(a,b)，否则每次同步都会产生新提交；
 * 2. **单调**：成绩只增不减（和 App 内"只有更大才更新"的规则一致），删掉的记录会从远端回来；
 * 3. **以名字为身份**：榜单按名称、成绩按「榜单名 + 玩家名」匹配，本地自增 id 不参与。
 *
 * 名称比较用 `lowercase()`：中文不受影响，英文大小写视为同一个。
 */
object SyncMerger {

    fun merge(local: SyncSnapshot, remote: SyncSnapshot, now: Long): MergeResult {
        val boards = LinkedHashMap<String, SyncBoard>()
        for (board in local.boards) {
            val key = board.name.trim().lowercase()
            if (key.isEmpty()) continue
            boards.putIfAbsent(key, board)
        }

        var addedBoards = 0
        for (board in remote.boards) {
            val key = board.name.trim().lowercase()
            if (key.isEmpty()) continue
            if (!boards.containsKey(key)) {
                boards[key] = board
                addedBoards++
            }
        }

        val entries = LinkedHashMap<String, SyncEntry>()
        for (entry in local.entries) {
            val key = entryKey(entry) ?: continue
            entries.putIfAbsent(key, entry)
        }

        var addedEntries = 0
        var improvedEntries = 0
        for (entry in remote.entries) {
            val key = entryKey(entry) ?: continue
            val existing = entries[key]
            when {
                existing == null -> {
                    entries[key] = entry
                    addedEntries++
                }

                entry.bestMs > existing.bestMs -> {
                    entries[key] = entry
                    improvedEntries++
                }
            }
        }

        // 成绩指向的榜单必须存在（仓库文件可能被人手改过，只留了 entry 行）。
        for (entry in entries.values) {
            val key = entry.board.trim().lowercase()
            if (key.isEmpty() || boards.containsKey(key)) continue
            boards[key] = SyncBoard(name = entry.board, createdAt = entry.updatedAt)
            addedBoards++
        }

        val merged = SyncSnapshot(
            boards = boards.values.toList(),
            entries = entries.values.toList(),
            updatedAt = maxOf(now, local.updatedAt, remote.updatedAt),
        )
        return MergeResult(
            snapshot = merged,
            addedBoards = addedBoards,
            addedEntries = addedEntries,
            improvedEntries = improvedEntries,
        )
    }

    /**
     * 两份数据的内容是否等价（忽略 updatedAt 与顺序）。
     * 用来判断"这次同步到底有没有新东西"，避免每次都产生一个空提交。
     */
    fun sameData(a: SyncSnapshot, b: SyncSnapshot): Boolean {
        val boardsA = a.boards.map { it.name.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
        val boardsB = b.boards.map { it.name.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
        if (boardsA != boardsB) return false
        return entriesSignature(a) == entriesSignature(b)
    }

    private fun entriesSignature(snapshot: SyncSnapshot): Set<String> =
        snapshot.entries
            .mapNotNull { entry -> entryKey(entry)?.let { key -> "$key=${entry.bestMs}" } }
            .toSet()

    private fun entryKey(entry: SyncEntry): String? {
        val board = entry.board.trim().lowercase()
        val player = entry.player.trim().lowercase()
        if (board.isEmpty() || player.isEmpty() || entry.bestMs <= 0L) return null
        return "$board\u0000$player"
    }
}
