package com.echotimer.core.settings

/**
 * 设置值的序列化/反序列化，纯函数，便于单测脏数据回落逻辑。
 * 播报间隔是单个 Int，直接由 SharedPreferences 存取，不需要额外编解码。
 */
object SettingsCodec {

    fun encodeThemeMode(mode: ThemeMode): String = mode.name

    fun decodeThemeMode(raw: String?): ThemeMode =
        ThemeMode.entries.firstOrNull { it.name == raw } ?: ThemeMode.SYSTEM

    fun encodeVoiceEngine(engine: VoiceEngine): String = engine.name

    fun decodeVoiceEngine(raw: String?): VoiceEngine =
        VoiceEngine.entries.firstOrNull { it.name == raw } ?: VoiceEngine.AUTO

    fun encodeSensitivity(sensitivity: OfflineSensitivity): String = sensitivity.name

    fun decodeSensitivity(raw: String?): OfflineSensitivity =
        OfflineSensitivity.entries.firstOrNull { it.name == raw } ?: OfflineSensitivity.MEDIUM
}
