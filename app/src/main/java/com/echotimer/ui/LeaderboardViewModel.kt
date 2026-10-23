package com.echotimer.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.echotimer.EchoTimerApplication
import com.echotimer.core.leaderboard.CreateBoardResult
import com.echotimer.core.leaderboard.Leaderboard
import com.echotimer.core.leaderboard.LeaderboardEntry
import com.echotimer.core.leaderboard.LeaderboardRules
import com.echotimer.core.leaderboard.SubmitResult
import com.echotimer.core.settings.SettingsRepository
import com.echotimer.core.sync.LeaderboardSyncManager
import com.echotimer.core.sync.SyncResult
import com.echotimer.core.time.AnnouncementScheduler
import com.echotimer.core.time.ElapsedFormatter
import com.echotimer.core.time.MonotonicClock
import com.echotimer.core.time.StopwatchEngine
import com.echotimer.core.voice.Announcer
import com.echotimer.data.LeaderboardRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 天梯 tab 内部的页面。 */
sealed interface LeaderboardRoute {
    data object List : LeaderboardRoute
    data class Board(val boardId: Long) : LeaderboardRoute
}

/** 挑战计时状态；非 null 表示正在挑战页。 */
data class ChallengeState(
    val boardId: Long,
    val boardName: String,
    val player: String,
    val running: Boolean,
    val elapsedMs: Long,
    /** 结束后保留的最终成绩。 */
    val finishedMs: Long? = null,
    val submitting: Boolean = false,
    val result: SubmitResult? = null,
) {
    val finished: Boolean get() = finishedMs != null
}

/**
 * 天梯榜编排层。
 *
 * 挑战用的是独立的 [StopwatchEngine]（与计时页互不干扰），且不提供计次功能；
 * 结束后调用仓库提交成绩，由仓库按"只有更大才更新"的规则落库。
 */
/** 同步面板的状态：是否在同步 + 上一次的结果（界面自己翻译成文案）。 */
data class SyncUiState(
    val running: Boolean = false,
    val result: SyncResult? = null,
)

