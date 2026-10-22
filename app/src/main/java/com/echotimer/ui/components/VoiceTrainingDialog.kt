package com.echotimer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.echotimer.R
import com.echotimer.core.settings.OfflineSensitivity
import com.echotimer.core.voice.VoiceCommand
import com.echotimer.core.voice.offline.OfflineVoiceStore
import com.echotimer.core.voice.offline.VoiceEnroller
import com.echotimer.core.voice.offline.VoiceMatcher
import com.echotimer.ui.theme.LocalEchoColors
import java.util.Locale

/**
 * 离线口令训练。
 *
 * 用户把 5 条口令各录一遍，之后完全离线识别，不依赖任何系统语音服务。
 * 训练完可以点「测试识别」验证——它会显示匹配到的口令和距离/门限，
 * 调灵敏度时能直接看到效果，而不是靠猜。
 */
@Composable
fun VoiceTrainingDialog(
    store: OfflineVoiceStore,
    sensitivity: OfflineSensitivity,
    onSensitivityChange: (OfflineSensitivity) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalEchoColors.current
    val enroller = remember { VoiceEnroller() }
    val level by enroller.level.collectAsStateWithLifecycle()
    var templates by remember { mutableStateOf(store.templates.value) }
    var active by remember { mutableStateOf<TrainingTask?>(null) }
    var outcome by remember { mutableStateOf<TrainingOutcome?>(null) }

    val recorded = templates.map { it.command }.toSet()

    LaunchedEffect(active) {
        val task = active ?: return@LaunchedEffect
        outcome = when (val result = enroller.record()) {
            is VoiceEnroller.Result.Success -> when (task) {
                is TrainingTask.Record -> {
                    store.save(task.command, result.frames)
                    templates = store.templates.value
                    TrainingOutcome.Recorded(result.durationMs)
                }

                TrainingTask.Test -> buildTestOutcome(result.frames, store.templates.value, sensitivity)
            }

            VoiceEnroller.Result.NoSpeech -> TrainingOutcome.NoSpeech
            VoiceEnroller.Result.Failed -> TrainingOutcome.Failed
        }
        active = null
    }

    NeumorphicDialog(
        title = stringResource(R.string.voice_training_title),
        onDismissRequest = onDismiss,
        contentMaxHeight = 400.dp,
        actions = {
            Spacer(Modifier.weight(1f))
            if (active != null) {
                NeumorphicDialogButton(
                    label = stringResource(R.string.voice_training_cancel),
                    onClick = { active = null },
                )
            } else {
                NeumorphicDialogButton(
                    label = stringResource(R.string.voice_training_test),
                    onClick = {
                        outcome = null
                        active = TrainingTask.Test
                    },
                    enabled = recorded.isNotEmpty(),
                )
                NeumorphicDialogButton(
                    label = stringResource(R.string.action_close),
                    onClick = onDismiss,
                    emphasis = true,
                )
            }
        },
    ) {
        val task = active
        if (task != null) {
            Text(
                text = if (task is TrainingTask.Record) {
                    stringResource(R.string.voice_training_prompt, stringResource(task.command.labelRes()))
                } else {
                    stringResource(R.string.voice_training_test_prompt)
                },
                color = colors.textPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.voice_training_listening),
                color = colors.textSecondary,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(16.dp))
            LevelBar(level = level)
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.voice_training_tip),
                color = colors.textSecondary,
                fontSize = 12.sp,
                lineHeight = 17.sp,
            )
        } else {
            Text(
                text = stringResource(R.string.voice_training_intro),
                color = colors.textSecondary,
                fontSize = 12.sp,
                lineHeight = 17.sp,
            )
            Spacer(Modifier.height(12.dp))
            VoiceCommand.entries.forEach { command ->
                TrainingRow(
                    label = stringResource(command.labelRes()),
                    recorded = command in recorded,
                    onRecord = {
                        outcome = null
                        active = TrainingTask.Record(command)
                    },
                )
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(6.dp))
            SensitivityRow(sensitivity = sensitivity, onSensitivityChange = onSensitivityChange)
            val current = outcome
            if (current != null) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = outcomeText(current),
                    color = if (current is TrainingOutcome.Hit) colors.accent else colors.danger,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
            }
        }
    }
}

private sealed interface TrainingTask {
    data class Record(val command: VoiceCommand) : TrainingTask

    data object Test : TrainingTask
}

private sealed interface TrainingOutcome {
    data class Recorded(val durationMs: Int) : TrainingOutcome

    data class Hit(val command: VoiceCommand, val distance: Float, val threshold: Float) : TrainingOutcome

    data class Miss(val nearest: VoiceCommand?, val distance: Float, val threshold: Float) : TrainingOutcome

    data object NoSpeech : TrainingOutcome

