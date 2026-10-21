package com.echotimer.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/** 录音权限的当前状态与请求入口。 */
data class MicPermission(
    val granted: Boolean,
    val request: () -> Unit,
)

fun hasMicPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED

/**
 * 麦克风权限的唯一实现：计时页与设置页共用。
 * 除了主动请求，还会在回到前台时重新核对（用户可能去系统设置里改过授权）。
 */
@Composable
fun rememberMicPermission(onResult: (Boolean) -> Unit): MicPermission {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestOnResult by rememberUpdatedState(onResult)
    var granted by remember { mutableStateOf(hasMicPermission(context)) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { result ->
        granted = result
        latestOnResult(result)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val current = hasMicPermission(context)
                if (current != granted) {
                    granted = current
                    latestOnResult(current)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    return MicPermission(granted = granted) {
        if (hasMicPermission(context)) {
            granted = true
            latestOnResult(true)
        } else {
            launcher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
}
