#!/usr/bin/env python3
"""用真实 sqlite3 引擎验证 Migrations.kt 中的 SQL。

为什么需要这个脚本：单元测试只能校验"迁移链是连续的、没有 DROP"，无法证明 SQL 真能跑通。
这里把 Kotlin 源码里的 SQL 原文抽出来，交给真实的 sqlite3 执行，验证：

1. 新装路径（1..VERSION 全跑）与升级路径（只跑 v1 → 插数据 → 跑 v2）得到的 schema 完全一致；
2. 升级不丢数据：v1 写入的行仍在，新增列取到默认值；
3. 外键级联删除生效（删 session 时 laps 一起删）；
4. 索引存在；
5. Migrations.VERSION 与最后一条迁移版本一致；
6. 天梯榜 schema 的行为：榜单名唯一（不区分大小写）、同榜同名唯一、按 best_ms 降序、
   删榜级联删成绩。

用法：python3 tools/verify_migrations.py
退出码 0 表示全部通过。
"""

from __future__ import annotations

import re
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MIGRATIONS_KT = ROOT / "app/src/main/java/com/echotimer/data/db/Migrations.kt"

LITERAL_RE = re.compile(r'"""(.*?)"""|"((?:[^"\\\n]|\\.)*)"', re.S)
VERSION_RE = re.compile(r"const val VERSION: Int = (\d+)")
TO_VERSION_RE = re.compile(r"toVersion\s*=\s*(\d+)")


def parse_migrations() -> tuple[int, dict[int, list[str]]]:
    source = MIGRATIONS_KT.read_text(encoding="utf-8")
    version_match = VERSION_RE.search(source)
    if not version_match:
        fail(f"未能在 {MIGRATIONS_KT} 中找到 VERSION")
    declared_version = int(version_match.group(1))

    chunks = source.split("Migration(")[1:]
    migrations: dict[int, list[str]] = {}
    for chunk in chunks:
        to_version = TO_VERSION_RE.search(chunk)
        if not to_version:
            continue
        statements: list[str] = []
        for match in LITERAL_RE.finditer(chunk):
            literal = match.group(1) if match.group(1) is not None else match.group(2)
            text = literal.strip()
            if text:
                statements.append(text)
        migrations[int(to_version.group(1))] = statements
    return declared_version, migrations


def fail(message: str) -> None:
    print(f"FAIL  {message}")
    sys.exit(1)


def check(name: str, condition: bool, detail: str = "") -> None:
    if condition:
        print(f"PASS  {name}")
    else:
        fail(f"{name}{(' — ' + detail) if detail else ''}")


def apply_script(db_path: Path, statements: list[str]) -> None:
    script = ";\n".join(statements) + ";"
    result = subprocess.run(
        ["sqlite3", str(db_path)],
        input=script,
        text=True,
        capture_output=True,
    )
    if result.returncode != 0:
        fail(f"sqlite3 执行失败：{result.stderr.strip()}\n--- 脚本 ---\n{script}")


def query(db_path: Path, sql: str) -> str:
    result = subprocess.run(
        ["sqlite3", str(db_path)],
        input=sql,
        text=True,
        capture_output=True,
    )
    if result.returncode != 0:
        fail(f"查询失败：{sql}\n{result.stderr.strip()}")
    return result.stdout.strip()


def schema(db_path: Path) -> str:
    return query(db_path, ".schema")


def run_sql(db_path: Path, sql: str) -> subprocess.CompletedProcess:
    return subprocess.run(["sqlite3", str(db_path)], input=sql, text=True, capture_output=True)


def verify_leaderboard_schema(db_path: Path) -> None:
    """用真实 sqlite3 验证天梯榜 schema 的关键约束与排序。"""
    result = run_sql(
        db_path,
        """
        INSERT INTO leaderboards (name, created_at) VALUES ('晨跑', 1000);
        INSERT INTO leaderboards (name, created_at) VALUES ('晚练', 2000);
        INSERT INTO leaderboard_entries (board_id, player, best_ms, updated_at) VALUES (1, 'Tom', 5000, 1000);
        INSERT INTO leaderboard_entries (board_id, player, best_ms, updated_at) VALUES (1, 'Amy', 9000, 1000);
        INSERT INTO leaderboard_entries (board_id, player, best_ms, updated_at) VALUES (1, '张三', 7000, 1000);
        """,
    )
    check("天梯榜可写入榜单与成绩", result.returncode == 0, result.stderr)

    duplicate_board = run_sql(
        db_path,
        "INSERT INTO leaderboards (name, created_at) VALUES ('晨跑', 3000);",
    )
    check("榜单名唯一约束生效", duplicate_board.returncode != 0, duplicate_board.stdout)

    run_sql(db_path, "INSERT INTO leaderboards (name, created_at) VALUES ('Morning', 3000);")
    case_variant_board = run_sql(
        db_path,
        "INSERT INTO leaderboards (name, created_at) VALUES ('MORNING', 4000);",
    )
    check(
        "榜单名唯一性不区分大小写（NOCASE）",
        case_variant_board.returncode != 0,
        case_variant_board.stdout,
    )

    duplicate_player = run_sql(
        db_path,
        "INSERT INTO leaderboard_entries (board_id, player, best_ms, updated_at) VALUES (1, 'tom', 1, 1000);",
    )
    check("同榜同名唯一且不区分大小写", duplicate_player.returncode != 0, duplicate_player.stdout)

    order = query(
        db_path,
        "SELECT player FROM leaderboard_entries WHERE board_id = 1 ORDER BY best_ms DESC;",
    ).splitlines()
    check("成绩按 best_ms 降序", order == ["Amy", "张三", "Tom"], str(order))

    run_sql(db_path, "PRAGMA foreign_keys=ON; DELETE FROM leaderboards WHERE id = 1;")
    remaining = query(db_path, "SELECT COUNT(*) FROM leaderboard_entries WHERE board_id = 1;")
    check("删除榜单时级联删除成绩", remaining == "0", remaining)