    data object Failed : TrainingOutcome
}

@Composable
private fun outcomeText(outcome: TrainingOutcome): String = when (outcome) {
    is TrainingOutcome.Recorded -> stringResource(
        R.string.voice_training_recorded_seconds,
        formatSeconds(outcome.durationMs),
    )

    is TrainingOutcome.Hit -> stringResource(
        R.string.voice_training_test_hit,
        stringResource(outcome.command.labelRes()),
        formatDistance(outcome.distance),
        formatDistance(outcome.threshold),
    )

    is TrainingOutcome.Miss -> {
        val nearest = outcome.nearest
        if (nearest != null) {
            stringResource(
                R.string.voice_training_test_miss,
                stringResource(nearest.labelRes()),
                formatDistance(outcome.distance),
                formatDistance(outcome.threshold),
            )
        } else {
            stringResource(R.string.voice_training_test_empty)
        }
    }

    TrainingOutcome.NoSpeech -> stringResource(R.string.voice_training_no_speech)
    TrainingOutcome.Failed -> stringResource(R.string.voice_training_failed)
}

private fun formatSeconds(durationMs: Int): String =
    String.format(Locale.US, "%.1f", durationMs / 1000f)

private fun formatDistance(value: Float): String = String.format(Locale.US, "%.2f", value)

private fun buildTestOutcome(
    frames: List<FloatArray>,
    templates: List<com.echotimer.core.voice.offline.VoiceTemplate>,
    sensitivity: OfflineSensitivity,
): TrainingOutcome {
    if (templates.isEmpty()) return TrainingOutcome.Miss(null, 0f, 0f)
    val threshold = VoiceMatcher.threshold(templates, sensitivity.ratio)
    val match = VoiceMatcher.match(frames, templates, sensitivity.ratio)
    if (match != null) {
        return TrainingOutcome.Hit(match.command, match.distance, match.threshold)
    }
    val nearest = VoiceMatcher.ranking(frames, templates).firstOrNull()
    return TrainingOutcome.Miss(nearest?.first, nearest?.second ?: 0f, threshold)
}

private fun VoiceCommand.labelRes(): Int = when (this) {
    VoiceCommand.START -> R.string.timer_action_start
    VoiceCommand.PAUSE -> R.string.timer_action_pause
    VoiceCommand.RESUME -> R.string.timer_action_resume
    VoiceCommand.STOP -> R.string.timer_action_finish
    VoiceCommand.LAP -> R.string.timer_action_lap
}

@Composable
private fun LevelBar(level: Float) {
    val colors = LocalEchoColors.current
    val fraction = ((level + 2f) / 14f).coerceIn(0.03f, 1f)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(CircleShape)
            .background(colors.surfaceAlt),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .height(8.dp)
                .clip(CircleShape)
                .background(colors.accent),
        )
    }
}

@Composable
private fun TrainingRow(label: String, recorded: Boolean, onRecord: () -> Unit) {
    val colors = LocalEchoColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (recorded) colors.accent else colors.textSecondary.copy(alpha = 0.35f)),
        )
        Spacer(Modifier.size(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = colors.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = stringResource(
                    if (recorded) R.string.voice_training_recorded else R.string.voice_training_missing,
                ),
                color = if (recorded) colors.accent else colors.textSecondary,
                fontSize = 11.sp,
            )
        }
        NeumorphicChip(
            label = stringResource(
                if (recorded) R.string.voice_training_rerecord else R.string.voice_training_record,
            ),
            selected = false,
            onClick = onRecord,
            fontSize = 12.sp,
            horizontalPadding = 12.dp,
        )
    }
}

@Composable
private fun SensitivityRow(
    sensitivity: OfflineSensitivity,
    onSensitivityChange: (OfflineSensitivity) -> Unit,
) {
    val colors = LocalEchoColors.current
    Text(
        text = stringResource(R.string.voice_training_sensitivity),
        color = colors.textSecondary,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
    )
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OfflineSensitivity.entries.forEach { option ->
            NeumorphicChip(
                label = stringResource(option.labelRes()),
                selected = option == sensitivity,
                onClick = { onSensitivityChange(option) },
                fontSize = 12.sp,
                horizontalPadding = 12.dp,
            )
        }
    }
    Spacer(Modifier.height(6.dp))
    Text(
        text = stringResource(R.string.voice_training_sensitivity_hint),
        color = colors.textSecondary,
        fontSize = 11.sp,
        lineHeight = 16.sp,
    )
}

private fun OfflineSensitivity.labelRes(): Int = when (this) {
    OfflineSensitivity.LOW -> R.string.voice_sensitivity_low
    OfflineSensitivity.MEDIUM -> R.string.voice_sensitivity_medium
    OfflineSensitivity.HIGH -> R.string.voice_sensitivity_high
}
