package com.wsy.ci.core.voice

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 基准日 2026-08-15 是周六，方便验证「本周/下周」「最近一次周几」的顺延逻辑。 */
class ChineseTimeParserTest {

    private val today = LocalDate.of(2026, 8, 15)

    private fun day(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).toEpochDay()

    // ---------- 相对日 ----------

    @Test
    fun `今天和今儿解析成当天`() {
        assertEquals(day(2026, 8, 15) to day(2026, 8, 15), ChineseTimeParser.parseDateRange("今天有什么安排", today))
        assertEquals(day(2026, 8, 15) to day(2026, 8, 15), ChineseTimeParser.parseDateRange("今儿有什么安排", today))
    }

    @Test
    fun `明天和明儿解析成次日`() {
        assertEquals(day(2026, 8, 16) to day(2026, 8, 16), ChineseTimeParser.parseDateRange("明天有什么安排", today))
        assertEquals(day(2026, 8, 16) to day(2026, 8, 16), ChineseTimeParser.parseDateRange("明儿有什么安排", today))
    }

    @Test
    fun `后天解析成加两天`() {
        assertEquals(day(2026, 8, 17) to day(2026, 8, 17), ChineseTimeParser.parseDateRange("后天有什么安排", today))
    }

    @Test
    fun `大后天解析成加三天`() {
        assertEquals(day(2026, 8, 18) to day(2026, 8, 18), ChineseTimeParser.parseDateRange("大后天有什么安排", today))
    }

    @Test
    fun `昨天解析成前一天`() {
        assertEquals(day(2026, 8, 14) to day(2026, 8, 14), ChineseTimeParser.parseDateRange("昨天做了什么", today))
    }

    // ---------- 周 ----------

    @Test
    fun `这周三和本周三解析成本周三`() {
        assertEquals(day(2026, 8, 12) to day(2026, 8, 12), ChineseTimeParser.parseDateRange("这周三的安排", today))
        assertEquals(day(2026, 8, 12) to day(2026, 8, 12), ChineseTimeParser.parseDateRange("本周三的安排", today))
    }

    @Test
    fun `下周三解析成下周三`() {
        assertEquals(day(2026, 8, 19) to day(2026, 8, 19), ChineseTimeParser.parseDateRange("下周三的安排", today))
    }

    @Test
    fun `无限定词的周三星期三礼拜三取最近一次出现`() {
        // 今天是周六，本周三已过去，最近一次出现在下周三 8-19
        assertEquals(day(2026, 8, 19) to day(2026, 8, 19), ChineseTimeParser.parseDateRange("周三的安排", today))
        assertEquals(day(2026, 8, 19) to day(2026, 8, 19), ChineseTimeParser.parseDateRange("星期三的安排", today))
        assertEquals(day(2026, 8, 19) to day(2026, 8, 19), ChineseTimeParser.parseDateRange("礼拜三的安排", today))
    }

    @Test
    fun `周日周天周七都算周日`() {
        // 今天周六，最近一次周日是明天 8-16
        assertEquals(day(2026, 8, 16) to day(2026, 8, 16), ChineseTimeParser.parseDateRange("周日的安排", today))
        assertEquals(day(2026, 8, 16) to day(2026, 8, 16), ChineseTimeParser.parseDateRange("周天的安排", today))
        assertEquals(day(2026, 8, 16) to day(2026, 8, 16), ChineseTimeParser.parseDateRange("周七的安排", today))
    }

    // ---------- 绝对日 ----------

    @Test
    fun `月日格式的四种写法都解析成同一天`() {
        val expected = day(2026, 8, 20) to day(2026, 8, 20)
        assertEquals(expected, ChineseTimeParser.parseDateRange("8月20号有什么安排", today))
        assertEquals(expected, ChineseTimeParser.parseDateRange("8月20日有什么安排", today))
        assertEquals(expected, ChineseTimeParser.parseDateRange("8/20有什么安排", today))
    }

    @Test
    fun `仅日期号数取当月`() {
        assertEquals(day(2026, 8, 20) to day(2026, 8, 20), ChineseTimeParser.parseDateRange("20号有什么安排", today))
    }

    @Test
    fun `仅日期号数已过则顺延到下月`() {
        assertEquals(day(2026, 9, 5) to day(2026, 9, 5), ChineseTimeParser.parseDateRange("5号有什么安排", today))
    }

    // ---------- 区间 ----------

    @Test
    fun `这周和本周解析成本周一到本周日`() {
        val expected = day(2026, 8, 10) to day(2026, 8, 16)
        assertEquals(expected, ChineseTimeParser.parseDateRange("这周有什么安排", today))
        assertEquals(expected, ChineseTimeParser.parseDateRange("本周有什么安排", today))
    }

    @Test
    fun `下周解析成下周一到下周日`() {
        assertEquals(day(2026, 8, 17) to day(2026, 8, 23), ChineseTimeParser.parseDateRange("下周有什么安排", today))
    }

    @Test
    fun `接下来三天解析成今天起三天`() {
        assertEquals(day(2026, 8, 15) to day(2026, 8, 17), ChineseTimeParser.parseDateRange("接下来三天有什么安排", today))
    }

    @Test
    fun `接下来两天支持两字数字`() {
        assertEquals(day(2026, 8, 15) to day(2026, 8, 16), ChineseTimeParser.parseDateRange("接下来两天有什么安排", today))
    }

    @Test
    fun `这个月和本月解析成当月首尾两天`() {
        val expected = day(2026, 8, 1) to day(2026, 8, 31)
        assertEquals(expected, ChineseTimeParser.parseDateRange("这个月有什么安排", today))
        assertEquals(expected, ChineseTimeParser.parseDateRange("本月有什么安排", today))
    }

