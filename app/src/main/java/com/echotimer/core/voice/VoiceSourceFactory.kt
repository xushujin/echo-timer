package com.echotimer.core.voice

import android.content.Context
import android.speech.SpeechRecognizer
import com.echotimer.core.settings.AppSettings
import com.echotimer.core.settings.SettingsRepository
import com.echotimer.core.voice.offline.OfflineCommandController
import com.echotimer.core.voice.offline.OfflineVoiceStore
import com.echotimer.core.voice.offline.VoiceBackend
import com.echotimer.core.voice.offline.VoiceEngineResolver
import com.echotimer.core.voice.offline.VoiceTemplate
import kotlinx.coroutines.flow.StateFlow

/**
 * 按设置与设备能力创建语音指令来源。
 *
 * 放在容器里而不是 ViewModel 里，是因为"用哪个引擎"同时取决于三件事：
 * 设置里的引擎偏好、设备上有没有系统识别服务、离线模板录了几条。
 */
class VoiceSourceFactory(
    private val context: Context,
    private val store: OfflineVoiceStore,
    private val settingsRepository: SettingsRepository,
) {

    /** 离线模板列表，界面与 ViewModel 都靠它感知"训练完了一条"。 */
    val templates: StateFlow<List<VoiceTemplate>> = store.templates

    fun backend(settings: AppSettings): VoiceBackend = VoiceEngineResolver.resolve(
        engine = settings.voiceEngine,
        systemUsable = SpeechRecognizer.isRecognitionAvailable(context),
        offlineTemplates = store.count(),
    )

    fun create(settings: AppSettings, onCommand: (VoiceCommand) -> Unit): VoiceCommandSource =
        when (backend(settings)) {
            VoiceBackend.SYSTEM -> SpeechCommandController(
                context = context,
                languageTag = settings.recognitionLanguage,
                onCommand = onCommand,
            )

            // NONE 也给离线实现：它在 start() 时会报 UNAVAILABLE，
            // 这样界面只需处理一种对象，不必到处判空。
            VoiceBackend.OFFLINE, VoiceBackend.NONE -> OfflineCommandController(
                store = store,
                sensitivity = { settingsRepository.settings.value.offlineSensitivity },
                onCommand = onCommand,
            )
        }
}
