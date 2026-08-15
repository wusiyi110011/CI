package com.wsy.ci.core.voice

import com.wsy.ci.llm.ParsedVoiceCommand
import com.wsy.ci.llm.ParsedVoiceSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ParsedVoiceCommandMapperTest {

    private val candidates = listOf(
        VoiceTarget(1L, "Rust 所有权", VoiceTargetKind.TASK),
        VoiceTarget(2L, "机器学习", VoiceTargetKind.QUEST),
    )

    @Test
    fun `block意图转成BlockTime`() {
        val parsed = ParsedVoiceCommand(
            intent = "block",
            spans = listOf(ParsedVoiceSpan("2026-08-16", "09:00", "10:30")),
            reason = "要去开会",
        )
        val result = parsed.toVoiceIntent(candidates, "原始文本")
        assertTrue(result is VoiceIntent.BlockTime)
        val blockTime = result as VoiceIntent.BlockTime
        assertEquals(1, blockTime.spans.size)
        assertEquals(9 * 60, blockTime.spans[0].startMinute)
        assertEquals(10 * 60 + 30, blockTime.spans[0].endMinute)
        assertEquals("要去开会", blockTime.reason)
    }

    @Test
    fun `block意图没有可用时间段时回退成Unknown`() {
        val parsed = ParsedVoiceCommand(intent = "block", spans = emptyList())
        val result = parsed.toVoiceIntent(candidates, "原始文本")
        assertEquals(VoiceIntent.Unknown("原始文本"), result)
    }

    @Test
    fun `query意图转成QuerySchedule`() {
        val parsed = ParsedVoiceCommand(intent = "query", from = "2026-08-17", to = "2026-08-23")
        val result = parsed.toVoiceIntent(candidates, "原始文本")
        assertTrue(result is VoiceIntent.QuerySchedule)
        val query = result as VoiceIntent.QuerySchedule
        assertEquals(java.time.LocalDate.of(2026, 8, 17).toEpochDay(), query.fromEpochDay)
        assertEquals(java.time.LocalDate.of(2026, 8, 23).toEpochDay(), query.toEpochDay)
    }

    @Test
    fun `query意图日期缺失时回退成Unknown`() {
        val parsed = ParsedVoiceCommand(intent = "query", from = "2026-08-17", to = null)
        val result = parsed.toVoiceIntent(candidates, "原始文本")
        assertEquals(VoiceIntent.Unknown("原始文本"), result)
    }

    @Test
    fun `start意图命中候选转成StartTask`() {
        val parsed = ParsedVoiceCommand(intent = "start", targetId = 2L)
        val result = parsed.toVoiceIntent(candidates, "原始文本")
        assertEquals(VoiceIntent.StartTask(candidates[1]), result)
    }

    @Test
    fun `start意图targetId不在候选清单里回退成Unknown`() {
        val parsed = ParsedVoiceCommand(intent = "start", targetId = 999L)
        val result = parsed.toVoiceIntent(candidates, "原始文本")
        assertEquals(VoiceIntent.Unknown("原始文本"), result)
    }

    @Test
    fun `未知intent字符串回退成Unknown`() {
        val parsed = ParsedVoiceCommand(intent = "什么鬼")
        val result = parsed.toVoiceIntent(candidates, "原始文本")
        assertEquals(VoiceIntent.Unknown("原始文本"), result)
    }
}
