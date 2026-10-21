package com.echotimer.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * 手写 SQLite 数据层（不引 Room 等 ORM）。
 *
 * 迁移策略是硬约束，不是建议：
 * - [onUpgrade] 只按 [Migrations] 声明的显式迁移逐版本执行；
 * - 迁移链条缺版本时直接抛异常，绝不"删库重建"；
 * - [onDowngrade] 抛异常而不是 drop，避免降级安装静默丢数据。
 */
class EchoDb(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    DATABASE_NAME,
    null,
    Migrations.VERSION,
) {

    override fun onConfigure(db: SQLiteDatabase) {
        // 必须在事务外开启，否则 SQLite 会忽略该设置。
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        applyMigrations(db, fromVersion = 0, toVersion = Migrations.VERSION)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        applyMigrations(db, fromVersion = oldVersion, toVersion = newVersion)
    }

    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        throw IllegalStateException(
            "不支持数据库降级：当前 $oldVersion，目标 $newVersion。请卸载重装或安装更高版本。",
        )
    }

    private fun applyMigrations(db: SQLiteDatabase, fromVersion: Int, toVersion: Int) {
        val steps = Migrations.pending(fromVersion, toVersion)
        val expected = (fromVersion + 1..toVersion).toList()
        check(steps.map { it.toVersion } == expected) {
            "数据库迁移链不完整：需要 $expected，实际 ${steps.map { it.toVersion }}。" +
                "请补齐 Migrations 中的显式迁移，禁止 destructive migration。"
        }
        for (step in steps) {
            for (statement in step.statements) {
                db.execSQL(statement)
            }
        }
    }

    companion object {
        const val DATABASE_NAME = "echotimer.db"
    }
}
