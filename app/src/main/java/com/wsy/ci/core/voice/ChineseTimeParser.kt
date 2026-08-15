/*
 * Copyright 2026 吴思毅
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.wsy.ci.core.voice

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * 中文口语时间表达式解析成 [TimeSpan] 或日期区间。`today` / `nowMinute` 一律作为参数注入，
 * 函数内部绝不调 `LocalDate.now()`，保证可重现测试。
 */
object ChineseTimeParser {

    /** 解析出一天内的若干时间段（配合 [parseIntentDay] 得到的日期）。 */
    fun parseSpans(text: String, today: LocalDate, nowMinute: Int? = null): List<TimeSpan> {
        val epochDay = resolveDay(text, today) ?: today.toEpochDay()
        return resolveTimeRanges(text, nowMinute).map { (start, end) -> TimeSpan(epochDay, start, end) }
    }

    /** 解析出查询用的日期区间 `[from, to]`（含两端）。 */
    fun parseDateRange(text: String, today: LocalDate): Pair<Long, Long>? {
        if (THIS_WEEK_REGEX.containsMatchIn(text)) {
            val monday = mondayOf(today)
            return monday.toEpochDay() to monday.plusDays(6).toEpochDay()
        }
        if (NEXT_WEEK_REGEX.containsMatchIn(text)) {
            val monday = mondayOf(today).plusWeeks(1)
            return monday.toEpochDay() to monday.plusDays(6).toEpochDay()
        }
        NEXT_N_DAYS_REGEX.find(text)?.let { m ->
            val n = chineseDigitsToInt(m.groupValues[1])
            if (n != null && n > 0) {
                return today.toEpochDay() to today.plusDays((n - 1).toLong()).toEpochDay()
            }
        }
        if (text.contains("这个月") || text.contains("本月")) {
            val first = today.withDayOfMonth(1)
            val last = today.withDayOfMonth(today.lengthOfMonth())
            return first.toEpochDay() to last.toEpochDay()
        }
        val day = resolveDay(text, today) ?: return null
        return day to day
    }

    // ---------- 日期解析 ----------

    private fun resolveDay(text: String, today: LocalDate): Long? {
        ABSOLUTE_MD_REGEX.find(text)?.let { m ->
            resolveAbsoluteMonthDay(m, today)?.let { return it }
        }
        when {
            text.contains("大后天") -> return today.plusDays(3).toEpochDay()
            text.contains("后天") -> return today.plusDays(2).toEpochDay()
            text.contains("明天") || text.contains("明儿") -> return today.plusDays(1).toEpochDay()
            text.contains("昨天") -> return today.minusDays(1).toEpochDay()
            text.contains("今天") || text.contains("今儿") -> return today.toEpochDay()
        }
        WEEK_REGEX.find(text)?.let { m ->
            resolveWeekday(m, today)?.let { return it }
        }
        ABSOLUTE_D_REGEX.find(text)?.let { m ->
            resolveAbsoluteDayOfMonth(m, today)?.let { return it }
        }
        return null
    }

    private fun resolveAbsoluteMonthDay(m: MatchResult, today: LocalDate): Long? {
        val month = m.groupValues[1].toIntOrNull() ?: return null
        val day = m.groupValues[2].toIntOrNull() ?: return null
        if (month !in 1..12 || day !in 1..31) return null
        var date = runCatching { LocalDate.of(today.year, month, day) }.getOrNull() ?: return null
        if (date.isBefore(today)) date = date.plusYears(1)
        return date.toEpochDay()
    }

    private fun resolveAbsoluteDayOfMonth(m: MatchResult, today: LocalDate): Long? {
        val day = m.groupValues[1].toIntOrNull() ?: return null
        if (day !in 1..31) return null
        var date = runCatching { LocalDate.of(today.year, today.monthValue, day) }.getOrNull() ?: return null
        if (date.isBefore(today)) date = date.plusMonths(1)
        return date.toEpochDay()
    }

