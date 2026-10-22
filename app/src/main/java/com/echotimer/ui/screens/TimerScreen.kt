package com.echotimer.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.echotimer.R
import com.echotimer.core.settings.AppSettings
import com.echotimer.core.time.ElapsedFormatter
import com.echotimer.core.time.IntervalMath
import com.echotimer.core.time.LapSplit
import com.echotimer.core.time.StopwatchState
import com.echotimer.core.voice.VoiceStatus
import com.echotimer.core.voice.offline.VoiceBackend
import com.echotimer.ui.TimerUiState
import com.echotimer.ui.components.Glyph
import com.echotimer.ui.components.GlyphIcon
import com.echotimer.ui.components.NeumorphicCard
import com.echotimer.ui.components.NeumorphicIconButton
import com.echotimer.ui.components.StatusDot
import com.echotimer.ui.components.VoiceSetupDialog
import com.echotimer.ui.components.openVoiceInputSettings
import com.echotimer.ui.components.rememberMicPermission
import com.echotimer.ui.components.rememberSpeechAvailability
import com.echotimer.ui.neumorph.NeumorphicSurface
import com.echotimer.ui.theme.LocalEchoColors

/** 低于该高度（如横屏、分屏）改用可滚动布局，避免固定元素挤压表盘。 */
private val CompactHeightThreshold = 560.dp

/**
 * 计时页。只负责展示与事件上抛，所有状态由 [com.echotimer.ui.TimerViewModel] 提供。
 */
@Composable
fun TimerScreen(
    state: TimerUiState,
    settings: AppSettings,
    onPrimary: () -> Unit,
    onLap: () -> Unit,
    onFinish: () -> Unit,
    onReset: () -> Unit,
    onVoiceToggle: (Boolean) -> Unit,
    onMicPermissionResult: (Boolean) -> Unit,
    onOpenVoiceTraining: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val speech = rememberSpeechAvailability()
    // 系统识别可用、或者已经录过离线口令，都算"能用语音"。
    val canUseVoice = state.voiceBackend != VoiceBackend.NONE
    val mic = rememberMicPermission { granted ->
        onMicPermissionResult(granted)
        if (granted && !settings.voiceCommandsEnabled && canUseVoice) {
            onVoiceToggle(true)
        }
    }
    LaunchedEffect(mic.granted) { onMicPermissionResult(mic.granted) }

    var showSpeechHelp by remember { mutableStateOf(false) }

    val onVoiceBarClick: () -> Unit = {
        speech.refresh()
        when {
            settings.voiceCommandsEnabled -> onVoiceToggle(false)
            // 两个引擎都不可用时，弹窗里给出"录离线口令"的入口，而不是只说一句不支持。
            !canUseVoice -> showSpeechHelp = true
            mic.granted -> onVoiceToggle(true)
            else -> mic.request()
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val compact = maxHeight < CompactHeightThreshold
        if (compact) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
            ) {
                Header()
                Spacer(Modifier.height(8.dp))
                TimerDial(state = state, settings = settings, dialSize = 200.dp)
                Spacer(Modifier.height(12.dp))
                ResultHint(state)
                ActionRow(
                    state = state,
                    onPrimary = onPrimary,
                    onLap = onLap,
                    onFinish = onFinish,
                    compact = true,
                )
                ResetAction(state = state, onReset = onReset)
                Spacer(Modifier.height(12.dp))
                VoiceBar(
                    settings = settings,
                    status = state.voiceStatus,
                    rmsLevel = state.rmsLevel,
                    backend = state.voiceBackend,
                    onClick = onVoiceBarClick,
                )
                Spacer(Modifier.height(12.dp))
                LapStrip(laps = state.laps)
                Spacer(Modifier.height(16.dp))
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
            ) {
                Header()
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    // 表盘按可用空间收缩，避免小屏时压到按钮。
                    val dialSize = minOf(maxWidth, maxHeight).coerceIn(180.dp, 304.dp)
                    TimerDial(state = state, settings = settings, dialSize = dialSize)
                }
                ResultHint(state)
                ActionRow(
                    state = state,
                    onPrimary = onPrimary,
                    onLap = onLap,
                    onFinish = onFinish,
                    compact = false,
                )
                ResetAction(state = state, onReset = onReset)
                Spacer(Modifier.height(14.dp))
                VoiceBar(
                    settings = settings,
                    status = state.voiceStatus,
                    rmsLevel = state.rmsLevel,
                    backend = state.voiceBackend,
                    onClick = onVoiceBarClick,
                )
                Spacer(Modifier.height(12.dp))
                LapStrip(laps = state.laps)
                Spacer(Modifier.height(12.dp))
            }
        }
    }

    if (showSpeechHelp) {
        VoiceSetupDialog(
            availability = speech.value,
            offlineTemplates = state.offlineTemplates,
            onDismiss = { showSpeechHelp = false },
            onOpenTraining = {
                showSpeechHelp = false
                onOpenVoiceTraining()
            },
            onOpenSettings = {
                showSpeechHelp = false
                // 没有任何识别服务时，ACTION_VOICE_INPUT_SETTINGS 在国内 ROM 上会落到
                // 「默认数字助理应用」页面（与语音识别无关），所以直接开系统设置。
                openVoiceInputSettings(
                    context = context,
                    preferVoiceInput = speech.value.servicePackages.isNotEmpty(),
                )
            },
        )
    }
}

