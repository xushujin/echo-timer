package com.echotimer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.echotimer.R
import com.echotimer.core.settings.OfflineSensitivity
import com.echotimer.core.voice.VoiceCommand
import com.echotimer.core.voice.offline.MatchStats
import com.echotimer.core.voice.offline.OfflineVoiceStore
import com.echotimer.core.voice.offline.VoiceEnroller
import com.echotimer.core.voice.offline.VoiceMatcher
import com.echotimer.core.voice.offline.VoiceTemplate
import com.echotimer.ui.theme.LocalEchoColors
import java.util.Locale

/**
 * 离线口令训练 + 自检。
 *
 * 设计目标：**任何失败都能看出原因**。
 * - 录音时实时显示"峰值 dBFS / 触发门限 dBFS"——峰值到不了门限就是没录到，一目了然；
 * - 结果卡片放在最上方（原来放在最底部，长列表下经常看不到）；
 * - 「测试识别」给出命中口令、距离、门限，以及模板的类内/类间距离；
 * - 每条口令支持录两遍：同一句话两遍的差异用来定门限，比固定系数可靠得多。
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
    val peakDbfs by enroller.peakDbfs.collectAsStateWithLifecycle()
    val triggerDbfs by enroller.triggerDbfs.collectAsStateWithLifecycle()

    var templates by remember { mutableStateOf(store.templates.value) }
    var active by remember { mutableStateOf<TrainingTask?>(null) }
    var outcome by remember { mutableStateOf<TrainingOutcome?>(null) }

    val takesByCommand = remember(templates) { templates.groupingBy { it.command }.eachCount() }
    val recordedCommands = takesByCommand.keys
    val stats = remember(templates, sensitivity) { templateStats(templates, sensitivity) }

    LaunchedEffect(active) {
        val task = active ?: return@LaunchedEffect
        outcome = when (val result = enroller.record()) {
            is VoiceEnroller.Result.Success -> when (task) {
                is TrainingTask.Record -> {
                    val takes = store.saveTake(task.command, result.frames)
                    templates = store.templates.value
                    TrainingOutcome.Recorded(result.durationMs, takes)
                }

                TrainingTask.Test -> testOutcome(result.frames, store.templates.value, sensitivity)
            }

            is VoiceEnroller.Result.NoSpeech -> TrainingOutcome.NoSpeech(
                peakDbfs = com.echotimer.core.voice.offline.AudioLevel.toDbfs(result.peakRms),
                triggerRms = result.triggerRms,
                waitedMs = result.waitedMs,
            )

            VoiceEnroller.Result.Failed -> TrainingOutcome.MicFailed
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
                    enabled = templates.isNotEmpty(),
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
            RecordingPanel(
                title = if (task is TrainingTask.Record) {
                    stringResource(R.string.voice_training_prompt, stringResource(task.command.labelRes()))
                } else {
                    stringResource(R.string.voice_training_test_prompt)
                },
                level = level,
                peakDbfs = peakDbfs,
                triggerDbfs = triggerDbfs,
            )
            return@NeumorphicDialog
        }

        val current = outcome
        if (current != null) {
            OutcomeCard(current)
            Spacer(Modifier.height(12.dp))
        }

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
                takes = takesByCommand[command] ?: 0,
                onRecord = {
                    outcome = null
                    active = TrainingTask.Record(command)
                },
            )
            Spacer(Modifier.height(8.dp))
        }

        Spacer(Modifier.height(4.dp))
        SensitivityRow(sensitivity = sensitivity, onSensitivityChange = onSensitivityChange)

        Spacer(Modifier.height(10.dp))
        DiagnosticsBlock(
            commandCount = recordedCommands.size,
            totalTakes = templates.size,
            stats = stats,
        )
    }
}

private sealed interface TrainingTask {
    data class Record(val command: VoiceCommand) : TrainingTask

    data object Test : TrainingTask
}

private sealed interface TrainingOutcome {
    data class Recorded(val durationMs: Int, val takes: Int) : TrainingOutcome

    data class Hit(val command: VoiceCommand, val distance: Float, val threshold: Float) : TrainingOutcome

    data class Miss(val nearest: VoiceCommand?, val distance: Float, val threshold: Float) : TrainingOutcome

    data class NoSpeech(val peakDbfs: Double, val triggerRms: Double, val waitedMs: Long) : TrainingOutcome

    data object MicFailed : TrainingOutcome
}

/** 结果卡片：固定放在内容最上方，避免"结果在最底部、用户看不到"。 */
@Composable
private fun OutcomeCard(outcome: TrainingOutcome) {
    val colors = LocalEchoColors.current
    val positive = outcome is TrainingOutcome.Recorded || outcome is TrainingOutcome.Hit
    val tint = if (positive) colors.accent else colors.danger

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(tint.copy(alpha = 0.12f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Column {
            Text(
                text = outcomeHeadline(outcome),
                color = tint,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 19.sp,
            )
            val detail = outcomeDetail(outcome)
            if (detail != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = detail,
                    color = colors.textSecondary,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                )
            }
        }
    }
}

