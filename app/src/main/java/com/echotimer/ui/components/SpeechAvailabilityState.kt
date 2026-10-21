package com.echotimer.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.echotimer.core.voice.SpeechAvailability
import com.echotimer.core.voice.SpeechInspector

/**
 * 语音识别能力状态 + 手动刷新入口。
 * [refresh] 会同时更新状态并返回最新结果，便于在点击回调里立即使用（不必等重组）。
 */
class SpeechAvailabilityState(
    val value: SpeechAvailability,
    val refresh: () -> SpeechAvailability,
)

/**
 * 语音识别能力检测的唯一实现：计时页与设置页共用。
 * 回到前台时自动重新检测，用户去系统设置里启用语音服务后返回即可生效。
 */
@Composable
fun rememberSpeechAvailability(): SpeechAvailabilityState {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var availability by remember { mutableStateOf(SpeechInspector.inspect(context)) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                availability = SpeechInspector.inspect(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    return SpeechAvailabilityState(
        value = availability,
        refresh = {
            val latest = SpeechInspector.inspect(context)
            availability = latest
            latest
        },
    )
}
