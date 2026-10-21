package com.echotimer.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.echotimer.R
import com.echotimer.core.voice.SpeechAvailability

/** 尝试打开系统的语音输入设置；不同 ROM 可能没有这个页面，逐级回退到系统设置。 */
fun openVoiceInputSettings(context: Context) {
    val candidates = listOf(
        Intent(Settings.ACTION_VOICE_INPUT_SETTINGS),
        Intent(Settings.ACTION_SETTINGS),
    )
    for (intent in candidates) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (_: ActivityNotFoundException) {
            // 换下一个入口
        }
    }
}

/**
 * 设备没有可用语音识别服务时的说明弹窗。
 * 给出检测结果（找到了几个服务）与可执行的下一步，而不是一句"不支持"。
 */
@Composable
fun SpeechUnavailableDialog(
    availability: SpeechAvailability,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.voice_unavailable_title)) },
        text = {
            Text(
                stringResource(
                    R.string.voice_unavailable_body,
                    availability.servicePackages.size,
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = onOpenSettings) {
                Text(stringResource(R.string.voice_unavailable_open_settings))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.voice_unavailable_close))
            }
        },
    )
}
