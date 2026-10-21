package com.echotimer.data

import android.content.ContentValues
import android.database.Cursor
import com.echotimer.data.db.EchoDb
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * 历史记录仓库：所有数据库读写都在 IO 线程完成，界面只订阅 [sessions]。
 * 每次写操作后重新查询一次，保证 UI 与磁盘一致（数据量很小，代价可忽略）。
 */
class HistoryRepository(
    private val db: EchoDb,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {

    private val _sessions = MutableStateFlow<List<TimingSession>>(emptyList())
    val sessions: StateFlow<List<TimingSession>> = _sessions.asStateFlow()

    suspend fun refresh() = withContext(io) {
        _sessions.value = querySessions()
    }

    /** 写入一条记录（含计次），返回新记录 id。 */
    suspend fun save(draft: SessionDraft): Long = withContext(io) {
        val database = db.writableDatabase
        database.beginTransaction()
        try {
            val sessionId = database.insertOrThrow(
                TABLE_SESSIONS,
                null,
                ContentValues().apply {
                    put(COL_STARTED_AT, draft.startedAt)
                    put(COL_ENDED_AT, draft.endedAt)
                    put(COL_TOTAL_MS, draft.totalMs)
                    put(COL_NOTE, draft.note)
                    put(COL_CREATED_AT, System.currentTimeMillis())
                },
            )
            for (lap in draft.laps) {
                database.insertOrThrow(
                    TABLE_LAPS,
                    null,
                    ContentValues().apply {
                        put(COL_SESSION_ID, sessionId)
                        put(COL_LAP_INDEX, lap.index)
                        put(COL_LAP_MS, lap.lapMs)
                        put(COL_TOTAL_MS, lap.totalMs)
                    },
                )
            }
            database.setTransactionSuccessful()
            _sessions.value = querySessions()
            sessionId
        } finally {
            database.endTransaction()
        }
    }

    suspend fun delete(sessionId: Long) = withContext(io) {
        db.writableDatabase.delete(TABLE_SESSIONS, "$COL_ID = ?", arrayOf(sessionId.toString()))
        _sessions.value = querySessions()
    }

    suspend fun rename(sessionId: Long, note: String) = withContext(io) {
        db.writableDatabase.update(
            TABLE_SESSIONS,
            ContentValues().apply { put(COL_NOTE, note.trim()) },
            "$COL_ID = ?",
            arrayOf(sessionId.toString()),
        )
        _sessions.value = querySessions()
    }

    private fun querySessions(): List<TimingSession> {
        val database = db.readableDatabase
        val rows = ArrayList<SessionRow>(LIMIT)
        database.rawQuery(
            """
            SELECT id, started_at, ended_at, total_ms, note
            FROM sessions
            ORDER BY started_at DESC, id DESC
            LIMIT $LIMIT
            """.trimIndent(),
            null,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                rows.add(
                    SessionRow(
                        id = cursor.getLong(0),
                        startedAt = cursor.getLong(1),
                        endedAt = cursor.getLong(2),
                        totalMs = cursor.getLong(3),
                        note = cursor.getString(4) ?: "",
                    ),
                )
            }
        }
        if (rows.isEmpty()) return emptyList()
        val lapsBySession = queryLaps(rows.map { it.id })
        return rows.map { row ->
            TimingSession(
                id = row.id,
                startedAt = row.startedAt,
                endedAt = row.endedAt,
                totalMs = row.totalMs,
                note = row.note,
                laps = lapsBySession[row.id] ?: emptyList(),
            )
        }
    }

    private fun queryLaps(sessionIds: List<Long>): Map<Long, List<LapRecord>> {
        val placeholders = sessionIds.joinToString(",") { "?" }
        val result = HashMap<Long, MutableList<LapRecord>>(sessionIds.size)
        db.readableDatabase.rawQuery(
            """
            SELECT session_id, lap_index, lap_ms, total_ms
            FROM laps
            WHERE session_id IN ($placeholders)
            ORDER BY session_id, lap_index
            """.trimIndent(),
            sessionIds.map { it.toString() }.toTypedArray(),
        ).use { cursor: Cursor ->
            while (cursor.moveToNext()) {
                val sessionId = cursor.getLong(0)
                result.getOrPut(sessionId) { mutableListOf() }.add(
                    LapRecord(
                        index = cursor.getInt(1),
                        lapMs = cursor.getLong(2),
                        totalMs = cursor.getLong(3),
                    ),
                )
            }
        }
        return result
    }

    private data class SessionRow(
        val id: Long,
        val startedAt: Long,
        val endedAt: Long,
        val totalMs: Long,
        val note: String,
    )

    private companion object {
        const val LIMIT = 200
        const val TABLE_SESSIONS = "sessions"
        const val TABLE_LAPS = "laps"
        const val COL_ID = "id"
        const val COL_STARTED_AT = "started_at"
        const val COL_ENDED_AT = "ended_at"
        const val COL_TOTAL_MS = "total_ms"
        const val COL_NOTE = "note"
        const val COL_CREATED_AT = "created_at"
        const val COL_SESSION_ID = "session_id"
        const val COL_LAP_INDEX = "lap_index"
        const val COL_LAP_MS = "lap_ms"
    }
}