@Composable
private fun outcomeHeadline(outcome: TrainingOutcome): String = when (outcome) {
    is TrainingOutcome.Recorded -> stringResource(
        R.string.voice_training_recorded_take,
        outcome.takes,
        formatSeconds(outcome.durationMs),
    )

    is TrainingOutcome.Hit -> stringResource(
        R.string.voice_training_hit,
        stringResource(outcome.command.labelRes()),
        formatDecimal(outcome.distance),
        formatDecimal(outcome.threshold),
    )

    is TrainingOutcome.Miss -> {
        val nearest = outcome.nearest
        if (nearest == null) {
            stringResource(R.string.voice_training_test_empty)
        } else {
            stringResource(
                R.string.voice_training_miss,
                stringResource(nearest.labelRes()),
                formatDecimal(outcome.distance),
                formatDecimal(outcome.threshold),
                formatDecimal((outcome.distance - outcome.threshold).coerceAtLeast(0f)),
            )
        }
    }

    is TrainingOutcome.NoSpeech -> stringResource(
        R.string.voice_training_no_speech_detail,
        formatDb(outcome.peakDbfs),
        formatDb(dbFromRms(outcome.triggerRms)),
    )

    TrainingOutcome.MicFailed -> stringResource(R.string.voice_training_mic_failed)
}

@Composable
private fun outcomeDetail(outcome: TrainingOutcome): String? = when (outcome) {
    is TrainingOutcome.NoSpeech -> stringResource(R.string.voice_training_no_speech_hint)
    is TrainingOutcome.Miss -> stringResource(R.string.voice_training_miss_hint)
    is TrainingOutcome.Recorded -> stringResource(R.string.voice_training_recorded_hint)
    else -> null
}

