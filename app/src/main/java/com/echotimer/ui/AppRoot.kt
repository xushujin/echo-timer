package com.echotimer.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.echotimer.AppContainer
import com.echotimer.R
import com.echotimer.core.settings.AppSettings
import com.echotimer.core.theme.Palettes
import com.echotimer.core.update.UpdatePolicy
import com.echotimer.core.update.UpdateState
import com.echotimer.core.update.releaseInfo
import com.echotimer.ui.components.Glyph
import com.echotimer.ui.components.GlyphIcon
import com.echotimer.ui.components.UpdateDialog
import com.echotimer.ui.components.VoiceTrainingDialog
import com.echotimer.ui.neumorph.NeumorphicSurface
import com.echotimer.ui.screens.AboutScreen
import com.echotimer.ui.screens.HistoryScreen
import com.echotimer.ui.screens.LeaderboardTab
import com.echotimer.ui.screens.LeaderboardTab
import com.echotimer.ui.screens.SettingsScreen
import com.echotimer.ui.screens.TimerScreen
import com.echotimer.ui.theme.EchoTimerTheme
import com.echotimer.ui.theme.LocalEchoColors
import com.echotimer.ui.theme.resolveDarkMode

/** 底部导航的四个页面。 */
enum class AppTab(@StringRes val labelRes: Int, val glyph: Glyph) {
    TIMER(R.string.tab_timer, Glyph.TIMER),
    HISTORY(R.string.tab_history, Glyph.HISTORY),
    LEADERBOARD(R.string.tab_leaderboard, Glyph.TROPHY),
    SETTINGS(R.string.tab_settings, Glyph.SETTINGS),
    ABOUT(R.string.tab_about, Glyph.INFO),
}

/**
 * 应用根节点。主题在此统一应用：切换配色或明暗后，四个页面同时生效。
 */
@Composable
fun EchoTimerRoot(container: AppContainer) {
    val settings by container.settingsRepository.settings.collectAsStateWithLifecycle()
    val palette = Palettes.byId(settings.paletteId)
    val dark = resolveDarkMode(settings.themeMode)
    EchoTimerTheme(palette = palette, dark = dark) {
        RootContent(container = container, settings = settings, dark = dark)
    }
}