    private fun resolveWeekday(m: MatchResult, today: LocalDate): Long? {
        val qualifier = m.groupValues[1]
        val weekdayChar = m.groupValues[3].firstOrNull() ?: return null
        val targetDow = WEEKDAY_CHARS[weekdayChar] ?: return null
        val monday = mondayOf(today)
        return when (qualifier) {
            "这", "本" -> monday.plusDays((targetDow.value - 1).toLong()).toEpochDay()
            "下" -> monday.plusWeeks(1).plusDays((targetDow.value - 1).toLong()).toEpochDay()
            else -> {
                var d = today
                while (d.dayOfWeek != targetDow) d = d.plusDays(1)
                d.toEpochDay()
            }
        }
    }

    private fun mondayOf(date: LocalDate): LocalDate = date.minusDays((date.dayOfWeek.value - 1).toLong())

    // ---------- 时间段解析 ----------

    private fun resolveTimeRanges(text: String, nowMinute: Int?): List<Pair<Int, Int>> {
        val paired = pairRanges(findClockMatches(text), text)
        if (paired.isNotEmpty()) return paired

        DEFAULT_RANGES.entries.firstOrNull { text.contains(it.key) }?.let { return listOf(it.value) }

        val durationMatch = DURATION_REGEX.find(text)
        if (durationMatch != null && nowMinute != null) {
            val hours = chineseDigitsToInt(durationMatch.groupValues[2])
            if (hours != null && hours > 0) {
                val end = (nowMinute + hours * 60).coerceAtMost(23 * 60 + 59)
                return listOf(nowMinute to end)
            }
        }
        return emptyList()
    }

    private data class ClockMatch(val range: IntRange, val hour: Int, val minute: Int, val period: String?)

    private fun findClockMatches(text: String): List<ClockMatch> {
        val results = mutableListOf<ClockMatch>()
        for (m in COLON_TIME_REGEX.findAll(text)) {
            val hour = m.groupValues[1].toIntOrNull() ?: continue
            val minute = m.groupValues[2].toIntOrNull() ?: continue
            if (hour !in 0..23) continue
            results += ClockMatch(m.range, hour, minute, findPrecedingPeriod(text, m.range.first))
        }
        for (m in DIAN_TIME_REGEX.findAll(text)) {
            val hour = chineseDigitsToInt(m.groupValues[1]) ?: continue
            if (hour !in 0..23) continue
            val minute = when {
                m.groupValues[2] == "半" -> 30
                m.groupValues[3].isNotEmpty() -> chineseDigitsToInt(m.groupValues[3]) ?: 0
                else -> 0
            }
            results += ClockMatch(m.range, hour, minute, findPrecedingPeriod(text, m.range.first))
        }
        return results.sortedBy { it.range.first }
    }

    private fun findPrecedingPeriod(text: String, index: Int): String? {
        val windowStart = (index - 3).coerceAtLeast(0)
        val window = text.substring(windowStart, index)
        return PERIOD_WORDS.firstOrNull { window.endsWith(it) }
    }

    private fun pairRanges(matches: List<ClockMatch>, text: String): List<Pair<Int, Int>> {
        val pairs = mutableListOf<Pair<Int, Int>>()
        var i = 0
        while (i < matches.size - 1) {
            val a = matches[i]
            val b = matches[i + 1]
            val betweenRaw = text.substring(a.range.last + 1, b.range.first)
            val between = b.period?.takeIf { betweenRaw.endsWith(it) }
                ?.let { betweenRaw.dropLast(it.length) } ?: betweenRaw
            if (between !in CONNECTORS) {
                i++
                continue
            }
            val inherited = a.period ?: b.period
            pairs += applyPeriod(a.period ?: inherited, a.hour, a.minute) to
                applyPeriod(b.period ?: inherited, b.hour, b.minute)
            i += 2
        }
        return pairs
    }

