package com.echotimer

import android.content.Context
import android.os.SystemClock
import com.echotimer.core.settings.SettingsRepository
import com.echotimer.core.time.MonotonicClock
import com.echotimer.core.voice.Announcer
import com.echotimer.core.voice.TtsAnnouncer
import com.echotimer.core.voice.VoiceSourceFactory
import com.echotimer.core.sync.LeaderboardSyncManager
import com.echotimer.core.update.UpdateManager
import com.echotimer.core.voice.offline.OfflineVoiceStore
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

    /** 榜单同步：本地 ↔ GitHub 仓库里的 NDJSON 文件。 */
    val syncManager: LeaderboardSyncManager by lazy {
        LeaderboardSyncManager(settingsRepository, leaderboardRepository)
    }

    /** 应用内更新：检查 GitHub 发布页 → 下载 → 校验 → 调起系统安装器。 */
    val updateManager: UpdateManager by lazy {
        UpdateManager(appContext, settingsRepository)
    }

    /** 单调时钟：SystemClock.elapsedRealtime 不受系统时间调整影响。 */
    val clock: MonotonicClock = MonotonicClock { SystemClock.elapsedRealtime() }

    /**
     * 播报引擎全应用共用一个：计时页与天梯挑战都会用它。
     * 两个 TTS 实例会互相抢音频焦点，所以放在容器里做进程级单例，由 ViewModel 只读不释放。
     */
    val announcer: Announcer by lazy { TtsAnnouncer(appContext) }

    /** 离线口令模板（只存特征，不存音频）。 */
    val voiceTemplates: OfflineVoiceStore by lazy { OfflineVoiceStore(appContext) }

    /** 语音指令来源工厂：按设置与设备能力在"系统识别 / 离线口令"之间选择。 */
    val voiceSourceFactory: VoiceSourceFactory by lazy {
        VoiceSourceFactory(appContext, voiceTemplates, settingsRepository)
    }
}
