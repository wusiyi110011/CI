package com.wsy.ci.core.voice

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceCommandParserTest {

    private val today = LocalDate.of(2026, 8, 15)
    private val nowMinute = 10 * 60
    private val pinyinOf: PinyinOf = { it.lowercaseChar().toString() }
    private val candidates = listOf(
        VoiceTarget(1L, "Rust 所有权", VoiceTargetKind.TASK),
        VoiceTarget(2L, "机器学习", VoiceTargetKind.QUEST),
    )

    private fun parse(text: String) =
        VoiceCommandParser.parse(text, today, nowMinute, candidates, pinyinOf)

    @Test
    fun `开始类动词命中候选返回StartTask`() {
        val intent = parse("开始 Rust 所有权那个任务")
        assertTrue(intent is VoiceIntent.StartTask)
        assertEquals(1L, (intent as VoiceIntent.StartTask).target.id)
    }

    @Test
    fun `查询类词能解析日期返回QuerySchedule`() {
        val intent = parse("看一下这周三的安排")
        assertTrue(intent is VoiceIntent.QuerySchedule)
        val expected = LocalDate.of(2026, 8, 12).toEpochDay()
        assertEquals(expected, (intent as VoiceIntent.QuerySchedule).fromEpochDay)
        assertEquals(expected, intent.toEpochDay)
    }

    @Test
    fun `问今天干了什么也能识别成QuerySchedule`() {
        // "干了什么"这类口语提问不含"安排/日程"等词，得靠"什么"兜住
        val intent = parse("今天干了什么")
        assertTrue(intent is VoiceIntent.QuerySchedule)
        val todayEpochDay = today.toEpochDay()
        assertEquals(todayEpochDay, (intent as VoiceIntent.QuerySchedule).fromEpochDay)
        assertEquals(todayEpochDay, intent.toEpochDay)
    }

    @Test
    fun `占用类词能解析时间段返回BlockTime`() {
        val intent = parse("明天下午三点到五点没空要去开会")
        assertTrue(intent is VoiceIntent.BlockTime)
        val blockTime = intent as VoiceIntent.BlockTime
        assertEquals(1, blockTime.spans.size)
        assertEquals(LocalDate.of(2026, 8, 16).toEpochDay(), blockTime.spans[0].epochDay)
        assertEquals(900, blockTime.spans[0].startMinute)
        assertEquals(1020, blockTime.spans[0].endMinute)
        assertEquals("要去开会", blockTime.reason)
    }

    @Test
    fun `占用类词解析不出时间段兜底原因为临时安排`() {
        val intent = parse("明天下午三点到五点没空") // 没有"要去/约了/开会"这类可提取的短语
        assertTrue(intent is VoiceIntent.BlockTime)
        assertEquals("临时安排", (intent as VoiceIntent.BlockTime).reason)
    }

    @Test
    fun `都不满足时返回Unknown`() {
        val intent = parse("今天天气怎么样")
        assertTrue(intent is VoiceIntent.Unknown)
        assertEquals("今天天气怎么样", (intent as VoiceIntent.Unknown).rawText)
    }

    @Test
    fun `开始动词命中但无匹配候选时继续尝试其它意图`() {
        // "开始"未命中任何候选，应继续往下判定，命中占用类词解析出 BlockTime
        val intent = parse("开始弄一下，下午三点到五点没空")
        assertTrue(intent is VoiceIntent.BlockTime)
    }
}
