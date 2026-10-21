package com.echotimer.core.voice

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 基于系统 TTS 的播报实现。
 *
 * 为什么用系统 TTS：语音合成需要高质量的离线声学模型，属于平台能力而不是业务逻辑；
 * 应用侧自研的是"什么时候播、播什么、和麦克风怎么互斥"这套调度（见 [com.echotimer.core.time.AnnouncementScheduler]）。
 */
class TtsAnnouncer(context: Context) : Announcer {

    private val _available = MutableStateFlow(false)
    override val available: StateFlow<Boolean> = _available.asStateFlow()

    private val _speaking = MutableStateFlow(false)
    override val speaking: StateFlow<Boolean> = _speaking.asStateFlow()

    private var engine: TextToSpeech? = null

    /** 构造 TextToSpeech 时回调可能早于字段赋值，先缓存状态。 */
    private var initStatus: Int? = null

    init {
        val created = TextToSpeech(context.applicationContext) { status -> handleInit(status) }
        engine = created
        created.setOnUtteranceProgressListener(
            object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _speaking.value = true
                }

                override fun onDone(utteranceId: String?) {
                    _speaking.value = false
                }

                // 抽象成员（API 21 起标记为废弃，但仍必须实现）。
                @Suppress("OVERRIDE_DEPRECATION")
                override fun onError(utteranceId: String?) {
                    _speaking.value = false
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    _speaking.value = false
                }

                override fun onStop(utteranceId: String?, interrupted: Boolean) {
                    _speaking.value = false
                }
            },
        )
        initStatus?.let { handleInit(it) }
    }

    override fun speak(text: String) {
        if (text.isBlank() || !_available.value) return
        val tts = engine ?: return
        // QUEUE_FLUSH：播报是"时间点提示"，旧内容过时即丢弃，不排队堆积。
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
    }

    override fun stop() {
        engine?.stop()
        _speaking.value = false
    }

    override fun release() {
        _speaking.value = false
        _available.value = false
        engine?.stop()
        engine?.shutdown()
        engine = null
    }

    private fun handleInit(status: Int) {
        initStatus = status
        val tts = engine ?: return
        if (status != TextToSpeech.SUCCESS) {
            _available.value = false
            return
        }
        tts.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
        val chinese = tts.setLanguage(Locale.SIMPLIFIED_CHINESE)
        val supported = chinese != TextToSpeech.LANG_MISSING_DATA && chinese != TextToSpeech.LANG_NOT_SUPPORTED
        if (!supported) {
            // 没有中文语音包时退回系统默认语言，至少能读出数字。
            val fallback = tts.setLanguage(Locale.getDefault())
            _available.value =
                fallback != TextToSpeech.LANG_MISSING_DATA && fallback != TextToSpeech.LANG_NOT_SUPPORTED
        } else {
            _available.value = true
        }
    }

    private companion object {
        const val UTTERANCE_ID = "echotimer-announcement"
    }
}
