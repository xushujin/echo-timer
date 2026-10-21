package com.echotimer.data.db

/**
 * 一条数据库迁移。
 *
 * 约定：迁移只描述"从上一版本到 [toVersion] 要做的事"，必须幂等无副作用地按序执行，
 * 且**禁止**出现 DROP TABLE / DELETE FROM / 重建表等破坏性语句（由 MigrationsTest 静态校验）。
 */
data class Migration(
    val toVersion: Int,
    val statements: List<String>,
)

/**
 * 数据库 schema 与迁移的唯一来源。
 *
 * 版本策略：
 * - 首次安装（[EchoDb.onCreate]）会把 1..VERSION 的所有迁移依次跑一遍；
 * - 升级安装（[EchoDb.onUpgrade]）只跑 oldVersion+1..newVersion。
 * 因此"新装"与"升级"得到的 schema 必然一致，无需靠肉眼对齐建表语句。
 *
 * 当前版本历史：
 * - v1：sessions / laps 两张表 + 两个索引。
 * - v2：sessions 增加 note 列（历史记录重命名）。
 * - v3：leaderboards / leaderboard_entries 两张表 + 一个索引（天梯榜）。
 */
object Migrations {

    const val VERSION: Int = 3

    val ALL: List<Migration> = listOf(
        Migration(
            toVersion = 1,
            statements = listOf(
                """
                CREATE TABLE sessions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    started_at INTEGER NOT NULL,
                    ended_at INTEGER NOT NULL,
                    total_ms INTEGER NOT NULL,
                    created_at INTEGER NOT NULL
                )
                """.trimIndent(),
                """
                CREATE TABLE laps (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    session_id INTEGER NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
                    lap_index INTEGER NOT NULL,
                    lap_ms INTEGER NOT NULL,
                    total_ms INTEGER NOT NULL
                )
                """.trimIndent(),
                "CREATE INDEX idx_sessions_started_at ON sessions(started_at DESC)",
                "CREATE INDEX idx_laps_session_id ON laps(session_id, lap_index)",
            ),
        ),
        Migration(
            toVersion = 2,
            statements = listOf(
                "ALTER TABLE sessions ADD COLUMN note TEXT NOT NULL DEFAULT ''",
            ),
        ),
        Migration(
            toVersion = 3,
            statements = listOf(
                """
                CREATE TABLE leaderboards (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL COLLATE NOCASE UNIQUE,
                    created_at INTEGER NOT NULL
                )
                """.trimIndent(),
                """
                CREATE TABLE leaderboard_entries (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    board_id INTEGER NOT NULL REFERENCES leaderboards(id) ON DELETE CASCADE,
                    player TEXT NOT NULL COLLATE NOCASE,
                    best_ms INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL,
                    UNIQUE(board_id, player)
                )
                """.trimIndent(),
                "CREATE INDEX idx_leaderboard_entries_board ON leaderboard_entries(board_id, best_ms DESC)",
            ),
        ),
    )

    /**
     * 取出 (fromVersion, toVersion] 区间内的迁移，按版本升序。
     * 区间内有缺口时由 [EchoDb] 抛异常，而不是悄悄建出一张缺列的表。
     */
    fun pending(fromVersion: Int, toVersion: Int): List<Migration> =
        ALL.filter { it.toVersion in (fromVersion + 1)..toVersion }.sortedBy { it.toVersion }
}