@Composable
private fun RecordingPanel(
    title: String,
    level: Float,
    peakDbfs: Double,
    triggerDbfs: Double,
) {
    val colors = LocalEchoColors.current
    Text(
        text = title,
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
    Spacer(Modifier.height(8.dp))
    Text(
        text = stringResource(
            R.string.voice_training_live_level,
            formatDb(peakDbfs),
            formatDb(triggerDbfs),
        ),
        color = if (peakDbfs > triggerDbfs) colors.accent else colors.textSecondary,
        fontSize = 11.sp,
        fontFamily = FontFamily.Monospace,
    )
    Spacer(Modifier.height(10.dp))
    Text(
        text = stringResource(R.string.voice_training_tip),
        color = colors.textSecondary,
        fontSize = 12.sp,
        lineHeight = 17.sp,
    )
}

/** 诊断信息：模板数量、类内/类间距离、当前门限。 */
@Composable
private fun DiagnosticsBlock(commandCount: Int, totalTakes: Int, stats: TemplateStats) {
    val colors = LocalEchoColors.current
    Text(
        text = stringResource(
            R.string.voice_training_diag_templates,
            commandCount,
            VoiceCommand.entries.size,
            totalTakes,
        ),
        color = colors.textSecondary,
        fontSize = 11.sp,
        fontFamily = FontFamily.Monospace,
    )
    Spacer(Modifier.height(2.dp))
    Text(
        text = stringResource(
            R.string.voice_training_diag_threshold,
            formatDecimalOrDash(stats.sameClass),
            formatDecimalOrDash(stats.interClass),
            formatDecimal(stats.threshold),
        ),
        color = colors.textSecondary,
        fontSize = 11.sp,
        fontFamily = FontFamily.Monospace,
    )
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
private fun TrainingRow(label: String, takes: Int, onRecord: () -> Unit) {
    val colors = LocalEchoColors.current
    val recorded = takes > 0
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
                text = if (recorded) {
                    stringResource(R.string.voice_training_takes, takes)
                } else {
                    stringResource(R.string.voice_training_missing)
                },
                color = if (recorded) colors.accent else colors.textSecondary,
                fontSize = 11.sp,
            )
        }
        NeumorphicChip(
            label = stringResource(
                if (recorded) R.string.voice_training_record_again else R.string.voice_training_record,
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

private data class TemplateStats(
    val sameClass: Float?,
    val interClass: Float?,
    val threshold: Float,
)

private fun templateStats(templates: List<VoiceTemplate>, sensitivity: OfflineSensitivity): TemplateStats =
    TemplateStats(
        sameClass = VoiceMatcher.sameClassDistance(templates),
        interClass = VoiceMatcher.interClassDistance(templates),
        threshold = VoiceMatcher.threshold(templates, sensitivity.ratio),
    )

private fun testOutcome(
    frames: List<FloatArray>,
    templates: List<VoiceTemplate>,
    sensitivity: OfflineSensitivity,
): TrainingOutcome {
    if (templates.isEmpty()) return TrainingOutcome.Miss(null, 0f, 0f)
    val stats: MatchStats = VoiceMatcher.stats(frames, templates, sensitivity.ratio)
    val nearest = stats.bestCommand ?: return TrainingOutcome.Miss(null, 0f, stats.threshold)
    return if (stats.bestDistance <= stats.threshold) {
        TrainingOutcome.Hit(nearest, stats.bestDistance, stats.threshold)
    } else {
        TrainingOutcome.Miss(nearest, stats.bestDistance, stats.threshold)
    }
}

private fun VoiceCommand.labelRes(): Int = when (this) {
    VoiceCommand.START -> R.string.timer_action_start
    VoiceCommand.PAUSE -> R.string.timer_action_pause
    VoiceCommand.RESUME -> R.string.timer_action_resume
    VoiceCommand.STOP -> R.string.timer_action_finish
    VoiceCommand.LAP -> R.string.timer_action_lap
}

private fun OfflineSensitivity.labelRes(): Int = when (this) {
    OfflineSensitivity.LOW -> R.string.voice_sensitivity_low
    OfflineSensitivity.MEDIUM -> R.string.voice_sensitivity_medium
    OfflineSensitivity.HIGH -> R.string.voice_sensitivity_high
}

private fun formatSeconds(durationMs: Int): String =
    String.format(Locale.US, "%.1f", durationMs / 1000f)

private fun formatDecimal(value: Float): String = String.format(Locale.US, "%.2f", value)

private fun formatDecimalOrDash(value: Float?): String =
    if (value == null || value == Float.MAX_VALUE) "—" else formatDecimal(value)

private fun formatDb(value: Double): String =
    if (value <= -89.0) "≤-90" else String.format(Locale.US, "%.0f", value)

/** 触发门限是以 RMS 给出的，展示时换成同一套 dBFS 刻度。 */
private fun dbFromRms(rms: Double): Double {
    if (rms <= 0.5) return -90.0
    return (20.0 * kotlin.math.log10(rms / 32_768.0)).coerceIn(-90.0, 0.0)
}
