package com.echotimer.core.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 设置的唯一读写入口（单例，由 AppContainer 持有）。
 *
 * "同一个设置只有一套实现"的落地方式：
 * - 内存态只有一份 [MutableStateFlow]；
 * - 磁盘只有一份 SharedPreferences 文件；
 * - 所有写操作都必须走 [update]，写完立即回写内存态，界面靠 collect 自动刷新。
 */
class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    /** 原子地改一项或多项设置；返回值相同则不写盘。 */
    fun update(transform: (AppSettings) -> AppSettings) {
        val current = _settings.value
        val next = transform(current).sanitized()
        if (next == current) return
        write(next)
        _settings.value = next
    }

    /** 设置播报间隔（单选，秒）。 */
    fun setAnnouncementInterval(seconds: Int) {
        update { current -> current.copy(announcementIntervalSeconds = seconds) }
    }

    fun resetToDefaults() {
        update { AppSettings() }
    }

    private fun read(): AppSettings {
        val defaults = AppSettings()
        return AppSettings(
            paletteId = prefs.getString(KEY_PALETTE_ID, defaults.paletteId) ?: defaults.paletteId,
            themeMode = SettingsCodec.decodeThemeMode(prefs.getString(KEY_THEME_MODE, null)),
            announcementsEnabled = prefs.getBoolean(KEY_ANNOUNCEMENTS, defaults.announcementsEnabled),
            announcementIntervalSeconds = prefs.getInt(
                KEY_INTERVAL,
                defaults.announcementIntervalSeconds,
            ),
            voiceCommandsEnabled = prefs.getBoolean(KEY_VOICE_COMMANDS, defaults.voiceCommandsEnabled),
            speakStartAndStop = prefs.getBoolean(KEY_SPEAK_START_STOP, defaults.speakStartAndStop),
            autoSaveSessions = prefs.getBoolean(KEY_AUTO_SAVE, defaults.autoSaveSessions),
            keepScreenOn = prefs.getBoolean(KEY_KEEP_SCREEN_ON, defaults.keepScreenOn),
            recognitionLanguage = prefs.getString(KEY_RECOGNITION_LANGUAGE, defaults.recognitionLanguage)
                ?: defaults.recognitionLanguage,
            voiceEngine = SettingsCodec.decodeVoiceEngine(prefs.getString(KEY_VOICE_ENGINE, null)),
            offlineSensitivity = SettingsCodec.decodeSensitivity(
                prefs.getString(KEY_OFFLINE_SENSITIVITY, null),
            ),
            leaderboardEditable = prefs.getBoolean(
                KEY_LEADERBOARD_EDITABLE,
                defaults.leaderboardEditable,
            ),
            syncEnabled = prefs.getBoolean(KEY_SYNC_ENABLED, defaults.syncEnabled),
            syncRepo = prefs.getString(KEY_SYNC_REPO, defaults.syncRepo) ?: defaults.syncRepo,
            syncBranch = prefs.getString(KEY_SYNC_BRANCH, defaults.syncBranch) ?: defaults.syncBranch,
            syncPath = prefs.getString(KEY_SYNC_PATH, defaults.syncPath) ?: defaults.syncPath,
            syncToken = prefs.getString(KEY_SYNC_TOKEN, defaults.syncToken) ?: defaults.syncToken,
            syncForceLocal = prefs.getBoolean(KEY_SYNC_FORCE_LOCAL, defaults.syncForceLocal),
        ).sanitized()
    }

    private fun write(settings: AppSettings) {
        prefs.edit()
            .putString(KEY_PALETTE_ID, settings.paletteId)
            .putString(KEY_THEME_MODE, SettingsCodec.encodeThemeMode(settings.themeMode))
            .putBoolean(KEY_ANNOUNCEMENTS, settings.announcementsEnabled)
            .putInt(KEY_INTERVAL, settings.announcementIntervalSeconds)
            .putBoolean(KEY_VOICE_COMMANDS, settings.voiceCommandsEnabled)
            .putBoolean(KEY_SPEAK_START_STOP, settings.speakStartAndStop)
            .putBoolean(KEY_AUTO_SAVE, settings.autoSaveSessions)
            .putBoolean(KEY_KEEP_SCREEN_ON, settings.keepScreenOn)
            .putString(KEY_RECOGNITION_LANGUAGE, settings.recognitionLanguage)
            .putString(KEY_VOICE_ENGINE, SettingsCodec.encodeVoiceEngine(settings.voiceEngine))
            .putString(KEY_OFFLINE_SENSITIVITY, SettingsCodec.encodeSensitivity(settings.offlineSensitivity))
            .putBoolean(KEY_LEADERBOARD_EDITABLE, settings.leaderboardEditable)
            .putBoolean(KEY_SYNC_ENABLED, settings.syncEnabled)
            .putString(KEY_SYNC_REPO, settings.syncRepo)
            .putString(KEY_SYNC_BRANCH, settings.syncBranch)
            .putString(KEY_SYNC_PATH, settings.syncPath)
            .putString(KEY_SYNC_TOKEN, settings.syncToken)
            .putBoolean(KEY_SYNC_FORCE_LOCAL, settings.syncForceLocal)
            .apply()
    }

    private companion object {
        const val FILE_NAME = "echotimer_settings"
        const val KEY_PALETTE_ID = "palette_id"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_ANNOUNCEMENTS = "announcements_enabled"
        const val KEY_INTERVAL = "announcement_interval_seconds"
        const val KEY_VOICE_COMMANDS = "voice_commands_enabled"
        const val KEY_SPEAK_START_STOP = "speak_start_stop"
        const val KEY_AUTO_SAVE = "auto_save_sessions"
        const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
        const val KEY_RECOGNITION_LANGUAGE = "recognition_language"
        const val KEY_VOICE_ENGINE = "voice_engine"
        const val KEY_OFFLINE_SENSITIVITY = "offline_sensitivity"
        const val KEY_LEADERBOARD_EDITABLE = "leaderboard_editable"
        const val KEY_SYNC_ENABLED = "sync_enabled"
        const val KEY_SYNC_REPO = "sync_repo"
        const val KEY_SYNC_BRANCH = "sync_branch"
        const val KEY_SYNC_PATH = "sync_path"
        const val KEY_SYNC_TOKEN = "sync_token"
        const val KEY_SYNC_FORCE_LOCAL = "sync_force_local"
    }
}