@Composable
private fun RootContent(container: AppContainer, settings: AppSettings, dark: Boolean) {
    val colors = LocalEchoColors.current
    var tabName by rememberSaveable { mutableStateOf(AppTab.TIMER.name) }
    val currentTab = AppTab.entries.firstOrNull { it.name == tabName } ?: AppTab.TIMER

    val timerViewModel: TimerViewModel = viewModel(factory = TimerViewModel.Factory)
    val historyViewModel: HistoryViewModel = viewModel(factory = HistoryViewModel.Factory)
    val leaderboardViewModel: LeaderboardViewModel = viewModel(factory = LeaderboardViewModel.Factory)
    val timerState by timerViewModel.uiState.collectAsStateWithLifecycle()
    val sessions by historyViewModel.sessions.collectAsStateWithLifecycle()
    val boards by leaderboardViewModel.boards.collectAsStateWithLifecycle()
    val boardEntries by leaderboardViewModel.entries.collectAsStateWithLifecycle()
    val boardRoute by leaderboardViewModel.route.collectAsStateWithLifecycle()
    val challenge by leaderboardViewModel.challenge.collectAsStateWithLifecycle()
    val syncState by leaderboardViewModel.syncState.collectAsStateWithLifecycle()
    val updateState by container.updateManager.state.collectAsStateWithLifecycle()
    val currentVersionName = container.updateManager.currentVersionName

    val view = LocalView.current
    SideEffect {
        view.keepScreenOn = settings.keepScreenOn && (timerState.isRunning || challenge?.running == true)
        // 系统栏图标明暗跟随应用主题，而不是跟随系统设置，避免深色主题下图标看不清。
        val window = (view.context as? Activity)?.window
        if (window != null) {
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }
    }

    var showVoiceTraining by remember { mutableStateOf(false) }
    // 用户点了「隐藏」：本次不弹，但设置页仍然能看到状态；换版本时重置。
    var updateDialogHidden by remember { mutableStateOf(false) }
    val updateInfo = updateState.releaseInfo
    val updateVersion = updateInfo?.version
    LaunchedEffect(updateVersion) { updateDialogHidden = false }

    // 启动时按"一天一次"的节奏检查更新；不满足条件时完全不联网。
    LaunchedEffect(Unit) { container.updateManager.checkIfDue() }

    BackHandler(enabled = currentTab != AppTab.TIMER) { tabName = AppTab.TIMER.name }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .systemBarsPadding(),
    ) {
        Box(modifier = Modifier.weight(1f)) {
            when (currentTab) {
                AppTab.TIMER -> TimerScreen(
                    state = timerState,
                    settings = settings,
                    onPrimary = timerViewModel::onPrimaryAction,
                    onLap = timerViewModel::onLap,
                    onFinish = timerViewModel::onFinish,
                    onReset = timerViewModel::onReset,
                    onVoiceToggle = timerViewModel::setVoiceCommandsEnabled,
                    onMicPermissionResult = timerViewModel::onMicPermissionResult,
                    onOpenVoiceTraining = {
                        timerViewModel.setVoiceTrainingActive(true)
                        showVoiceTraining = true
                    },
                )

                AppTab.HISTORY -> HistoryScreen(
                    sessions = sessions,
                    onDelete = historyViewModel::delete,
                    onRename = historyViewModel::rename,
                )

                AppTab.LEADERBOARD -> LeaderboardTab(
                    boards = boards,
                    entries = boardEntries,
                    route = boardRoute,
                    challenge = challenge,
                    overtakes = leaderboardViewModel.overtakes,
                    editable = settings.leaderboardEditable,
                    syncState = syncState,
                    onSync = leaderboardViewModel::syncNow,
                    onCreateBoard = leaderboardViewModel::createBoard,
                    onDeleteBoard = leaderboardViewModel::deleteBoard,
                    onOpenBoard = leaderboardViewModel::openBoard,
                    onBack = leaderboardViewModel::backToList,
                    onStartChallenge = leaderboardViewModel::startChallenge,
                    onManualSubmit = leaderboardViewModel::submitManual,
                    onDeleteEntry = leaderboardViewModel::deleteEntry,
                    onTogglePause = leaderboardViewModel::togglePause,
                    onFinishChallenge = leaderboardViewModel::finishChallenge,
                    onDiscardChallenge = leaderboardViewModel::discardChallenge,
                )

                AppTab.SETTINGS -> SettingsScreen(
                    settings = settings,
                    ttsAvailable = timerState.ttsAvailable,
                    onUpdate = { next -> container.settingsRepository.update { next } },
                    onTestSpeak = timerViewModel::testSpeak,
                    onMicPermissionResult = timerViewModel::onMicPermissionResult,
                    offlineTemplates = timerState.offlineTemplates,
                    syncState = syncState,
                    onSync = leaderboardViewModel::syncNow,
                    updateState = updateState,
                    currentVersion = currentVersionName,
                    onCheckUpdate = { container.updateManager.check(auto = false) },
                    onOpenReleases = container.updateManager::openReleasesPage,
                    onOpenVoiceTraining = {
                        timerViewModel.setVoiceTrainingActive(true)
                        showVoiceTraining = true
                    },
                )

                AppTab.ABOUT -> AboutScreen()
            }
        }
        BottomBar(current = currentTab, onSelect = { tabName = it.name })
    }

    val promptUpdate = when (val current = updateState) {
        // 自动检查时，用户点过「稍后」的版本不再弹；手动检查总是弹。
        is UpdateState.Available -> UpdatePolicy.shouldPrompt(
            version = current.info.version,
            skippedVersion = settings.skippedUpdateVersion,
            auto = current.auto,
        )

        is UpdateState.Failed -> current.info != null
        is UpdateState.NeedsPermission,
        is UpdateState.Downloading,
        is UpdateState.Installing,
        -> true

        UpdateState.Idle, is UpdateState.Checking, is UpdateState.UpToDate -> false
    }

    if (promptUpdate && updateInfo != null && !updateDialogHidden) {
        UpdateDialog(
            state = updateState,
            currentVersion = currentVersionName,
            onDownload = container.updateManager::download,
            onLater = { container.updateManager.dismiss(updateInfo) },
            onHide = { updateDialogHidden = true },
            onOpenPage = container.updateManager::openReleasesPage,
            onRetry = container.updateManager::retry,
        )
    }

    if (showVoiceTraining) {
        VoiceTrainingDialog(
            store = container.voiceTemplates,
            sensitivity = settings.offlineSensitivity,
            onSensitivityChange = { value ->
                container.settingsRepository.update { it.copy(offlineSensitivity = value) }
            },
            onDismiss = {
                showVoiceTraining = false
                timerViewModel.setVoiceTrainingActive(false)
            },
        )
    }
}

@Composable
private fun BottomBar(current: AppTab, onSelect: (AppTab) -> Unit) {
    val colors = LocalEchoColors.current
    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        NeumorphicSurface(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 24.dp,
            depth = 12.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                AppTab.entries.forEach { item ->
                    val selected = item == current
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(18.dp))
                            .clickable { onSelect(item) }
                            .padding(vertical = 7.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        GlyphIcon(
                            glyph = item.glyph,
                            size = 20.dp,
                            tint = if (selected) colors.accent else colors.textSecondary,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(item.labelRes),
                            color = if (selected) colors.accent else colors.textSecondary,
                            fontSize = 10.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
            }
        }
    }
}
