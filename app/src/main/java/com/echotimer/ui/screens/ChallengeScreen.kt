package com.echotimer.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.echotimer.R
import com.echotimer.core.time.ElapsedFormatter
import com.echotimer.ui.ChallengeState
import com.echotimer.ui.OvertakeNotice
import com.echotimer.ui.components.Glyph
import com.echotimer.ui.components.NeumorphicCard
import com.echotimer.ui.components.NeumorphicChip
import com.echotimer.ui.components.DialogBodyText
import com.echotimer.ui.components.NeumorphicDialog
import com.echotimer.ui.components.NeumorphicDialogButton
import com.echotimer.ui.components.NeumorphicIconButton
import com.echotimer.ui.components.submitResultText
import com.echotimer.ui.neumorph.NeumorphicSurface
import com.echotimer.ui.theme.LocalEchoColors
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

/** 弹幕横向飞过整屏的时间；比寿命短一点，飞出去后留一小段缓冲再移除。 */
private const val DANMAKU_TRAVEL_MS = 4_200
private const val DANMAKU_LIFETIME_MS = 4_800L
private const val DANMAKU_LANES = 3
private const val DANMAKU_LANE_HEIGHT_DP = 44

/** 同时最多留几条：切后台回来一次跨过十几个人时，不能把屏幕铺满。 */
private const val DANMAKU_MAX_VISIBLE = 6

/**
 * 天梯挑战页：一个不带计次的秒表。
 * 结束（或点返回放弃）后回到榜单详情；成绩由 ViewModel 提交，页面只负责展示结果。
 *
 * [overtakes] 是「超过榜上某个人」的事件流，界面把它渲染成弹幕：
 * 计时每跨过一条记录就飞出一条「超越 张三 01:03.00」，不打断报时、也不需要用户操作。
 */
@Composable
fun ChallengeScreen(
    state: ChallengeState,
    overtakes: SharedFlow<OvertakeNotice>,
    onTogglePause: () -> Unit,
    onFinish: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalEchoColors.current
    var showDiscardDialog by remember { mutableStateOf(false) }

    // 弹幕只在本地保留一小会儿：事件本身是一次性的，不该进 ViewModel 状态。
    val danmaku = remember { mutableStateListOf<OvertakeNotice>() }
    LaunchedEffect(overtakes) {
        overtakes.collect { notice ->
            while (danmaku.size >= DANMAKU_MAX_VISIBLE) {
                danmaku.removeAt(0)
            }
            danmaku.add(notice)
            launch {
                delay(DANMAKU_LIFETIME_MS)
                danmaku.remove(notice)
            }
        }
    }

    BackHandler {
        if (state.finished) onExit() else showDiscardDialog = true
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(14.dp))
        Text(
            text = state.boardName,
            color = colors.textPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp,
        )
        Text(
            text = state.player + " · " + stringResource(R.string.challenge_hint),
            color = colors.textSecondary,
            fontSize = 12.sp,
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            ChallengeDial(state = state)
            if (danmaku.isNotEmpty()) {
                DanmakuLayer(
                    items = danmaku,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(140.dp)
                        .clipToBounds(),
                )
            }
        }

        val result = state.result
        if (state.finished) {
            NeumorphicCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = when {
                        state.submitting -> stringResource(R.string.challenge_submitting)
                        result == null -> ""
                        else -> submitResultText(result)
                    },
                    color = colors.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(12.dp))
                NeumorphicChip(
                    label = stringResource(R.string.challenge_back_to_board),
                    selected = false,
                    onClick = onExit,
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(22.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NeumorphicIconButton(
                    glyph = if (state.running) Glyph.PAUSE else Glyph.PLAY,
                    contentDescription = stringResource(
                        if (state.running) R.string.challenge_pause else R.string.challenge_resume,
                    ),
                    onClick = onTogglePause,
                    size = 88.dp,
                    depth = 18.dp,
                    accent = true,
                )
                NeumorphicIconButton(
                    glyph = Glyph.STOP,
                    contentDescription = stringResource(R.string.challenge_finish),
                    onClick = onFinish,
                    size = 60.dp,
                    enabled = state.elapsedMs > 0L,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
    }

    if (showDiscardDialog) {
        NeumorphicDialog(
            title = stringResource(R.string.challenge_discard_title),
            onDismissRequest = { showDiscardDialog = false },
            contentMaxHeight = 160.dp,
            actions = {
                Spacer(Modifier.weight(1f))
                NeumorphicDialogButton(
                    label = stringResource(R.string.action_cancel),
                    onClick = { showDiscardDialog = false },
                )
                NeumorphicDialogButton(
                    label = stringResource(R.string.challenge_discard_confirm),
                    emphasis = true,
                    danger = true,
                    onClick = {
                        showDiscardDialog = false
                        onExit()
                    },
                )
            },
        ) {
            DialogBodyText(stringResource(R.string.challenge_discard_body))
        }
    }
}

/**
 * 弹幕层：每条从右侧飞向左侧，按 id 分到 3 条轨道，避免前后两条叠在一起。
 * 用 `offset` 做位移而不是布局动画，成本低、也不会影响表盘位置。
 */
@Composable
private fun DanmakuLayer(
    items: List<OvertakeNotice>,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier) {
        val widthPx = constraints.maxWidth.toFloat()
        val laneHeightPx = with(LocalDensity.current) { DANMAKU_LANE_HEIGHT_DP.dp.toPx() }
        items.forEach { notice ->
            key(notice.id) {
                DanmakuText(
                    notice = notice,
                    startX = widthPx,
                    laneOffset = (notice.id % DANMAKU_LANES) * laneHeightPx,
                )
            }
        }
    }
}

@Composable
private fun DanmakuText(
    notice: OvertakeNotice,
    startX: Float,
    laneOffset: Float,
) {
    val colors = LocalEchoColors.current
    val x = remember(notice.id) { Animatable(startX) }
    LaunchedEffect(notice.id) {
        x.animateTo(
            targetValue = -startX,
            animationSpec = tween(DANMAKU_TRAVEL_MS, easing = LinearEasing),
        )
    }

    Row(
        modifier = Modifier
            .offset { IntOffset(x.value.roundToInt(), laneOffset.roundToInt()) }
            .clip(RoundedCornerShape(percent = 50))
            .background(colors.accent.copy(alpha = 0.14f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.challenge_overtake, notice.player),
            color = colors.accent,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = ElapsedFormatter.clock(notice.bestMs),
            color = colors.accent.copy(alpha = 0.72f),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun ChallengeDial(state: ChallengeState) {
    val colors = LocalEchoColors.current
    val clockText = ElapsedFormatter.clock(state.finishedMs ?: state.elapsedMs)
    val stateLabel = stringResource(
        when {
            state.finished -> R.string.timer_state_finished
            state.running -> R.string.timer_state_running
            else -> R.string.timer_state_paused
        },
    )

    Box(modifier = Modifier.size(280.dp), contentAlignment = Alignment.Center) {
        NeumorphicSurface(
            modifier = Modifier.fillMaxSize(),
            circle = true,
            depth = 20.dp,
            pressed = state.running,
        ) {}
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
                fontSize = if (clockText.length > 9) 34.sp else 46.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.5.sp,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = state.player,
                color = colors.textSecondary,
                fontSize = 12.sp,
            )
        }
    }
}
