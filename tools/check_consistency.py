#!/usr/bin/env python3
"""文档 / 构建脚本 / 代码 的一致性检查。

覆盖的"逐项核对"：
1. 技术栈版本：根 build.gradle.kts 的插件版本 == app/build.gradle.kts 注入的 BuildConfig == README 表格；
2. 版本号：appVersionName == README 当前版本 == 变更记录最新条目；
3. SDK 范围：minSdk / targetSdk == README 与关于页文案；
4. 字符串资源：代码引用的 R.string.* 必须存在，strings.xml 里也不允许有未使用项（与 lint 的 UnusedResources 呼应）；
5. 权限：AndroidManifest 声明的权限必须在 README 与关于页文案里出现；
6. 测试数量：README 表格里每个测试类的用例数 == 源码中 @Test 数量，总数 == 各文件之和；
7. 数据库版本：Migrations.VERSION == README「表结构（当前 vN）」，且版本历史表覆盖每个版本；
8. 产物命名：构建脚本里的 APK 文件名 == README 中的产物路径。

用法：python3 tools/check_consistency.py
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
APP_GRADLE = ROOT / "app/build.gradle.kts"
ROOT_GRADLE = ROOT / "build.gradle.kts"
WRAPPER = ROOT / "gradle/wrapper/gradle-wrapper.properties"
README = ROOT / "README.md"
STRINGS = ROOT / "app/src/main/res/values/strings.xml"
MANIFEST = ROOT / "app/src/main/AndroidManifest.xml"
JAVA_SRC = ROOT / "app/src/main/java"

problems: list[str] = []


def require(condition: bool, message: str) -> None:
    print(("PASS  " if condition else "FAIL  ") + message)
    if not condition:
        problems.append(message)


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def gradle_prop(text: str, key: str) -> str | None:
    """读取 `key = "value"` 形式的字符串常量。"""
    match = re.search(rf'{key}\s*=\s*"([^"]+)"', text)
    return match.group(1) if match else None


def gradle_int(text: str, key: str) -> str | None:
    """读取 `key = 26` 形式的整数常量。"""
    match = re.search(rf'{key}\s*=\s*(\d+)', text)
    return match.group(1) if match else None


def build_config_string(text: str, key: str) -> str | None:
    """读取 buildConfigField("String", "KEY", "\"value\"") 注入的字符串。"""
    match = re.search(rf'buildConfigField\("String", "{key}", "\\"([^"]+)\\""\)', text)
    return match.group(1) if match else None


def main() -> None:
    app_gradle = read(APP_GRADLE)
    root_gradle = read(ROOT_GRADLE)
    readme = read(README)
    strings_xml = read(STRINGS)
    manifest = read(MANIFEST)

    kotlin_plugin = re.search(r'id\("org\.jetbrains\.kotlin\.android"\) version "([^"]+)"', root_gradle)
    agp_plugin = re.search(r'id\("com\.android\.application"\) version "([^"]+)"', root_gradle)
    wrapper_version = re.search(r"gradle-([\d.]+)-bin\.zip", read(WRAPPER))
    compose_bom = re.search(r'compose-bom:([\d.]+)', app_gradle)

    require(kotlin_plugin is not None, "根构建脚本声明了 Kotlin 插件版本")
    require(agp_plugin is not None, "根构建脚本声明了 AGP 版本")
    require(wrapper_version is not None, "wrapper 中能解析出 Gradle 版本")
    require(compose_bom is not None, "app 构建脚本声明了 Compose BOM 版本")
    if problems:
        sys.exit(1)

    tech = {
        "KOTLIN": (build_config_string(app_gradle, "TECH_KOTLIN"), kotlin_plugin.group(1)),
        "COMPOSE_BOM": (build_config_string(app_gradle, "TECH_COMPOSE_BOM"), compose_bom.group(1)),
        "AGP": (build_config_string(app_gradle, "TECH_AGP"), agp_plugin.group(1)),
        "GRADLE": (build_config_string(app_gradle, "TECH_GRADLE"), wrapper_version.group(1)),
    }
    for name, (injected, declared) in tech.items():
        require(
            injected == declared,
            f"{name}: BuildConfig 注入值 {injected} 与实际声明 {declared} 一致",
        )
        require(declared in readme, f"README 中出现了 {name} 版本 {declared}")

    version_name = gradle_prop(app_gradle, "appVersionName")
    version_code = re.search(r"val appVersionCode = (\d+)", app_gradle)
    require(version_name is not None and version_name in readme, f"README 中出现了版本号 {version_name}")
    require(version_code is not None, "构建脚本声明了 versionCode")

    min_sdk = gradle_int(app_gradle, "minSdk")
    target_sdk = gradle_int(app_gradle, "targetSdk")
    require(min_sdk is not None and f"API {min_sdk}" in readme, f"README 标注了最低 API {min_sdk}")
    require(target_sdk is not None and f"API {target_sdk}" in readme, f"README 标注了目标 API {target_sdk}")
    require(f"API {min_sdk}" in strings_xml, "关于页标注了最低 API")
    require(f"API {target_sdk}" in strings_xml, "关于页标注了目标 API")

    # ---- 字符串资源 ----
    defined = set(re.findall(r'<string name="([^"]+)"', strings_xml))
    referenced: set[str] = set()
    for kt in JAVA_SRC.rglob("*.kt"):
        referenced.update(re.findall(r"R\.string\.([a-z0-9_]+)", read(kt)))
    missing = sorted(referenced - defined)
    unused = sorted(defined - referenced)
    require(not missing, f"代码引用的字符串资源都存在（缺失：{missing}）")
    require(not unused, f"没有未使用的字符串资源（多余：{unused}）")

    # ---- 权限 ----
    permissions = re.findall(r'<uses-permission android:name="android\.permission\.([A-Z_]+)"', manifest)
    require(bool(permissions), "清单声明了权限")
    for permission in permissions:
        require(permission in readme, f"README 说明了权限 {permission}")
        require(permission in strings_xml, f"关于页说明了权限 {permission}")

    # ---- 测试数量 ----
    test_counts = {
        path.stem: len(re.findall(r"@Test\b", read(path)))
        for path in sorted((ROOT / "app/src/test").rglob("*Test.kt"))
    }
    total_tests = sum(test_counts.values())
    documented = {
        name: int(count) for name, count in re.findall(r"\|\s*`(\w+Test)`\s*\|\s*(\d+)\s*\|", readme)
    }
    for name, count in sorted(documented.items()):
        require(
            test_counts.get(name) == count,
            f"README 中 {name} 用例数 {count} == 实际 {test_counts.get(name)}",
        )
    undocumented = sorted(set(test_counts) - set(documented))
    require(not undocumented, f"每个测试类都在 README 表格里（缺少：{undocumented}）")
    require(
        f"（{total_tests} 个 JVM 单测）" in readme,
        f"README 技术栈表标注了 {total_tests} 个 JVM 单测",
    )
    require(
        f"单元测试（{total_tests} 个" in readme,
        f"README 质量章节标注了 {total_tests} 个单元测试",
    )
    require(
        f"{total_tests} 个单元测试全部通过" in readme,
        f"README 已验证清单标注了 {total_tests} 个单元测试",
    )

    # ---- 数据库版本 ----
    migrations_kt = read(ROOT / "app/src/main/java/com/echotimer/data/db/Migrations.kt")
    declared_version = re.search(r"const val VERSION: Int = (\d+)", migrations_kt)
    require(declared_version is not None, "Migrations.kt 声明了 VERSION")
    if declared_version:
        version = declared_version.group(1)
        require(f"当前 v{version}" in readme, f"README 标注了数据库版本 v{version}")
        for number in range(1, int(version) + 1):
            require(f"| v{number} |" in readme, f"README 版本历史包含 v{number}")

    # ---- 产物命名 ----
    require(
        'rename { "EchoTimer.apk" }' in app_gradle,
        "构建脚本把 release 产物命名为 EchoTimer.apk",
    )
    require("build/dist/EchoTimer.apk" in readme, "README 使用 build/dist/EchoTimer.apk")
    require("distDebugApk" in app_gradle, "构建脚本提供 distDebugApk 任务")
    require("build/dist/debug/EchoTimer-debug.apk" in readme, "README 标注了调试产物路径")

    print()
    if problems:
        print(f"共 {len(problems)} 项不一致，请修正后重跑。")
        sys.exit(1)
    print("全部一致。")


if __name__ == "__main__":
    main()
