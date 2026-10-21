package com.echotimer.core.voice

import kotlinx.coroutines.flow.StateFlow

/** 语音播报能力抽象：便于替换实现（TTS / 静音 / 测试桩）。 */
interface Announcer {

    /** 播报引擎是否可用（系统缺少中文语音包时为 false）。 */
    val available: StateFlow<Boolean>

    /** 当前是否正在出声；麦克风需要在此期间让路，避免把自己的播报识别成指令。 */
    val speaking: StateFlow<Boolean>

    /** 播报一句话，打断上一条未播完的内容。 */
    fun speak(text: String)

    fun stop()

    fun release()
}
