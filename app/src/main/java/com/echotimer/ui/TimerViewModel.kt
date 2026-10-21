package com.echotimer.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.echotimer.EchoTimerApplication
import com.echotimer.core.settings.AppSettings
import com.echotimer.core.settings.SettingsRepository
import com.echotimer.core.time.AnnouncementScheduler
import com.echotimer.core.time.ElapsedFormatter
import com.echotimer.core.time.LapSplit
import com.echotimer.core.time.MonotonicClock
import com.echotimer.core.time.StopwatchEngine
import com.echotimer.core.time.StopwatchState
import com.echotimer.core.voice.Announcer
import com.echotimer.core.voice.SpeechCommandController
import com.echotimer.core.voice.VoiceCommand
import com.echotimer.core.voice.VoiceStatus
import com.echotimer.data.HistoryRepository
import com.echotimer.data.LapRecord
import com.echotimer.data.SessionDraft
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 计时页面的完整状态快照。 */
data class TimerUiState(
    val stopwatchState: StopwatchState = StopwatchState.IDLE,
    val elapsedMs: Long = 0L,
    val laps: List<LapSplit> = emptyList(),
    /** 刚结束并写入历史的总时长；用于"已保存"提示，开始新一轮时清空。 */
    val lastSavedTotalMs: Long? = null,
    val micPermissionGranted: Boolean = false,
    val voiceStatus: VoiceStatus = VoiceStatus.OFF,
    val rmsLevel: Float = 0f,
    val ttsAvailable: Boolean = true,
) {
    val isRunning: Boolean get() = stopwatchState == StopwatchState.RUNNING
    val isPaused: Boolean get() = stopwatchState == StopwatchState.PAUSED
    val hasResult: Boolean get() = stopwatchState == StopwatchState.FINISHED
}

/**
 * 计时页的编排层：把纯逻辑（[StopwatchEngine] / [AnnouncementScheduler]）、
 * 语音能力（[Announcer] / [SpeechCommandController]）和持久化（[HistoryRepository]）串起来。
 *
 * 这里不写任何"计时算法"，只做状态同步与副作用调度，因此核心逻辑可以脱离 Android 单测。
 */
