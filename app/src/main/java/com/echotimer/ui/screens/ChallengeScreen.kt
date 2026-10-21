package com.echotimer.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.echotimer.R
import com.echotimer.core.time.ElapsedFormatter
import com.echotimer.ui.ChallengeState
import com.echotimer.ui.components.Glyph
import com.echotimer.ui.components.NeumorphicCard
import com.echotimer.ui.components.NeumorphicChip
import com.echotimer.ui.components.NeumorphicIconButton
import com.echotimer.ui.components.submitResultText
import com.echotimer.ui.neumorph.NeumorphicSurface
import com.echotimer.ui.theme.LocalEchoColors

/**
 * 天梯挑战页：一个不带计次的秒表。
 * 结束（或点返回放弃）后回到榜单详情；成绩由 ViewModel 提交，页面只负责展示结果。
 */
@Composable
fun ChallengeScreen(
    state: ChallengeState,
    onTogglePause: () -> Unit,
    onFinish: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalEchoColors.current
    var showDiscardDialog by remember { mutableStateOf(false) }

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
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text(stringResource(R.string.challenge_discard_title)) },
            text = { Text(stringResource(R.string.challenge_discard_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardDialog = false
                        onExit()
                    },
                ) {
                    Text(stringResource(R.string.challenge_discard_confirm), color = colors.danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
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