def main() -> None:
    if shutil.which("sqlite3") is None:
        fail("未找到 sqlite3 命令行工具")

    declared_version, migrations = parse_migrations()
    check("能解析出迁移定义", bool(migrations), str(migrations.keys()))
    check(
        "Migrations.VERSION 等于最大迁移版本",
        declared_version == max(migrations),
        f"VERSION={declared_version}, 最大迁移={max(migrations)}",
    )
    check(
        "迁移版本从 1 开始且连续",
        sorted(migrations) == list(range(1, max(migrations) + 1)),
        str(sorted(migrations)),
    )

    workdir = Path(tempfile.mkdtemp(prefix="echotimer-sql-"))
    try:
        fresh_db = workdir / "fresh.db"
        upgraded_db = workdir / "upgraded.db"

        # 路径 A：全新安装，按顺序跑完所有迁移。
        for version in sorted(migrations):
            apply_script(fresh_db, migrations[version])

        # 路径 B：只跑 v1，写入数据，再逐版本升级。
        first_version = min(migrations)
        apply_script(upgraded_db, migrations[first_version])
        apply_script(
            upgraded_db,
            [
                "INSERT INTO sessions (started_at, ended_at, total_ms, created_at) "
                "VALUES (1000, 61000, 60000, 1000)",
                "INSERT INTO laps (session_id, lap_index, lap_ms, total_ms) VALUES (1, 1, 30000, 30000)",
                "INSERT INTO laps (session_id, lap_index, lap_ms, total_ms) VALUES (1, 2, 30000, 60000)",
            ],
        )
        for version in sorted(migrations):
            if version > first_version:
                apply_script(upgraded_db, migrations[version])

        fresh_schema = schema(fresh_db)
        upgraded_schema = schema(upgraded_db)
        check(
            "新装与升级后的 schema 完全一致",
            fresh_schema == upgraded_schema,
            f"\n--- fresh ---\n{fresh_schema}\n--- upgraded ---\n{upgraded_schema}",
        )

        columns = query(fresh_db, "PRAGMA table_info(sessions)")
        check(
            "sessions 表包含 note 列",
            "note" in columns,
            columns,
        )
        check(
            "laps 表存在",
            "CREATE TABLE laps" in fresh_schema,
        )
        index_names = query(
            fresh_db,
            "SELECT name FROM sqlite_master WHERE type='index' AND name LIKE 'idx_%' ORDER BY name",
        ).splitlines()
        check(
            "索引齐全",
            index_names == [
                "idx_laps_session_id",
                "idx_leaderboard_entries_board",
                "idx_sessions_started_at",
            ],
            str(index_names),
        )

        preserved = query(upgraded_db, "SELECT total_ms, note FROM sessions WHERE id = 1")
        check("升级后原有数据仍在且新列取默认值", preserved == "60000|", preserved)

        lap_count = query(upgraded_db, "SELECT COUNT(*) FROM laps")
        check("升级后计次数据仍在", lap_count == "2", lap_count)

        # 外键级联：应用在 onConfigure 里开启外键，这里显式打开后再删。
        after_delete = query(
            upgraded_db,
            "PRAGMA foreign_keys=ON; DELETE FROM sessions WHERE id = 1; SELECT COUNT(*) FROM laps;",
        ).splitlines()[-1]
        check("删除 session 时级联删除 laps", after_delete == "0", after_delete)

        # 约束校验：note 不允许 NULL。
        null_note_failed = subprocess.run(
            ["sqlite3", str(fresh_db)],
            input="INSERT INTO sessions (started_at, ended_at, total_ms, created_at, note) "
            "VALUES (1, 2, 3, 4, NULL);",
            text=True,
            capture_output=True,
        )
        check(
            "note 列 NOT NULL 约束生效",
            null_note_failed.returncode != 0,
            null_note_failed.stdout + null_note_failed.stderr,
        )

        verify_leaderboard_schema(fresh_db)

        print(f"\n全部通过：{len(migrations)} 条迁移，数据库版本 v{declared_version}")
    finally:
        shutil.rmtree(workdir, ignore_errors=True)


if __name__ == "__main__":
    main()
