package com.echotimer.ui.screens

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.echotimer.R
import com.echotimer.core.settings.AppSettings
import com.echotimer.core.settings.ThemeMode
import com.echotimer.core.theme.PaletteSpec
import com.echotimer.core.settings.VoiceEngine
import com.echotimer.core.theme.Palettes
import com.echotimer.core.voice.VoiceCommand
import com.echotimer.ui.components.NeumorphicChip
import com.echotimer.ui.components.NeumorphicSegmented
import com.echotimer.ui.components.VoiceSetupDialog
import com.echotimer.ui.components.NeumorphicToggle
import com.echotimer.ui.components.SectionTitle
import com.echotimer.ui.components.SettingRow
import com.echotimer.ui.components.openVoiceInputSettings
import com.echotimer.ui.components.rememberMicPermission
import com.echotimer.ui.components.rememberSpeechAvailability
import com.echotimer.ui.components.NeumorphicCard
import com.echotimer.ui.theme.LocalEchoColors

/**
 * 设置页。所有改动都通过 [onUpdate] 写回同一份 [AppSettings]，
 * 主题、播报、语音等设置全局只有这一套实现。
 */
@Composable
fun SettingsScreen(
    settings: AppSettings,
    ttsAvailable: Boolean,
    onUpdate: (AppSettings) -> Unit,
    onTestSpeak: () -> Unit,
    onMicPermissionResult: (Boolean) -> Unit,
    offlineTemplates: Int,
    onOpenVoiceTraining: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalEchoColors.current
    val context = LocalContext.current
    val speech = rememberSpeechAvailability()
    var showSpeechHelp by remember { mutableStateOf(false) }
    val mic = rememberMicPermission { granted ->
        onMicPermissionResult(granted)
        // 授权成功且确实有可用的引擎（系统识别或已录离线口令）时，直接打开语音指令。
        val ready = speech.value.usable || offlineTemplates > 0
        if (granted && !settings.voiceCommandsEnabled && ready) {
            onUpdate(settings.copy(voiceCommandsEnabled = true))
        }
    }
    val modeOptions = listOf(
        stringResource(R.string.settings_mode_system),
        stringResource(R.string.settings_mode_light),
        stringResource(R.string.settings_mode_dark),
    )
    val modeIndex = when (settings.themeMode) {
        ThemeMode.SYSTEM -> 0
        ThemeMode.LIGHT -> 1
        ThemeMode.DARK -> 2
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(14.dp))
        Text(
            text = stringResource(R.string.settings_title),
            color = colors.textPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 2.sp,
        )
        Spacer(Modifier.height(16.dp))

        // ---------------- 外观 ----------------
        NeumorphicCard(modifier = Modifier.fillMaxWidth()) {
            SectionTitle(stringResource(R.string.settings_section_appearance))
            Spacer(Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.settings_palette),
                color = colors.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Palettes.ALL.forEach { palette ->
                    PaletteSwatch(
                        palette = palette,
                        selected = palette.id == settings.paletteId,
                        onClick = { onUpdate(settings.copy(paletteId = palette.id)) },
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            Text(
                text = stringResource(R.string.settings_theme_mode),
                color = colors.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(10.dp))
            NeumorphicSegmented(
                options = modeOptions,
                selectedIndex = modeIndex,
                onSelect = { index ->
                    val mode = when (index) {
                        1 -> ThemeMode.LIGHT
                        2 -> ThemeMode.DARK
                        else -> ThemeMode.SYSTEM
                    }
                    onUpdate(settings.copy(themeMode = mode))
                },
            )
        }

        Spacer(Modifier.height(14.dp))

        // ---------------- 播报 ----------------
        NeumorphicCard(modifier = Modifier.fillMaxWidth()) {
            SectionTitle(stringResource(R.string.settings_section_announce))
            Spacer(Modifier.height(14.dp))
            SettingRow(
                title = stringResource(R.string.settings_announce_enabled),
                subtitle = stringResource(R.string.settings_announce_enabled_sub),
                control = {
                    NeumorphicToggle(
                        checked = settings.announcementsEnabled,
                        onCheckedChange = { onUpdate(settings.copy(announcementsEnabled = it)) },
                    )
                },
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.settings_intervals),
                color = colors.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(10.dp))
            AppSettings.SELECTABLE_INTERVALS.chunked(4).forEach { rowItems ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowItems.forEach { seconds ->
                        NeumorphicChip(
                            label = intervalLabel(seconds),
                            selected = seconds == settings.announcementIntervalSeconds,
                            onClick = {
                                onUpdate(settings.copy(announcementIntervalSeconds = seconds))
                            },
                            enabled = settings.announcementsEnabled,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            Text(
                text = stringResource(
                    R.string.settings_interval_preview,
                    intervalLabel(settings.announcementIntervalSeconds),
                    announcementPreview(settings.announcementIntervalSeconds),
                ),
                color = colors.accent,
                fontSize = 12.sp,
                lineHeight = 17.sp,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.settings_intervals_hint),
                color = colors.textSecondary,
                fontSize = 12.sp,
                lineHeight = 17.sp,
            )
            Spacer(Modifier.height(14.dp))
            SettingRow(
                title = stringResource(R.string.settings_test_speak),
                subtitle = if (ttsAvailable) {
                    stringResource(R.string.settings_test_speak_sub)
                } else {
                    stringResource(R.string.settings_tts_missing)
                },
                control = {
                    NeumorphicChip(
                        label = stringResource(R.string.settings_test_speak_action),
                        selected = false,
                        onClick = onTestSpeak,
                        enabled = ttsAvailable,
                    )
                },
            )
        }

        Spacer(Modifier.height(14.dp))

        // ---------------- 语音指令 ----------------
        NeumorphicCard(modifier = Modifier.fillMaxWidth()) {
            SectionTitle(stringResource(R.string.settings_section_voice))
            Spacer(Modifier.height(14.dp))
            SettingRow(
                title = stringResource(R.string.settings_voice_enabled),
                subtitle = stringResource(R.string.settings_voice_enabled_sub),
                control = {
                    NeumorphicToggle(
                        checked = settings.voiceCommandsEnabled,
                        onCheckedChange = { enabled ->
                            // refresh() 的返回值才是最新结果：speech.value 是上次组合时的快照。
                            val latest = speech.refresh()
                            val ready = latest.usable || offlineTemplates > 0
                            when {
                                !enabled -> onUpdate(settings.copy(voiceCommandsEnabled = false))
                                !ready -> showSpeechHelp = true
                                !mic.granted -> mic.request()
                                else -> onUpdate(settings.copy(voiceCommandsEnabled = true))
                            }
                        },
                    )
                },
            )
            if (!speech.value.usable && offlineTemplates == 0) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.settings_voice_unavailable_hint),
                    color = colors.danger,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.settings_voice_engine),
                color = colors.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(10.dp))
            NeumorphicSegmented(
                options = listOf(
                    stringResource(R.string.voice_engine_auto),
                    stringResource(R.string.voice_engine_system),
                    stringResource(R.string.voice_engine_offline),
                ),
                selectedIndex = when (settings.voiceEngine) {
                    VoiceEngine.AUTO -> 0
                    VoiceEngine.SYSTEM -> 1
                    VoiceEngine.OFFLINE -> 2
                },
                onSelect = { index ->
                    onUpdate(
                        settings.copy(
                            voiceEngine = when (index) {
                                0 -> VoiceEngine.AUTO
                                1 -> VoiceEngine.SYSTEM
                                else -> VoiceEngine.OFFLINE
                            },
                        ),
                    )
                },
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.settings_voice_engine_hint),
                color = colors.textSecondary,
                fontSize = 12.sp,
                lineHeight = 17.sp,
            )
            Spacer(Modifier.height(16.dp))
            SettingRow(
                title = stringResource(R.string.settings_offline_templates),
                subtitle = stringResource(
                    R.string.settings_offline_templates_status,
                    offlineTemplates,
                    VoiceCommand.entries.size,
                ) + " · " + stringResource(R.string.settings_offline_templates_hint),
                control = {
                    NeumorphicChip(
                        label = stringResource(R.string.voice_setup_train),
                        selected = false,
                        onClick = onOpenVoiceTraining,
                    )
                },
            )
            Spacer(Modifier.height(16.dp))
            SettingRow(
                title = stringResource(R.string.settings_voice_permission),
                subtitle = if (mic.granted) {
                    stringResource(R.string.settings_granted)
                } else {
                    stringResource(R.string.voice_permission_rationale)
                },
                control = if (mic.granted) {
                    null
                } else {
                    {
                        NeumorphicChip(
                            label = stringResource(R.string.settings_grant),
                            selected = false,
                            onClick = mic.request,
                        )
                    }
                },
            )
            Spacer(Modifier.height(16.dp))
            SettingRow(
                title = stringResource(R.string.settings_voice_language),
                subtitle = settings.recognitionLanguage,
            )
        }

        Spacer(Modifier.height(14.dp))

        // ---------------- 天梯榜 ----------------
        NeumorphicCard(modifier = Modifier.fillMaxWidth()) {
            SectionTitle(stringResource(R.string.settings_section_leaderboard))
            Spacer(Modifier.height(14.dp))
            SettingRow(
                title = stringResource(R.string.settings_leaderboard_editable),
                subtitle = stringResource(R.string.settings_leaderboard_editable_sub),
                control = {
                    NeumorphicToggle(
                        checked = settings.leaderboardEditable,
                        onCheckedChange = { onEdit ->
                            onUpdate(settings.copy(leaderboardEditable = onEdit))
                        },
                    )
                },
            )
        }

        Spacer(Modifier.height(14.dp))

        // ---------------- 其他 ----------------
        NeumorphicCard(modifier = Modifier.fillMaxWidth()) {
            SectionTitle(stringResource(R.string.settings_section_other))
            Spacer(Modifier.height(14.dp))
            SettingRow(
                title = stringResource(R.string.settings_auto_save),
                control = {
                    NeumorphicToggle(
                        checked = settings.autoSaveSessions,
                        onCheckedChange = { onUpdate(settings.copy(autoSaveSessions = it)) },
                    )
                },
            )
            Spacer(Modifier.height(16.dp))
            SettingRow(
                title = stringResource(R.string.settings_keep_screen_on),
                control = {
                    NeumorphicToggle(
                        checked = settings.keepScreenOn,
                        onCheckedChange = { onUpdate(settings.copy(keepScreenOn = it)) },
                    )
                },
            )
            Spacer(Modifier.height(16.dp))
            SettingRow(
                title = stringResource(R.string.settings_speak_start_stop),
                control = {
                    NeumorphicToggle(
                        checked = settings.speakStartAndStop,
                        onCheckedChange = { onUpdate(settings.copy(speakStartAndStop = it)) },
                    )
                },
            )
            Spacer(Modifier.height(18.dp))
            NeumorphicChip(
                label = stringResource(R.string.settings_reset),
                selected = false,
                onClick = { onUpdate(AppSettings()) },
            )
        }

        Spacer(Modifier.height(24.dp))
    }

    if (showSpeechHelp) {
        VoiceSetupDialog(
            availability = speech.value,
            offlineTemplates = offlineTemplates,
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

/** 间隔标签：小于 1 分钟用秒，否则用分钟，避免设置界面被长文本撑开。 */
private fun intervalLabel(seconds: Int): String =
    if (seconds < 60) "${seconds}s" else "${seconds / 60}m"

/** 播报点预览：例如 5 秒 -> "5s、10s、15s…"。 */
private fun announcementPreview(seconds: Int): String =
    (1..3).joinToString("、") { index ->
        if (seconds < 60) "${seconds * index}s" else "${index * seconds / 60}m"
    } + "…"

@Composable
private fun PaletteSwatch(palette: PaletteSpec, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalEchoColors.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .clickable(onClick = onClick),
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(palette.light.background),
                            Color(palette.light.accent),
                            Color(palette.dark.background),
                        ),
                        start = Offset.Zero,
                        end = Offset(size.width, size.height),
                    ),
                    radius = size.minDimension / 2f,
                )
                if (selected) {
                    drawCircle(
                        color = colors.textPrimary,
                        radius = size.minDimension / 2f - 1.dp.toPx(),
                        style = Stroke(width = 2.dp.toPx()),
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = palette.displayName,
            color = if (selected) colors.textPrimary else colors.textSecondary,
            fontSize = 11.sp,
        )
    }
}