    private fun applyPeriod(period: String?, hour: Int, minute: Int): Int {
        val h = when (period) {
            "下午", "晚上" -> if (hour in 1..11) hour + 12 else hour
            "中午" -> if (hour in 1..11) hour + 12 else hour
            else -> hour
        }.coerceIn(0, 23)
        return h * 60 + minute
    }

    // ---------- 中文数字 ----------

    private val CHINESE_DIGITS = mapOf(
        '零' to 0, '一' to 1, '二' to 2, '两' to 2, '三' to 3, '四' to 4,
        '五' to 5, '六' to 6, '七' to 7, '八' to 8, '九' to 9,
    )

    /** 支持 0~99：纯阿拉伯数字、单个汉字数字、「十」「十X」「X十」「X十Y」。 */
    private fun chineseDigitsToInt(raw: String): Int? {
        val s = raw.trim()
        if (s.isEmpty()) return null
        s.toIntOrNull()?.let { return it }
        if (s == "十") return 10
        if (s.length == 1) return CHINESE_DIGITS[s[0]]
        if (s.length == 2 && s[0] == '十') return CHINESE_DIGITS[s[1]]?.let { 10 + it }
        if (s.length == 2 && s[1] == '十') return CHINESE_DIGITS[s[0]]?.let { it * 10 }
        if (s.length == 3 && s[1] == '十') {
            val tens = CHINESE_DIGITS[s[0]] ?: return null
            val ones = CHINESE_DIGITS[s[2]] ?: return null
            return tens * 10 + ones
        }
        return null
    }

    private val WEEKDAY_CHARS = mapOf(
        '一' to DayOfWeek.MONDAY, '二' to DayOfWeek.TUESDAY, '三' to DayOfWeek.WEDNESDAY,
        '四' to DayOfWeek.THURSDAY, '五' to DayOfWeek.FRIDAY, '六' to DayOfWeek.SATURDAY,
        '日' to DayOfWeek.SUNDAY, '天' to DayOfWeek.SUNDAY, '七' to DayOfWeek.SUNDAY,
    )

    private val PERIOD_WORDS = listOf("早上", "上午", "中午", "下午", "晚上")
    private val CONNECTORS = listOf("到", "至", "-", "~", "－")

    /** 与 `LlmService.parseBlockers` 的 prompt 保持一致，另加「早上」「中午」两个更细粒度的时段词。 */
    private val DEFAULT_RANGES = mapOf(
        "早上" to (7 * 60 to 9 * 60),
        "上午" to (9 * 60 to 12 * 60),
        "中午" to (12 * 60 to 14 * 60),
        "下午" to (14 * 60 to 18 * 60),
        "晚上" to (19 * 60 to 22 * 60),
        "全天" to (8 * 60 to 22 * 60),
    )

    private val ABSOLUTE_MD_REGEX = Regex("""(\d{1,2})[月/](\d{1,2})[日号]?""")
    private val ABSOLUTE_D_REGEX = Regex("""(\d{1,2})[日号]""")
    private val WEEK_REGEX = Regex("""(这|本|下)?(周|星期|礼拜)([一二三四五六日天七])""")
    private val COLON_TIME_REGEX = Regex("""([0-2]?\d):([0-5]\d)""")
    private val DIAN_TIME_REGEX = Regex("""([\d一二三四五六七八九十两]{1,3})点(半|([\d一二三四五六七八九十两]{1,2})分?)?""")
    private val DURATION_REGEX = Regex("""(接下来|未来)([\d一二三四五六七八九十两]{1,3})(个)?小时""")
    private val THIS_WEEK_REGEX = Regex("""(这|本)周(?![一二三四五六日天七])""")
    private val NEXT_WEEK_REGEX = Regex("""下周(?![一二三四五六日天七])""")
    private val NEXT_N_DAYS_REGEX = Regex("""接下来([\d一二三四五六七八九十两]{1,3})天""")
}
