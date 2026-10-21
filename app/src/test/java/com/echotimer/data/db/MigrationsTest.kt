package com.echotimer.data.db

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 迁移链的静态校验：保证"新装"和"升级"走的是同一批语句，
 * 并且永远不会出现 destructive migration（删表/清数据）。
 *
 * 真实 SQL 执行验证见 tools/verify_migrations.py（用 sqlite3 跑真库）。
 */
class MigrationsTest {

    @Test
    fun versionsAreContiguousStartingAtOne() {
        val versions = Migrations.ALL.map { it.toVersion }
        assertEquals((1..Migrations.VERSION).toList(), versions)
        assertEquals(Migrations.VERSION, versions.max())
    }

    @Test
    fun everyMigrationHasStatements() {
        Migrations.ALL.forEach { migration ->
            assertTrue("v${migration.toVersion} 没有语句", migration.statements.isNotEmpty())
            migration.statements.forEach { statement ->
                assertTrue("v${migration.toVersion} 存在空语句", statement.isNotBlank())
            }
        }
    }

    @Test
    fun migrationsNeverDestroyData() {
        val forbidden = listOf("drop table", "drop index", "delete from", "truncate", "vacuum into")
        Migrations.ALL.forEach { migration ->
            migration.statements.forEach { statement ->
                val normalized = statement.lowercase()
                forbidden.forEach { keyword ->
                    assertTrue(
                        "v${migration.toVersion} 出现破坏性语句（$keyword）：$statement",
                        !normalized.contains(keyword),
                    )
                }
            }
        }
    }

    @Test
    fun pendingReturnsOnlyTheRequestedRange() {
        assertEquals(Migrations.ALL, Migrations.pending(0, Migrations.VERSION))
        assertEquals(listOf(2, 3), Migrations.pending(1, Migrations.VERSION).map { it.toVersion })
        assertTrue(Migrations.pending(Migrations.VERSION, Migrations.VERSION).isEmpty())
    }

    @Test
    fun initialSchemaCoversBothTablesAndIndexes() {
        val v1 = Migrations.ALL.first { it.toVersion == 1 }.statements.joinToString("\n").lowercase()
        assertTrue(v1.contains("create table sessions"))
        assertTrue(v1.contains("create table laps"))
        assertTrue(v1.contains("create index idx_sessions_started_at"))
        assertTrue(v1.contains("create index idx_laps_session_id"))
        assertTrue("外键必须级联删除计次", v1.contains("on delete cascade"))
    }

    @Test
    fun leaderboardTablesComeFromAnExplicitMigration() {
        val v3 = Migrations.ALL.first { it.toVersion == 3 }.statements.joinToString("\n").lowercase()
        assertTrue(v3.contains("create table leaderboards"))
        assertTrue(v3.contains("create table leaderboard_entries"))
        assertTrue("榜单名必须唯一", v3.contains("unique"))
        assertTrue("删除榜单时级联删除成绩", v3.contains("on delete cascade"))
        assertTrue(v3.contains("create index idx_leaderboard_entries_board"))
    }

    @Test
    fun noteColumnIsAddedByExplicitMigration() {
        val v2 = Migrations.ALL.first { it.toVersion == 2 }.statements.joinToString("\n").lowercase()
        assertTrue(v2.contains("alter table sessions add column note"))
        assertTrue("新增列必须有默认值，否则老数据无法通过 NOT NULL 约束", v2.contains("default ''"))
    }
}
