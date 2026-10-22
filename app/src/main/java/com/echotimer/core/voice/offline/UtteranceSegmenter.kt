package com.echotimer.core.voice.offline

/**
 * 端点检测（VAD）：把连续的 RMS 序列切成"一段话"。
 *
 * 拆成纯逻辑（不碰 AudioRecord）有两个好处：可以在 JVM 单测里用合成序列验证，
 * 也方便录音训练与实时监听共用同一套判定。
 *
 * 判定规则：
 * - 静音时用指数滑动平均跟踪噪声底，触发门限 = 噪声底 × [triggerRatio]，并设一个绝对下限，
 *   避免在极安静的环境里被风扇声触发；
 * - 连续静音超过 [tailSilenceMs] 视为说完；
 * - 超过 [maxUtteranceMs] 强制结束，防止用户一直说话导致永不返回。
 */
class UtteranceSegmenter(
    private val chunkMs: Int = 20,
    private val triggerRatio: Double = 3.5,
    private val minTriggerRms: Double = 600.0,
    private val tailSilenceMs: Int = 400,
    private val maxUtteranceMs: Int = 2_500,
    initialNoiseFloor: Double = 250.0,
) {

    sealed interface Event {
        /** 还没开始说话。 */
        data object Idle : Event

        /** 刚刚检测到开口。 */
        data object Started : Event

        /** 说话中。 */
        data object Ongoing : Event

        /** 说完了，[durationMs] 是这段语音的时长。 */
        data class Finished(val durationMs: Int) : Event
    }

    var noiseFloor: Double = initialNoiseFloor
        private set

    var isSpeaking: Boolean = false
        private set

    private var silenceMs = 0
    private var durationMs = 0

    val triggerLevel: Double get() = maxOf(noiseFloor * triggerRatio, minTriggerRms)

    fun reset() {
        isSpeaking = false
        silenceMs = 0
        durationMs = 0
    }

    fun accept(rms: Double): Event {
        if (!isSpeaking) {
            if (rms < triggerLevel) {
                // 只在安静时更新噪声底，否则说话声会把门限推高。
                noiseFloor = noiseFloor * 0.95 + rms * 0.05
                return Event.Idle
            }
            isSpeaking = true
            silenceMs = 0
            durationMs = chunkMs
            return Event.Started
        }

        durationMs += chunkMs
        if (rms < triggerLevel * 0.6) silenceMs += chunkMs else silenceMs = 0

        if (silenceMs >= tailSilenceMs || durationMs >= maxUtteranceMs) {
            isSpeaking = false
            val duration = durationMs
            silenceMs = 0
            durationMs = 0
            return Event.Finished(duration)
        }
        return Event.Ongoing
    }
}
