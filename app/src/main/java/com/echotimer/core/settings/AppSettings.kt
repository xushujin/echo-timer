package com.echotimer.core.settings

import com.echotimer.core.theme.Palettes

/** 语音指令用哪个识别引擎。 */
enum class VoiceEngine {
    /** 系统识别可用就用系统，否则用离线口令识别。 */
    AUTO,

    /** 只用系统语音识别服务（需要设备装了 RecognitionService）。 */
    SYSTEM,

    /** 只用自研离线口令识别。 */
    OFFLINE,
}

/**
 * 离线口令识别的灵敏度。
 *
 * 数值是"命中门限 = 类间距离 × 该比例"里的比例：越高越容易命中，也越容易误触发。
 */
enum class OfflineSensitivity(val ratio: Float) {
    LOW(0.42f),
    MEDIUM(0.52f),
    HIGH(0.62f),
}

/** 主题明暗模式。SYSTEM 表示跟随系统。 */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

/**
 * 全局设置快照。整个应用只有这一份设置模型，
 * 所有界面都从 [SettingsRepository.settings] 读取、通过 [SettingsRepository.update] 写入。
 */
data class AppSettings(
    /** 配色 id，取值见 [Palettes.ALL]。 */
    val paletteId: String = Palettes.DEFAULT_ID,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** 是否开启定时语音播报。 */
    val announcementsEnabled: Boolean = true,
    /**
     * 播报间隔（秒）：单选，语义是"每隔 N 秒播报一次当前时长"。
     * 例如 5 表示在 5s、10s、15s… 各播报一次；1 表示每秒播报一次。
     */
    val announcementIntervalSeconds: Int = DEFAULT_INTERVAL_SECONDS,
    /** 是否开启"开始/结束"等语音指令。 */
    val voiceCommandsEnabled: Boolean = false,
    /** 开始与结束是否也播报。 */
    val speakStartAndStop: Boolean = true,
    /** 结束计时后是否自动写入历史记录。 */
    val autoSaveSessions: Boolean = true,
    /** 计时过程中保持屏幕常亮。 */
    val keepScreenOn: Boolean = true,
    /** 语音识别语言标签（BCP-47）。 */
    val recognitionLanguage: String = "zh-CN",
    /** 语音指令引擎。 */
    val voiceEngine: VoiceEngine = VoiceEngine.AUTO,
    /** 离线口令识别灵敏度。 */
    val offlineSensitivity: OfflineSensitivity = OfflineSensitivity.MEDIUM,
    /**
     * 是否允许修改榜单数据（手工录入、删除成绩、删除榜单）。
     * 默认关闭 = 只读：成绩只能通过"开始挑战"产生，防止误删或手动改分。
     */
    val leaderboardEditable: Boolean = false,
    /** 是否启用「同步到 GitHub 仓库」（可选功能，默认关闭）。 */
    val syncEnabled: Boolean = false,
    /** 仓库，形如 owner/name；也接受粘贴完整地址。 */
    val syncRepo: String = "",
    val syncBranch: String = "main",
    /** 仓库里的文件路径（NDJSON）。 */
    val syncPath: String = DEFAULT_SYNC_PATH,
    /** 访问令牌：只存在本机，不会写进同步文件。 */
    val syncToken: String = "",
    /**
     * 同步时以本地为准：跳过合并，直接用本地数据覆盖仓库文件。
     * 默认关闭（默认是双向合并，谁的数据都不丢）。
     */
    val syncForceLocal: Boolean = false,
) {

    /** 归一化：非法值回落默认，保证从磁盘读到的脏数据不会把界面带崩。 */
    fun sanitized(): AppSettings = copy(
        paletteId = paletteId.takeIf { it in Palettes.ids() } ?: Palettes.DEFAULT_ID,
        announcementIntervalSeconds = announcementIntervalSeconds
            .coerceIn(MIN_INTERVAL_SECONDS, MAX_INTERVAL_SECONDS),
        recognitionLanguage = recognitionLanguage.takeIf { it.isNotBlank() } ?: "zh-CN",
        syncBranch = syncBranch.trim().ifBlank { "main" },
        syncPath = syncPath.trim().trim('/').ifBlank { DEFAULT_SYNC_PATH },
    )

    companion object {
        const val MIN_INTERVAL_SECONDS = 1
        const val MAX_INTERVAL_SECONDS = 3_600

        /** 默认 5 秒一次：比每秒播报更不吵，也符合"按间隔报时"的直觉。 */
        const val DEFAULT_INTERVAL_SECONDS = 5

        /** 同步文件默认路径：和 docs/index.html 里读的路径保持一致。 */
        const val DEFAULT_SYNC_PATH = "echotimer/leaderboard.ndjson"

        /** 设置界面提供的可选间隔。 */
        val SELECTABLE_INTERVALS: List<Int> = listOf(1, 2, 5, 10, 15, 30, 60, 120, 300, 600)
    }
}
