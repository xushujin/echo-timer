package com.echotimer.core.voice.offline

import com.echotimer.core.settings.VoiceEngine

/** 最终用哪个引擎听口令。 */
enum class VoiceBackend {
    /** 系统语音识别服务（需要设备上有 RecognitionService）。 */
    SYSTEM,

    /** 自研离线口令识别（只需要录过模板）。 */
    OFFLINE,

    /** 两者都不可用。 */
    NONE,
}

/**
 * 引擎选择规则（纯函数，便于单测）。
 *
 * AUTO 的优先级：系统识别优先——它是通用 ASR，能听懂各种说法，体验更好；
 * 系统不可用（国行 ColorOS / HyperOS 等）时退回离线口令识别。
 */
object VoiceEngineResolver {

    fun resolve(engine: VoiceEngine, systemUsable: Boolean, offlineTemplates: Int): VoiceBackend =
        when (engine) {
            VoiceEngine.SYSTEM -> if (systemUsable) VoiceBackend.SYSTEM else VoiceBackend.NONE
            VoiceEngine.OFFLINE -> if (offlineTemplates > 0) VoiceBackend.OFFLINE else VoiceBackend.NONE
            VoiceEngine.AUTO -> when {
                systemUsable -> VoiceBackend.SYSTEM
                offlineTemplates > 0 -> VoiceBackend.OFFLINE
                else -> VoiceBackend.NONE
            }
        }
}