class TimerViewModel(
    private val settingsRepository: SettingsRepository,
    private val historyRepository: HistoryRepository,
    clock: MonotonicClock,
    private val announcer: Announcer,
    speechFactory: (languageTag: String, onCommand: (VoiceCommand) -> Unit) -> SpeechCommandController,
) : ViewModel() {

    private val engine = StopwatchEngine(clock)
    private val scheduler = AnnouncementScheduler()

    private val _uiState = MutableStateFlow(TimerUiState())
    val uiState: StateFlow<TimerUiState> = _uiState.asStateFlow()

    private val speech: SpeechCommandController = speechFactory(currentSettings().recognitionLanguage) {
        handleVoiceCommand(it)
    }

    private var ticker: Job? = null
    private var sessionStartedAtMs: Long = 0L

    init {
        scheduler.setInterval(currentSettings().announcementIntervalSeconds)
        observeSettings()
        observeAnnouncer()
        observeSpeech()
        viewModelScope.launch { historyRepository.refresh() }
        _uiState.update { it.copy(ttsAvailable = announcer.available.value) }
    }

    // ---------------------------------------------------------------------
    // 用户操作
    // ---------------------------------------------------------------------

    /** 主按钮：开始 / 暂停 / 继续。 */
    fun onPrimaryAction() {
        when (engine.state) {
            StopwatchState.IDLE, StopwatchState.FINISHED -> startNewSession()
            StopwatchState.RUNNING -> pause()
            StopwatchState.PAUSED -> resume()
        }
    }

    /** 计次；仅在运行或暂停时有效。 */
    fun onLap() {
        val lap = engine.addLap() ?: return
        publishState()
        if (currentSettings().speakStartAndStop) {
            announcer.speak("第 ${lap.index} 次，${ElapsedFormatter.spoken(lap.totalMs)}")
        }
    }

    /** 结束并保存。 */
    fun onFinish() {
        if (engine.state != StopwatchState.RUNNING && engine.state != StopwatchState.PAUSED) return
        val total = engine.finish()
        stopTicker()
        publishState()
        val settings = currentSettings()
        if (settings.speakStartAndStop) {
            announcer.speak("计时结束，总时长 ${ElapsedFormatter.spoken(total)}")
        }
        if (settings.autoSaveSessions && total > 0L) {
            saveCurrentSession(total)
        }
    }

    /** 清零，丢弃本次计时与计次。 */
    fun onReset() {
        engine.reset()
        scheduler.reset()
        stopTicker()
        _uiState.update { it.copy(lastSavedTotalMs = null) }
        publishState()
    }

    /** 设置页的"测试播报"。 */
    fun testSpeak() {
        announcer.speak("语音播报测试，${ElapsedFormatter.spoken(90_000L)}")
    }

    /** 麦克风权限结果由界面回传。 */
    fun onMicPermissionResult(granted: Boolean) {
        _uiState.update { it.copy(micPermissionGranted = granted) }
        syncSpeechState()
    }

    /** 界面上直接开关语音指令（与设置页写的是同一份设置）。 */
    fun setVoiceCommandsEnabled(enabled: Boolean) {
        settingsRepository.update { it.copy(voiceCommandsEnabled = enabled) }
    }

    // ---------------------------------------------------------------------
    // 内部实现
    // ---------------------------------------------------------------------

    private fun startNewSession() {
        engine.start()
        scheduler.setInterval(currentSettings().announcementIntervalSeconds)
        scheduler.reset()
        sessionStartedAtMs = System.currentTimeMillis()
        _uiState.update { it.copy(lastSavedTotalMs = null) }
        publishState()
        startTicker()
        if (currentSettings().speakStartAndStop) {
            announcer.speak("开始计时")
        }
    }

    private fun pause() {
        engine.pause()
        stopTicker()
        publishState()
        if (currentSettings().speakStartAndStop) {
            announcer.speak("已暂停")
        }
    }

    private fun resume() {
        engine.start()
        publishState()
        startTicker()
        if (currentSettings().speakStartAndStop) {
            announcer.speak("继续计时")
        }
    }

    private fun handleVoiceCommand(command: VoiceCommand) {
        when (command) {
            VoiceCommand.START -> when (engine.state) {
                StopwatchState.IDLE, StopwatchState.FINISHED -> startNewSession()
                StopwatchState.PAUSED -> resume()
                StopwatchState.RUNNING -> Unit
            }

            VoiceCommand.PAUSE -> pause()
            VoiceCommand.RESUME -> if (engine.state == StopwatchState.PAUSED) resume()
            VoiceCommand.STOP -> onFinish()
            VoiceCommand.LAP -> onLap()
        }
    }

    private fun startTicker() {
        if (ticker?.isActive == true) return
        ticker = viewModelScope.launch {
            while (true) {
                val elapsed = engine.elapsedMs()
                publishElapsed(elapsed)
                announceIfDue(elapsed)
                delay(TICK_INTERVAL_MS)
            }
        }
    }

    private fun stopTicker() {
        ticker?.cancel()
        ticker = null
    }

    private fun publishElapsed(elapsedMs: Long) {
        _uiState.update { it.copy(elapsedMs = elapsedMs) }
    }

    private fun publishState() {
        _uiState.update {
            it.copy(
                stopwatchState = engine.state,
                elapsedMs = engine.elapsedMs(),
                laps = engine.laps(),
            )
        }
    }

    private fun announceIfDue(elapsedMs: Long) {
        val settings = currentSettings()
        // 即使关闭播报也推进调度器，避免重新打开时把过去的时间点一次性补播。
        val due = scheduler.advanceTo(elapsedMs) ?: return
        if (!settings.announcementsEnabled) return
        // 报时只读数字，不加"秒"（见 README「播报规则」）。
        announcer.speak(ElapsedFormatter.spokenSeconds(due.atMs))
    }

    private fun saveCurrentSession(totalMs: Long) {
        val laps = engine.laps().map { LapRecord(it.index, it.lapMs, it.totalMs) }
        val draft = SessionDraft(
            startedAt = sessionStartedAtMs,
            endedAt = System.currentTimeMillis(),
            totalMs = totalMs,
            laps = laps,
        )
        viewModelScope.launch {
            historyRepository.save(draft)
            _uiState.update { it.copy(lastSavedTotalMs = totalMs) }
        }
    }

    private fun observeSettings() {
        viewModelScope.launch {
            settingsRepository.settings
                .collect { settings ->
                    scheduler.setInterval(settings.announcementIntervalSeconds)
                    speech.languageTag = settings.recognitionLanguage
                    syncSpeechState()
                }
        }
    }

    private fun observeAnnouncer() {
        viewModelScope.launch {
            announcer.speaking.collect { speaking -> speech.setSuspended(speaking) }
        }
        viewModelScope.launch {
            announcer.available.collect { available ->
                _uiState.update { it.copy(ttsAvailable = available) }
            }
        }
    }

    private fun observeSpeech() {
        viewModelScope.launch {
            speech.status.collect { status -> _uiState.update { it.copy(voiceStatus = status) } }
        }
        viewModelScope.launch {
            speech.rmsLevel.collect { level -> _uiState.update { it.copy(rmsLevel = level) } }
        }
    }

    private fun syncSpeechState() {
        val settings = currentSettings()
        val granted = _uiState.value.micPermissionGranted
        if (settings.voiceCommandsEnabled && granted) {
            speech.start()
        } else {
            speech.stop()
            if (settings.voiceCommandsEnabled && !granted) {
                _uiState.update { it.copy(voiceStatus = VoiceStatus.PERMISSION_DENIED) }
            }
        }
    }

    private fun currentSettings(): AppSettings = settingsRepository.settings.value

    override fun onCleared() {
        super.onCleared()
        stopTicker()
        speech.release()
        // announcer 是应用级单例（另一个计时页也在用），这里不释放。
    }

    companion object {
        private const val TICK_INTERVAL_MS = 33L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as EchoTimerApplication
                val container = app.container
                TimerViewModel(
                    settingsRepository = container.settingsRepository,
                    historyRepository = container.historyRepository,
                    clock = container.clock,
                    announcer = container.announcer,
                    speechFactory = container::createSpeechController,
                )
            }
        }
    }
}
