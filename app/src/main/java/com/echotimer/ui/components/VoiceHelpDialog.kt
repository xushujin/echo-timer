package com.echotimer.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.echotimer.R
import com.echotimer.core.voice.SpeechAvailability

/**
 * 打开与语音识别相关的系统设置。
 *
 * 为什么需要 [preferVoiceInput]：`ACTION_VOICE_INPUT_SETTINGS` 在国内定制系统上并不可靠 ——
 * 一加 / OPPO 的 ColorOS 会把它落到「默认数字助理应用」页面，那跟语音识别没有任何关系，
 * 用户点进去只会更困惑。所以设备上根本没有识别服务时，直接打开系统设置首页。
 */
fun openVoiceInputSettings(context: Context, preferVoiceInput: Boolean = true) {
    val candidates = buildList {
        if (preferVoiceInput) add(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS))
        add(Intent(Settings.ACTION_SETTINGS))
    }
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
 * 语音指令暂时不可用时的引导弹窗。
 *
 * 分两种情况：
 * - 一个系统识别服务都没有：主推自研离线口令识别（这正是国行 ColorOS / HyperOS 的处境）；
 * - 装了服务但系统报告不可用：列出包名，建议重新启用/重装。
 */
@Composable
fun VoiceSetupDialog(
    availability: SpeechAvailability,
    offlineTemplates: Int,
    onDismiss: () -> Unit,
    onOpenTraining: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val hasService = availability.servicePackages.isNotEmpty()
    val canTrain = !hasService && offlineTemplates == 0

    val title = if (hasService) {
        stringResource(R.string.voice_unusable_title)
    } else {
        stringResource(R.string.voice_unavailable_title)
    }
    val body = if (hasService) {
        stringResource(
            R.string.voice_unavailable_body_unusable,
            availability.servicePackages.size,
            availability.servicePackages.joinToString(),
        )
    } else {
        stringResource(R.string.voice_setup_offline_body)
    }

    NeumorphicDialog(
        title = title,
        onDismissRequest = onDismiss,
        contentMaxHeight = 340.dp,
        actions = {
            Spacer(Modifier.weight(1f))
            NeumorphicDialogButton(
                label = stringResource(R.string.voice_unavailable_close),
                onClick = onDismiss,
            )
            if (canTrain) {
                NeumorphicDialogButton(
                    label = stringResource(R.string.voice_setup_train),
                    onClick = onOpenTraining,
                    emphasis = true,
                )
            } else {
                NeumorphicDialogButton(
                    label = stringResource(
                        if (hasService) {
                            R.string.voice_unavailable_open_settings
                        } else {
                            R.string.voice_unavailable_open_system_settings
                        },
                    ),
                    onClick = onOpenSettings,
                    emphasis = true,
                )
            }
        },
    ) {
        DialogBodyText(body)
    }
}