@Composable
private fun Header() {
    val colors = LocalEchoColors.current
    Column {
        Spacer(Modifier.height(14.dp))
        Text(
            text = stringResource(R.string.app_name),
            color = colors.textPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 2.sp,
        )
        Text(
            text = stringResource(R.string.app_tagline),
            color = colors.textSecondary,
            fontSize = 12.sp,
            letterSpacing = 1.sp,
        )
    }
}

@Composable
private fun ResultHint(state: TimerUiState) {
    val colors = LocalEchoColors.current
    val saved = state.lastSavedTotalMs
    if (saved != null) {
        Text(
            text = stringResource(R.string.timer_saved_hint, ElapsedFormatter.clock(saved)),
            modifier = Modifier.fillMaxWidth(),
            color = colors.textSecondary,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ActionRow(
    state: TimerUiState,
    onPrimary: () -> Unit,
    onLap: () -> Unit,
    onFinish: () -> Unit,
    compact: Boolean,
) {
    val canLapOrFinish = state.isRunning || state.isPaused
    val primaryGlyph = if (state.isRunning) Glyph.PAUSE else Glyph.PLAY
    val primaryLabel = stringResource(
        when {
            state.isRunning -> R.string.timer_action_pause
            state.isPaused -> R.string.timer_action_resume
            else -> R.string.timer_action_start
        },
    )
    val primarySize = if (compact) 72.dp else 88.dp
    val secondarySize = if (compact) 52.dp else 60.dp

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(
            if (compact) 16.dp else 22.dp,
            Alignment.CenterHorizontally,
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NeumorphicIconButton(
            glyph = Glyph.FLAG,
            contentDescription = stringResource(R.string.timer_action_lap),
            onClick = onLap,
            size = secondarySize,
            enabled = canLapOrFinish,
        )
        NeumorphicIconButton(
            glyph = primaryGlyph,
            contentDescription = primaryLabel,
            onClick = onPrimary,
            size = primarySize,
            depth = 18.dp,
            accent = true,
        )
        NeumorphicIconButton(
            glyph = Glyph.STOP,
            contentDescription = stringResource(R.string.timer_action_finish),
            onClick = onFinish,
            size = secondarySize,
            enabled = canLapOrFinish,
        )
    }
}

@Composable
private fun ResetAction(state: TimerUiState, onReset: () -> Unit) {
    val colors = LocalEchoColors.current
    if (state.elapsedMs > 0L && !state.isRunning) {
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.timer_action_reset),
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onReset)
                .padding(vertical = 6.dp),
            color = colors.textSecondary,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun TimerDial(state: TimerUiState, settings: AppSettings, dialSize: Dp) {
    val colors = LocalEchoColors.current
    val elapsed = state.elapsedMs
    val clockText = ElapsedFormatter.clock(elapsed)
    val running = state.isRunning
    val intervalSeconds = settings.announcementIntervalSeconds
    val nextMark = if (settings.announcementsEnabled) {
        IntervalMath.nextMark(elapsed, intervalSeconds)
    } else {
        null
    }
    val progress = if (nextMark != null && (running || state.isPaused)) {
        IntervalMath.progressToNextMark(elapsed, intervalSeconds)
    } else {
        0f
    }
    val stateLabel = stringResource(
        when (state.stopwatchState) {
            StopwatchState.IDLE -> R.string.timer_state_idle
            StopwatchState.RUNNING -> R.string.timer_state_running
            StopwatchState.PAUSED -> R.string.timer_state_paused
            StopwatchState.FINISHED -> R.string.timer_state_finished
        },
    )
    // 字号跟随表盘尺寸，小屏（180dp）时也不会把时间挤出圆形区域。
    val baseFontSize = when {
        dialSize < 220.dp -> 30.sp
        dialSize < 260.dp -> 38.sp
        else -> 46.sp
    }
    val clockFontSize = if (clockText.length > 9) baseFontSize * 0.78f else baseFontSize
    val subLabel = when {
        running || state.isPaused -> nextMark?.let {
            stringResource(R.string.timer_next_mark, ElapsedFormatter.spoken(it.atMs))
        } ?: stringResource(R.string.timer_announce_off)

        state.laps.isNotEmpty() -> stringResource(R.string.timer_laps_count, state.laps.size)
        else -> stringResource(R.string.timer_idle_hint)
    }

    Box(modifier = Modifier.size(dialSize), contentAlignment = Alignment.Center) {
        NeumorphicSurface(
            modifier = Modifier.fillMaxSize(),
            circle = true,
            depth = 20.dp,
            pressed = running,
        ) {}
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 6.dp.toPx()
            val inset = 30.dp.toPx()
            val arcSize = Size(size.width - inset * 2f, size.height - inset * 2f)
            drawArc(
                color = colors.surfaceAlt,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
            if (progress > 0f) {
                drawArc(
                    color = colors.accent,
                    startAngle = -90f,
                    sweepAngle = 360f * progress,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stateLabel,
                color = colors.textSecondary,
                fontSize = 12.sp,
                letterSpacing = 3.sp,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = clockText,
                color = colors.textPrimary,
                fontSize = clockFontSize,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.5.sp,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = subLabel,
                color = colors.textSecondary,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun VoiceBar(
    settings: AppSettings,
    status: VoiceStatus,
    rmsLevel: Float,
    backend: VoiceBackend,
    onClick: () -> Unit,
) {
    val colors = LocalEchoColors.current
    val listening = settings.voiceCommandsEnabled &&
        (status == VoiceStatus.LISTENING || status == VoiceStatus.SUSPENDED)
    val label = when {
        !settings.voiceCommandsEnabled -> stringResource(R.string.voice_off)
        status == VoiceStatus.PERMISSION_DENIED -> stringResource(R.string.voice_no_permission)
        status == VoiceStatus.UNAVAILABLE -> stringResource(R.string.voice_unavailable)
        status == VoiceStatus.ERROR -> stringResource(R.string.voice_error)
        status == VoiceStatus.SUSPENDED -> stringResource(R.string.voice_suspended)
        // 开关是开的但没在监听（例如训练弹窗占用麦克风期间），不能说"正在听"。
        status == VoiceStatus.OFF -> stringResource(R.string.voice_paused)
        backend == VoiceBackend.OFFLINE -> stringResource(R.string.voice_listening_offline)
        else -> stringResource(R.string.voice_listening)
    }
    val dotColor = when {
        !settings.voiceCommandsEnabled -> colors.textSecondary
        status == VoiceStatus.LISTENING -> colors.accent
        status == VoiceStatus.ERROR || status == VoiceStatus.PERMISSION_DENIED -> colors.danger
        else -> colors.textSecondary
    }
    // 麦克风电平驱动呼吸感，让"正在听"有真实反馈。
    val pulseAlpha = if (listening) {
        0.55f + (rmsLevel.coerceIn(0f, 12f) / 12f) * 0.45f
    } else {
        0.45f
    }

    NeumorphicCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        cornerRadius = 20.dp,
        depth = 12.dp,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(
                color = dotColor,
                modifier = Modifier.graphicsLayer { alpha = pulseAlpha },
            )
            Spacer(Modifier.size(10.dp))
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                color = if (settings.voiceCommandsEnabled) colors.textPrimary else colors.textSecondary,
                fontSize = 13.sp,
            )
            GlyphIcon(
                glyph = Glyph.MIC,
                size = 18.dp,
                tint = if (settings.voiceCommandsEnabled) colors.accent else colors.textSecondary,
            )
        }
    }
}

@Composable
private fun LapStrip(laps: List<LapSplit>) {
    val colors = LocalEchoColors.current
    if (laps.isEmpty()) {
        Text(
            text = stringResource(R.string.timer_lap_empty),
            modifier = Modifier.fillMaxWidth(),
            color = colors.textSecondary,
            fontSize = 12.sp,
        )
        return
    }
    // 最新一次排在最左（时间降序），与历史记录里的计次明细保持一致。
    val listState = rememberLazyListState()
    // 新增计次后回到最左，保证「最后一次」始终可见（否则视口会锚在原来的第一项上）。
    LaunchedEffect(laps.size) { listState.animateScrollToItem(0) }
    LazyRow(state = listState, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(items = laps.asReversed(), key = { it.index }) { lap ->
            LapChip(lap)
        }
    }
}

@Composable
private fun LapChip(lap: LapSplit) {
    val colors = LocalEchoColors.current
    NeumorphicCard(
        cornerRadius = 16.dp,
        depth = 8.dp,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            text = stringResource(R.string.timer_lap_item, lap.index),
            color = colors.textSecondary,
            fontSize = 11.sp,
        )
        // 计次记录的是"按下时的累计时长"，不是与上一次的差值
        Text(
            text = ElapsedFormatter.clock(lap.totalMs),
            color = colors.textPrimary,
            fontSize = 14.sp,
            fontFamily = FontFamily.Monospace,
        )
    }
}
