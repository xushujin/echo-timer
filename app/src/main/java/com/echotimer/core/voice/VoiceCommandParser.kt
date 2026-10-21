package com.echotimer.core.voice

import java.util.Locale

/** 可被语音触发的动作。 */
enum class VoiceCommand {
    START,
    PAUSE,
    RESUME,
    STOP,
    LAP,
}

/**
 * 语音指令解析：把语音识别返回的自由文本归一化成 [VoiceCommand]。
 *
 * 这是"自研"的部分——识别引擎只负责给文本，文本怎么算一条有效指令、怎么去抖、
 * 怎么避免"不要开始"被误判，全部由这里的规则决定（规则与 README 表格一一对应）。
 */
object VoiceCommandParser {

    /** 否定词：出现即放弃整条指令，避免"不要开始"被当成"开始"。 */
    private val NEGATIONS = listOf("不要", "不用", "别", "取消", "not", "donot")

    /** 语气词/客套词：先剔除再做包含匹配，"请开始吧"才能命中"开始"。 */
    private val FILLERS = listOf(
        "请", "帮我", "麻烦", "现在", "马上", "一下", "吧", "啊", "呀", "呢", "了", "嘛",
        "please", "now",
    )

    /**
     * 匹配顺序有讲究：
     * - 暂停要在"停"之前，否则"暂停"会被"停"抢走；
     * - 继续要在开始之前，避免某些识别结果里两者同时出现时误判；
     * - 结束要在开始之前，避免"结束"句子里夹带"开始"。
     */
    private val RULES: List<Pair<VoiceCommand, List<String>>> = listOf(
        VoiceCommand.PAUSE to listOf("暂停", "pause"),
        VoiceCommand.RESUME to listOf("继续", "接着", "resume", "continue"),
        VoiceCommand.LAP to listOf("计次", "记一圈", "一圈", "分段", "lap", "split"),
        VoiceCommand.STOP to listOf("结束", "停止", "停下", "停", "完成", "stop", "end", "finish", "done"),
        VoiceCommand.START to listOf("开始", "启动", "计时", "start", "begin", "go"),
    )

    /** 解析一条识别结果；无法识别时返回 null。 */
    fun parse(raw: String): VoiceCommand? {
        val normalized = normalize(raw)
        if (normalized.isEmpty()) return null
        if (NEGATIONS.any { normalized.contains(it) }) return null
        for ((command, keywords) in RULES) {
            if (keywords.any { normalized.contains(it) }) return command
        }
        return null
    }

    /**
     * 归一化：全角转半角 -> 小写 -> 只保留字母/数字/汉字 -> 去掉语气词。
     * 归一化结果仅用于匹配，不用于展示。
     */
    fun normalize(raw: String): String {
        val halfWidth = StringBuilder(raw.length)
        for (ch in raw) {
            halfWidth.append(
                if (ch.code in FULL_WIDTH_START..FULL_WIDTH_END) {
                    (ch.code - FULL_WIDTH_OFFSET).toChar()
                } else {
                    ch
                },
            )
        }
        val cleaned = StringBuilder(halfWidth.length)
        for (ch in halfWidth.toString().lowercase(Locale.ROOT)) {
            if (ch.isLetterOrDigit()) cleaned.append(ch)
        }
        var text = cleaned.toString()
        for (filler in FILLERS) {
            text = text.replace(filler, "")
        }
        return text
    }

    private const val FULL_WIDTH_START = 0xFF01
    private const val FULL_WIDTH_END = 0xFF5E
    private const val FULL_WIDTH_OFFSET = 0xFEE0
}