class LeaderboardViewModel(
    private val repository: LeaderboardRepository,
    private val settingsRepository: SettingsRepository,
    private val announcer: Announcer,
    private val syncManager: LeaderboardSyncManager,
    clock: MonotonicClock,
) : ViewModel() {

    val boards: StateFlow<List<Leaderboard>> = repository.boards
    val entries: StateFlow<List<LeaderboardEntry>> = repository.entries

    private val _route = MutableStateFlow<LeaderboardRoute>(LeaderboardRoute.List)
    val route: StateFlow<LeaderboardRoute> = _route.asStateFlow()

    private val _challenge = MutableStateFlow<ChallengeState?>(null)
    val challenge: StateFlow<ChallengeState?> = _challenge.asStateFlow()

    private val _syncState = MutableStateFlow(SyncUiState())
    val syncState: StateFlow<SyncUiState> = _syncState.asStateFlow()

    private val engine = StopwatchEngine(clock)
    private val scheduler = AnnouncementScheduler()
    private var ticker: Job? = null

    init {
        viewModelScope.launch { repository.refresh() }
        // 与计时页共用同一份播报设置：挑战也会按设定间隔报时。
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                scheduler.setInterval(settings.announcementIntervalSeconds)
            }
        }
    }

    fun openBoard(boardId: Long) {
        _route.value = LeaderboardRoute.Board(boardId)
        viewModelScope.launch { repository.selectBoard(boardId) }
    }

    fun backToList() {
        if (_challenge.value != null) {
            discardChallenge()
            return
        }
        _route.value = LeaderboardRoute.List
    }

    suspend fun createBoard(rawName: String): CreateBoardResult = repository.createBoard(rawName)

    /** 立即同步：读远端 → 双向合并 → 写远端 → 写回本地。 */
    fun syncNow() {
        if (_syncState.value.running) return
        viewModelScope.launch {
            _syncState.value = SyncUiState(running = true, result = _syncState.value.result)
            val result = syncManager.sync()
            _syncState.value = SyncUiState(running = false, result = result)
        }
    }

    fun deleteEntry(entryId: Long) {
        viewModelScope.launch { repository.deleteEntry(entryId) }
    }

    fun deleteBoard(boardId: Long) {
        viewModelScope.launch {
            repository.deleteBoard(boardId)
            if ((_route.value as? LeaderboardRoute.Board)?.boardId == boardId) {
                _route.value = LeaderboardRoute.List
            }
        }
    }

    fun startChallenge(board: Leaderboard, rawPlayer: String) {
        val player = LeaderboardRules.normalizeName(rawPlayer)
        if (LeaderboardRules.validateName(player) != null) return

        val settings = settingsRepository.settings.value
        scheduler.setInterval(settings.announcementIntervalSeconds)
        scheduler.reset()

        engine.start()
        _challenge.value = ChallengeState(
            boardId = board.id,
            boardName = board.name,
            player = player,
            running = true,
            elapsedMs = 0L,
        )
        startTicker()
        if (settings.speakStartAndStop) {
            announcer.speak("开始挑战")
        }
    }

    /** 手工录入成绩：走与挑战相同的"只有更大才更新"规则。 */
    suspend fun submitManual(boardId: Long, player: String, elapsedMs: Long): SubmitResult =
        repository.submit(boardId, player, elapsedMs)

    fun togglePause() {
        val state = _challenge.value ?: return
        if (state.finished) return
        if (state.running) {
            engine.pause()
            stopTicker()
        } else {
            engine.start()
            startTicker()
        }
        _challenge.value = state.copy(running = !state.running, elapsedMs = engine.elapsedMs())
        if (settingsRepository.settings.value.speakStartAndStop) {
            announcer.speak(if (state.running) "已暂停" else "继续挑战")
        }
    }

    /** 结束并提交成绩。 */
    fun finishChallenge() {
        val state = _challenge.value ?: return
        if (state.finished) return

        val total = engine.finish()
        stopTicker()
        if (settingsRepository.settings.value.speakStartAndStop) {
            announcer.speak("挑战结束，用时 ${ElapsedFormatter.spoken(total)}")
        }
        _challenge.value = state.copy(
            running = false,
            elapsedMs = total,
            finishedMs = total,
            submitting = true,
        )
        viewModelScope.launch {
            val result = repository.submit(state.boardId, state.player, total)
            _challenge.value = _challenge.value?.copy(submitting = false, result = result)
        }
    }

    fun discardChallenge() {
        stopTicker()
        engine.reset()
        announcer.stop()
        _challenge.value = null
    }

    /** 到点就报，只读数字（与计时页同一套规则）。 */
    private fun announceIfDue(elapsedMs: Long) {
        val settings = settingsRepository.settings.value
        val due = scheduler.advanceTo(elapsedMs) ?: return
        if (!settings.announcementsEnabled) return
        announcer.speak(ElapsedFormatter.spokenSeconds(due.atMs))
    }

    private fun startTicker() {
        if (ticker?.isActive == true) return
        ticker = viewModelScope.launch {
            while (true) {
                val elapsed = engine.elapsedMs()
                _challenge.value = _challenge.value?.copy(elapsedMs = elapsed)
                announceIfDue(elapsed)
                delay(TICK_INTERVAL_MS)
            }
        }
    }

    private fun stopTicker() {
        ticker?.cancel()
        ticker = null
    }

    override fun onCleared() {
        super.onCleared()
        stopTicker()
    }

    companion object {
        private const val TICK_INTERVAL_MS = 33L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as EchoTimerApplication
                LeaderboardViewModel(
                    repository = app.container.leaderboardRepository,
                    settingsRepository = app.container.settingsRepository,
                    announcer = app.container.announcer,
                    syncManager = app.container.syncManager,
                    clock = app.container.clock,
                )
            }
        }
    }
}
