package com.echotimer.core.voice.offline

import android.content.Context
import android.content.SharedPreferences
import com.echotimer.core.voice.VoiceCommand
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 离线口令模板的存储（SharedPreferences，独立文件）。
 *
 * 只存 MFCC 特征，不存音频；一条口令一个键，删掉某个命令只影响它自己。
 * 模板数量会作为"是否可离线识别"的依据，所以对外暴露成 StateFlow，界面能实时刷新。
 */
class OfflineVoiceStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    private val _templates = MutableStateFlow(read())
    val templates: StateFlow<List<VoiceTemplate>> = _templates.asStateFlow()

    fun count(): Int = _templates.value.size

    fun has(command: VoiceCommand): Boolean = _templates.value.any { it.command == command }

    fun save(command: VoiceCommand, frames: List<FloatArray>) {
        if (frames.isEmpty()) return
        prefs.edit()
            .putString(keyOf(command), VoiceTemplateCodec.encode(frames))
            .putLong(timeKeyOf(command), System.currentTimeMillis())
            .apply()
        _templates.value = read()
    }

    fun remove(command: VoiceCommand) {
        prefs.edit()
            .remove(keyOf(command))
            .remove(timeKeyOf(command))
            .apply()
        _templates.value = read()
    }

    fun clear() {
        prefs.edit().clear().apply()
        _templates.value = emptyList()
    }

    private fun read(): List<VoiceTemplate> = VoiceCommand.entries.mapNotNull { command ->
        val encoded = prefs.getString(keyOf(command), null) ?: return@mapNotNull null
        val frames = VoiceTemplateCodec.decode(encoded) ?: return@mapNotNull null
        if (frames.isEmpty()) return@mapNotNull null
        VoiceTemplate(
            command = command,
            frames = frames,
            updatedAt = prefs.getLong(timeKeyOf(command), 0L),
        )
    }

    private fun keyOf(command: VoiceCommand) = "template_${command.name}"

    private fun timeKeyOf(command: VoiceCommand) = "template_${command.name}_at"

    private companion object {
        const val FILE_NAME = "echotimer_voice_templates"
    }
}
