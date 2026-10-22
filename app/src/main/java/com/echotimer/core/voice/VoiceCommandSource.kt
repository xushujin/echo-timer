package com.echotimer.core.voice

import kotlinx.coroutines.flow.StateFlow

/**
 * 语音指令来源的统一接口。
 *
 * 目前有两种实现：
 * - [SpeechCommandController]：系统语音识别服务（通用 ASR，需要设备装了 RecognitionService）；
 * - [com.echotimer.core.voice.offline.OfflineCommandController]：自研离线口令识别（只要录过模板）。
 *
 * 界面与 ViewModel 只依赖这个接口，因此"用哪个引擎"完全由设置与设备能力决定。
 */
interface VoiceCommandSource {

    /** 当前监听状态。 */
    val status: StateFlow<VoiceStatus>

    /** 麦克风音量（dB），用于界面上的呼吸动画。 */
    val rmsLevel: StateFlow<Float>

    /** 识别语言；离线引擎与语言无关，赋值是空操作。 */
    var languageTag: String

    fun start()

    fun stop()

    /** 播报期间让出麦克风。 */
    fun setSuspended(value: Boolean)

    fun release()
}
