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
 * 每条命令最多存 [VoiceTemplateCodec.MAX_TAKES] 遍录音；再录就替换掉最旧的一遍。
 * 对外暴露两份视图：
 * - [templates]：拍平后的模板列表（一条模板 = 一遍录音），匹配算法直接用；
 * - [takesOf]：某条命令录了几遍，训练界面显示用。
 */
class OfflineVoiceStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    private val _templates = MutableStateFlow(read())
    val templates: StateFlow<List<VoiceTemplate>> = _templates.asStateFlow()

    fun count(): Int = _templates.value.map { it.command }.distinct().size

    fun has(command: VoiceCommand): Boolean = _templates.value.any { it.command == command }

    fun takesOf(command: VoiceCommand): Int = _templates.value.count { it.command == command }

    /** 追加一遍录音，返回这条命令现在的遍数。 */
    fun saveTake(command: VoiceCommand, frames: List<FloatArray>): Int {
        if (frames.isEmpty()) return takesOf(command)
        val existing = readTakes(command).toMutableList()
        existing.add(frames)
        val trimmed = existing.takeLast(VoiceTemplateCodec.MAX_TAKES)
        prefs.edit()
            .putString(keyOf(command), VoiceTemplateCodec.encode(trimmed))
            .putLong(timeKeyOf(command), System.currentTimeMillis())
            .apply()
        _templates.value = read()
        return trimmed.size
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

    private fun readTakes(command: VoiceCommand): List<List<FloatArray>> {
        val encoded = prefs.getString(keyOf(command), null) ?: return emptyList()
        return VoiceTemplateCodec.decodeTakes(encoded).orEmpty()
    }

    private fun read(): List<VoiceTemplate> = VoiceCommand.entries.flatMap { command ->
        val updatedAt = prefs.getLong(timeKeyOf(command), 0L)
        readTakes(command)
            .filter { it.isNotEmpty() }
            .map { frames -> VoiceTemplate(command = command, frames = frames, updatedAt = updatedAt) }
    }

    private fun keyOf(command: VoiceCommand) = "template_${command.name}"

    private fun timeKeyOf(command: VoiceCommand) = "template_${command.name}_at"

    private companion object {
        const val FILE_NAME = "echotimer_voice_templates"
    }
}
