package com.echotimer.core.voice

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.speech.RecognitionService
import android.speech.SpeechRecognizer

/** 设备上的语音识别能力现状。 */
data class SpeechAvailability(
    /** 系统 [SpeechRecognizer] 认为是否可用（内部同样检查识别服务是否安装）。 */
    val recognizerAvailable: Boolean,
    /** 设备上安装的语音识别服务包名，按字母排序。 */
    val servicePackages: List<String>,
) {
    /** 是否真的能用：既要有服务，也要系统认为可用。 */
    val usable: Boolean get() = recognizerAvailable && servicePackages.isNotEmpty()

    /** 用于界面上展示的诊断信息。 */
    fun describe(): String = when {
        usable -> "可用（${servicePackages.joinToString()}）"
        servicePackages.isEmpty() -> "未安装语音识别服务"
        else -> "识别服务存在但系统报告不可用（${servicePackages.joinToString()}）"
    }
}

/**
 * 语音识别能力检测。
 *
 * 为什么需要它：`SpeechRecognizer` 只是"客户端"，真正干活的是设备上的识别服务
 * （Google 语音服务、厂商自带的语音服务等）。设备没装这类服务时，功能无法工作，
 * 界面必须说清楚原因，而不是只给一句"不支持"。
 */
object SpeechInspector {

    fun inspect(context: Context): SpeechAvailability {
        val packageManager = context.packageManager
        val intent = Intent(RecognitionService.SERVICE_INTERFACE)
        val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentServices(intent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentServices(intent, 0)
        }
        val packages = resolved.mapNotNull { it.serviceInfo?.packageName }.distinct().sorted()
        return SpeechAvailability(
            recognizerAvailable = SpeechRecognizer.isRecognitionAvailable(context),
            servicePackages = packages,
        )
    }
}
