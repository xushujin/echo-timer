package com.echotimer.data

import android.content.ContentValues
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import com.echotimer.core.leaderboard.CreateBoardResult
import com.echotimer.core.leaderboard.Leaderboard
import com.echotimer.core.leaderboard.LeaderboardEntry
import com.echotimer.core.leaderboard.LeaderboardRules
import com.echotimer.core.leaderboard.ScoreOutcome
import com.echotimer.core.leaderboard.SubmitResult
import com.echotimer.data.db.EchoDb
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * 天梯榜数据仓库。
 *
 * 三条业务规则在这里落地（判定逻辑本身在 [LeaderboardRules]，可单测）：
 * 1. 榜单名唯一：数据库 UNIQUE + NOCASE 兜底，重复插入返回 [CreateBoardResult.NameTaken]；
 * 2. 同名成绩只有**严格更大**才更新，否则保持原记录；
 * 3. 榜单按秒数降序（`ORDER BY best_ms DESC`）。
 */
class LeaderboardRepository(
    private val db: EchoDb,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {

    private val _boards = MutableStateFlow<List<Leaderboard>>(emptyList())
    val boards: StateFlow<List<Leaderboard>> = _boards.asStateFlow()

    private val _entries = MutableStateFlow<List<LeaderboardEntry>>(emptyList())
    val entries: StateFlow<List<LeaderboardEntry>> = _entries.asStateFlow()

    private var selectedBoardId: Long? = null

    suspend fun refresh() = withContext(io) {
        _boards.value = queryBoards()
        selectedBoardId?.let { _entries.value = queryEntries(it) }
    }

    /** 选中某个榜单，加载它的成绩列表。 */
    suspend fun selectBoard(boardId: Long) = withContext(io) {
        selectedBoardId = boardId
        _entries.value = queryEntries(boardId)
    }

    suspend fun createBoard(rawName: String): CreateBoardResult = withContext(io) {
        val name = LeaderboardRules.normalizeName(rawName)
        if (LeaderboardRules.validateName(name) != null) return@withContext CreateBoardResult.InvalidName

        val id = try {
            db.writableDatabase.insertOrThrow(
                TABLE_BOARDS,
                null,
                ContentValues().apply {
                    put(COL_NAME, name)
                    put(COL_CREATED_AT, System.currentTimeMillis())
                },
            )
        } catch (_: SQLiteConstraintException) {
            return@withContext CreateBoardResult.NameTaken
        }
        _boards.value = queryBoards()
        CreateBoardResult.Created(id)
    }

    suspend fun deleteBoard(boardId: Long) = withContext(io) {
        db.writableDatabase.delete(TABLE_BOARDS, "$COL_ID = ?", arrayOf(boardId.toString()))
        if (selectedBoardId == boardId) {
            selectedBoardId = null
            _entries.value = emptyList()
        }
        _boards.value = queryBoards()
    }

    /** 删除榜单里的一条成绩（榜单本身保留）。 */
    suspend fun deleteEntry(entryId: Long) = withContext(io) {
        db.writableDatabase.delete(TABLE_ENTRIES, "$COL_ID = ?", arrayOf(entryId.toString()))
        selectedBoardId?.let { _entries.value = queryEntries(it) }
        _boards.value = queryBoards()
    }

    /** 提交一次成绩：没有记录则新增，比原记录大才更新，否则保持原记录。 */
    suspend fun submit(boardId: Long, rawPlayer: String, elapsedMs: Long): SubmitResult =
        withContext(io) {
            val player = LeaderboardRules.normalizeName(rawPlayer)
            val database = db.writableDatabase
            var previousBestMs: Long? = null
            var outcome = ScoreOutcome.NEW_ENTRY
            val now = System.currentTimeMillis()

            database.beginTransaction()
            try {
                previousBestMs = queryBestMs(database, boardId, player)
                outcome = LeaderboardRules.decide(previousBestMs, elapsedMs)
                when (outcome) {
                    ScoreOutcome.NEW_ENTRY -> database.insertOrThrow(
                        TABLE_ENTRIES,
                        null,
                        ContentValues().apply {
                            put(COL_BOARD_ID, boardId)
                            put(COL_PLAYER, player)
                            put(COL_BEST_MS, elapsedMs)
                            put(COL_UPDATED_AT, now)
                        },
                    )

                    ScoreOutcome.IMPROVED -> database.update(
                        TABLE_ENTRIES,
                        ContentValues().apply {
                            put(COL_BEST_MS, elapsedMs)
                            put(COL_UPDATED_AT, now)
                        },
                        "$COL_BOARD_ID = ? AND $COL_PLAYER = ?",
                        arrayOf(boardId.toString(), player),
                    )

                    ScoreOutcome.KEPT -> Unit
                }
                database.setTransactionSuccessful()
            } finally {
                database.endTransaction()
            }

            _entries.value = queryEntries(boardId)
            _boards.value = queryBoards()
            val bestMs = if (outcome == ScoreOutcome.KEPT) previousBestMs ?: elapsedMs else elapsedMs
            SubmitResult(
                outcome = outcome,
                submittedMs = elapsedMs,
                bestMs = bestMs,
                previousBestMs = previousBestMs,
            )
        }

    private fun queryBoards(): List<Leaderboard> {
        val sql = """
            SELECT b.id, b.name, b.created_at, COUNT(e.id) AS entry_count, MAX(e.best_ms) AS best_ms
            FROM leaderboards b
            LEFT JOIN leaderboard_entries e ON e.board_id = b.id
            GROUP BY b.id
            ORDER BY b.created_at DESC, b.id DESC
        """.trimIndent()
        val result = ArrayList<Leaderboard>()
        db.readableDatabase.rawQuery(sql, null).use { cursor ->
            val bestColumn = cursor.getColumnIndexOrThrow("best_ms")
            while (cursor.moveToNext()) {
                result.add(
                    Leaderboard(
                        id = cursor.getLong(0),
                        name = cursor.getString(1),
                        createdAt = cursor.getLong(2),
                        entryCount = cursor.getInt(3),
                        bestMs = if (cursor.isNull(bestColumn)) null else cursor.getLong(bestColumn),
                    ),
                )
            }
        }
        return result
    }

    private fun queryEntries(boardId: Long): List<LeaderboardEntry> {
        val sql = """
            SELECT id, board_id, player, best_ms, updated_at
            FROM leaderboard_entries
            WHERE board_id = ?
            ORDER BY best_ms DESC, updated_at ASC, player ASC
        """.trimIndent()
        val result = ArrayList<LeaderboardEntry>()
        db.readableDatabase.rawQuery(sql, arrayOf(boardId.toString())).use { cursor ->
            while (cursor.moveToNext()) {
                result.add(
                    LeaderboardEntry(
                        id = cursor.getLong(0),
                        boardId = cursor.getLong(1),
                        player = cursor.getString(2),
                        bestMs = cursor.getLong(3),
                        updatedAt = cursor.getLong(4),
                    ),
                )
            }
        }
        return result
    }

    private fun queryBestMs(database: SQLiteDatabase, boardId: Long, player: String): Long? {
        database.rawQuery(
            "SELECT best_ms FROM leaderboard_entries WHERE board_id = ? AND player = ?",
            arrayOf(boardId.toString(), player),
        ).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getLong(0) else null
        }
    }

    private companion object {
        const val TABLE_BOARDS = "leaderboards"
        const val TABLE_ENTRIES = "leaderboard_entries"
        const val COL_ID = "id"
        const val COL_NAME = "name"
        const val COL_CREATED_AT = "created_at"
        const val COL_BOARD_ID = "board_id"
        const val COL_PLAYER = "player"
        const val COL_BEST_MS = "best_ms"
        const val COL_UPDATED_AT = "updated_at"
    }
}