    @Test
    fun `无法解析出日期返回null`() {
        assertNull(ChineseTimeParser.parseDateRange("有什么安排", today))
    }

    // ---------- 时段词 ----------

    @Test
    fun `六个时段词解析成固定默认区间`() {
        assertEquals(listOf(TimeSpan(day(2026, 8, 15), 7 * 60, 9 * 60)), ChineseTimeParser.parseSpans("早上没空", today))
        assertEquals(listOf(TimeSpan(day(2026, 8, 15), 9 * 60, 12 * 60)), ChineseTimeParser.parseSpans("上午没空", today))
        assertEquals(listOf(TimeSpan(day(2026, 8, 15), 12 * 60, 14 * 60)), ChineseTimeParser.parseSpans("中午没空", today))
        assertEquals(listOf(TimeSpan(day(2026, 8, 15), 14 * 60, 18 * 60)), ChineseTimeParser.parseSpans("下午没空", today))
        assertEquals(listOf(TimeSpan(day(2026, 8, 15), 19 * 60, 22 * 60)), ChineseTimeParser.parseSpans("晚上没空", today))
        assertEquals(listOf(TimeSpan(day(2026, 8, 15), 8 * 60, 22 * 60)), ChineseTimeParser.parseSpans("全天没空", today))
    }

    // ---------- 点钟 ----------

    @Test
    fun `三点到五点解析成180到300分钟`() {
        val spans = ChineseTimeParser.parseSpans("三点到五点没空", today)
        assertEquals(listOf(TimeSpan(day(2026, 8, 15), 180, 300)), spans)
    }

    @Test
    fun `阿拉伯数字点钟解析结果一致`() {
        val spans = ChineseTimeParser.parseSpans("3点到5点没空", today)
        assertEquals(listOf(TimeSpan(day(2026, 8, 15), 180, 300)), spans)
    }

    @Test
    fun `二十四小时制点钟直接使用`() {
        val spans = ChineseTimeParser.parseSpans("15点到17点没空", today)
        assertEquals(listOf(TimeSpan(day(2026, 8, 15), 900, 1020)), spans)
    }

    @Test
    fun `三点半解析成三点三十分`() {
        val spans = ChineseTimeParser.parseSpans("三点半到五点没空", today)
        assertEquals(listOf(TimeSpan(day(2026, 8, 15), 210, 300)), spans)
    }

    @Test
    fun `冒号写法解析成对应分钟`() {
        val spans = ChineseTimeParser.parseSpans("3:30到5:00没空", today)
        assertEquals(listOf(TimeSpan(day(2026, 8, 15), 210, 300)), spans)
    }

    @Test
    fun `十五点三十解析成930分钟`() {
        val spans = ChineseTimeParser.parseSpans("十五点三十到十六点没空", today)
        assertEquals(listOf(TimeSpan(day(2026, 8, 15), 930, 960)), spans)
    }

    // ---------- 上下午修正 ----------

    @Test
    fun `下午三点到五点修正成900到1020分钟`() {
        val spans = ChineseTimeParser.parseSpans("明天下午三点到五点没空要去开会", today)
        assertEquals(listOf(TimeSpan(day(2026, 8, 16), 900, 1020)), spans)
    }

    @Test
    fun `晚上八点修正成加十二小时并顺延到区间终点`() {
        val spans = ChineseTimeParser.parseSpans("晚上八点到十点没空", today)
        assertEquals(listOf(TimeSpan(day(2026, 8, 15), 20 * 60, 22 * 60)), spans)
    }

    @Test
    fun `上午十点不做加十二小时修正`() {
        val spans = ChineseTimeParser.parseSpans("上午十点到十二点没空", today)
        assertEquals(listOf(TimeSpan(day(2026, 8, 15), 10 * 60, 12 * 60)), spans)
    }

    // ---------- 范围写法 ----------

    @Test
    fun `到至短横线三种连接词等价`() {
        val expected = listOf(TimeSpan(day(2026, 8, 15), 180, 300))
        assertEquals(expected, ChineseTimeParser.parseSpans("三点到五点没空", today))
        assertEquals(expected, ChineseTimeParser.parseSpans("三点至五点没空", today))
        assertEquals(expected, ChineseTimeParser.parseSpans("三点-五点没空", today))
    }

    @Test
    fun `从X到Y写法忽略从字`() {
        val spans = ChineseTimeParser.parseSpans("从三点到五点没空", today)
        assertEquals(listOf(TimeSpan(day(2026, 8, 15), 180, 300)), spans)
    }

    // ---------- 相对时长 ----------

    @Test
    fun `接下来两小时以当前分钟为起点`() {
        val spans = ChineseTimeParser.parseSpans("接下来两小时没空", today, nowMinute = 10 * 60)
        assertEquals(listOf(TimeSpan(day(2026, 8, 15), 10 * 60, 12 * 60)), spans)
    }

    @Test
    fun `未来一小时以当前分钟为起点`() {
        val spans = ChineseTimeParser.parseSpans("未来一小时没空", today, nowMinute = 9 * 60)
        assertEquals(listOf(TimeSpan(day(2026, 8, 15), 9 * 60, 10 * 60)), spans)
    }

    @Test
    fun `未注入nowMinute时相对时长无法解析`() {
        val spans = ChineseTimeParser.parseSpans("接下来两小时没空", today)
        assertTrue(spans.isEmpty())
    }
}
