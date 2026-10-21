package com.echotimer

import android.content.Context
import android.os.SystemClock
import com.echotimer.core.settings.SettingsRepository
import com.echotimer.core.time.MonotonicClock
import com.echotimer.core.voice.Announcer
import com.echotimer.core.voice.SpeechCommandController
import com.echotimer.core.voice.TtsAnnouncer
import com.echotimer.core.voice.VoiceCommand
import com.echotimer.data.HistoryRepository
import com.echotimer.data.LeaderboardRepository
import com.echotimer.data.db.EchoDb

/**
 * 手写依赖容器（不使用 DI 框架）。
 *
 * 生命周期与应用进程一致的对象（设置、数据库、时钟）放在这里；
 * 与界面生命周期绑定的对象（TTS、语音识别）由 ViewModel 创建并在 onCleared 释放。
 */
class AppContainer(context: Context) {

    private val appContext: Context = context.applicationContext

    val settingsRepository: SettingsRepository = SettingsRepository(appContext)

    /** 全应用共用一个 SQLiteOpenHelper，避免多个实例同时操作同一文件。 */
    private val database: EchoDb = EchoDb(appContext)

    val historyRepository: HistoryRepository = HistoryRepository(database)

    val leaderboardRepository: LeaderboardRepository = LeaderboardRepository(database)

    /** 单调时钟：SystemClock.elapsedRealtime 不受系统时间调整影响。 */
    val clock: MonotonicClock = MonotonicClock { SystemClock.elapsedRealtime() }

    /**
     * 播报引擎全应用共用一个：计时页与天梯挑战都会用它。
     * 两个 TTS 实例会互相抢音频焦点，所以放在容器里做进程级单例，由 ViewModel 只读不释放。
     */
    val announcer: Announcer by lazy { TtsAnnouncer(appContext) }

    fun createSpeechController(
        languageTag: String,
        onCommand: (VoiceCommand) -> Unit,
    ): SpeechCommandController = SpeechCommandController(appContext, languageTag, onCommand)
}
