package com.echotimer.core.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceCommandParserTest {

    @Test
    fun recognisesStartVariants() {
        assertEquals(VoiceCommand.START, VoiceCommandParser.parse("开始"))
        assertEquals(VoiceCommand.START, VoiceCommandParser.parse("开始计时"))
        assertEquals(VoiceCommand.START, VoiceCommandParser.parse("计时开始"))
        assertEquals(VoiceCommand.START, VoiceCommandParser.parse("请开始吧"))
        assertEquals(VoiceCommand.START, VoiceCommandParser.parse("开始！"))
        assertEquals(VoiceCommand.START, VoiceCommandParser.parse("  start "))
        assertEquals(VoiceCommand.START, VoiceCommandParser.parse("Go"))
    }

    @Test
    fun recognisesStopVariants() {
        assertEquals(VoiceCommand.STOP, VoiceCommandParser.parse("结束"))
        assertEquals(VoiceCommand.STOP, VoiceCommandParser.parse("结束计时。"))
        assertEquals(VoiceCommand.STOP, VoiceCommandParser.parse("停止"))
        assertEquals(VoiceCommand.STOP, VoiceCommandParser.parse("停一下"))
        assertEquals(VoiceCommand.STOP, VoiceCommandParser.parse("STOP!"))
        assertEquals(VoiceCommand.STOP, VoiceCommandParser.parse("finish"))
    }

    @Test
    fun recognisesPauseResumeAndLap() {
        assertEquals(VoiceCommand.PAUSE, VoiceCommandParser.parse("暂停"))
        assertEquals(VoiceCommand.PAUSE, VoiceCommandParser.parse("pause"))
        assertEquals(VoiceCommand.RESUME, VoiceCommandParser.parse("继续"))
        assertEquals(VoiceCommand.RESUME, VoiceCommandParser.parse("continue"))
        assertEquals(VoiceCommand.LAP, VoiceCommandParser.parse("计次"))
        assertEquals(VoiceCommand.LAP, VoiceCommandParser.parse("记一圈"))
        assertEquals(VoiceCommand.LAP, VoiceCommandParser.parse("lap"))
    }

    @Test
    fun fullWidthPunctuationAndSpacesAreIgnored() {
        assertEquals(VoiceCommand.START, VoiceCommandParser.parse("开 始 计 时"))
        assertEquals(VoiceCommand.STOP, VoiceCommandParser.parse("结束，谢谢"))
        assertEquals("", VoiceCommandParser.normalize("，。！？"))
    }

    @Test
    fun negationBlocksCommand() {
        assertNull(VoiceCommandParser.parse("不要开始"))
        assertNull(VoiceCommandParser.parse("别结束"))
        assertNull(VoiceCommandParser.parse("取消开始"))
    }

    @Test
    fun stopWinsOverStartWhenBothAppear() {
        // 识别结果偶尔会同时带上两个关键词，结束优先，避免刚结束又重新开始。
        assertEquals(VoiceCommand.STOP, VoiceCommandParser.parse("结束开始"))
    }

    @Test
    fun pauseWinsOverStop() {
        assertEquals(VoiceCommand.PAUSE, VoiceCommandParser.parse("暂停计时"))
    }

    @Test
    fun unrelatedTextIsIgnored() {
        assertNull(VoiceCommandParser.parse(""))
        assertNull(VoiceCommandParser.parse("   "))
        assertNull(VoiceCommandParser.parse("今天天气不错"))
        assertNull(VoiceCommandParser.parse("12345"))
    }

    @Test
    fun normalizeRemovesFillersAndLowercases() {
        assertEquals("start", VoiceCommandParser.normalize("Please START now"))
        assertEquals("开始", VoiceCommandParser.normalize("请 开始 吧"))
    }
}
