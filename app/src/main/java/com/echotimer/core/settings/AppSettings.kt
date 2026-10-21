package com.echotimer.core.settings

import com.echotimer.core.theme.Palettes

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
) {

    /** 归一化：非法值回落默认，保证从磁盘读到的脏数据不会把界面带崩。 */
    fun sanitized(): AppSettings = copy(
        paletteId = paletteId.takeIf { it in Palettes.ids() } ?: Palettes.DEFAULT_ID,
        announcementIntervalSeconds = announcementIntervalSeconds
            .coerceIn(MIN_INTERVAL_SECONDS, MAX_INTERVAL_SECONDS),
        recognitionLanguage = recognitionLanguage.takeIf { it.isNotBlank() } ?: "zh-CN",
    )

    companion object {
        const val MIN_INTERVAL_SECONDS = 1
        const val MAX_INTERVAL_SECONDS = 3_600

        /** 默认 5 秒一次：比每秒播报更不吵，也符合"按间隔报时"的直觉。 */
        const val DEFAULT_INTERVAL_SECONDS = 5

        /** 设置界面提供的可选间隔。 */
        val SELECTABLE_INTERVALS: List<Int> = listOf(1, 2, 5, 10, 15, 30, 60, 120, 300, 600)
    }
}
