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

package com.wsy.ci.core.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewDigestTest {

    private fun snapshot(
        totalMinutes: Int = 0,
        planned: Int = 0,
        done: Int = 0,
        skipped: Int = 0,
        domains: List<Pair<String, Int>> = emptyList(),
        topHours: List<Int> = emptyList(),
        earnedCi: Long = 0,
        spentCi: Long = 0,
    ) = ReviewPeriodSnapshot(
        fromDay = 20_000,
        toDay = 20_006,
        totalMinutes = totalMinutes,
        plannedCount = planned,
        doneCount = done,
        skippedCount = skipped,
        minutesByDomain = domains,
        topHours = topHours,
        earnedCi = earnedCi,
        spentCi = spentCi,
    )

    @Test
    fun `环比正负号与百分比`() {
        val digest = ReviewDigest.build(
            granularityLabel = "本周 vs 上周",
            todayEpochDay = 20_006,
            current = snapshot(totalMinutes = 320),
            previous = snapshot(totalMinutes = 450),
            mainQuests = emptyList(),
            sideQuests = emptyList(),
        )
        assertTrue(digest.contains("本期 320分钟 vs 上期 450分钟（环比-28.9%）"))
    }

    @Test
    fun `上期增长带加号`() {
        val digest = ReviewDigest.build(
            granularityLabel = "本周 vs 上周",
            todayEpochDay = 20_006,
            current = snapshot(totalMinutes = 500),
            previous = snapshot(totalMinutes = 400),
            mainQuests = emptyList(),
            sideQuests = emptyList(),
        )
        assertTrue(digest.contains("环比+25.0%"))
    }

    @Test
    fun `没有上期时只报本期不提环比`() {
        val digest = ReviewDigest.build(
            granularityLabel = "本月 vs 上月",
            todayEpochDay = 20_006,
            current = snapshot(totalMinutes = 120, domains = listOf("英语" to 120)),
            previous = null,
            mainQuests = emptyList(),
            sideQuests = emptyList(),
        )
        assertTrue(digest.contains("本期 120分钟"))
        assertFalse(digest.contains("环比"))
        assertFalse(digest.contains("上期"))
    }

    @Test
    fun `上期零基线时不做除法`() {
        val digest = ReviewDigest.build(
            granularityLabel = "本周 vs 上周",
            todayEpochDay = 20_006,
            current = snapshot(totalMinutes = 90),
            previous = snapshot(totalMinutes = 0),
            mainQuests = emptyList(),
            sideQuests = emptyList(),
        )
        assertTrue(digest.contains("上期无记录"))
    }

    @Test
    fun `领域按名字对齐两期`() {
        val digest = ReviewDigest.build(
            granularityLabel = "本周 vs 上周",
            todayEpochDay = 20_006,
            current = snapshot(domains = listOf("英语" to 120, "数学" to 60)),
            previous = snapshot(domains = listOf("英语" to 200)),
            mainQuests = emptyList(),
            sideQuests = emptyList(),
        )
        assertTrue(digest.contains("英语 120分钟(上期200)"))
        // 上期没学过的领域单独标注，而不是错拿别的领域的数字
        assertTrue(digest.contains("数学 60分钟(上期无记录)"))
    }

    @Test
    fun `完成率与CI经济都带上期对照`() {
        val digest = ReviewDigest.build(
            granularityLabel = "本周 vs 上周",
            todayEpochDay = 20_006,
            current = snapshot(planned = 12, done = 8, skipped = 2, earnedCi = 560, spentCi = 200),
            previous = snapshot(planned = 10, done = 10, skipped = 0, earnedCi = 700, spentCi = 0),
            mainQuests = emptyList(),
            sideQuests = emptyList(),
        )
        assertTrue(digest.contains("计划12 完成8 跳过2（完成率66%）"))
        assertTrue(digest.contains("计划10 完成10 跳过0（完成率100%）"))
        assertTrue(digest.contains("入560 出200 净存360"))
        assertTrue(digest.contains("入700 出0 净存700"))
    }

    @Test
    fun `主线倒计时未过期与已过期措辞不同`() {
        val digest = ReviewDigest.build(
            granularityLabel = "本周 vs 上周",
            todayEpochDay = 20_300,
            current = snapshot(),
            previous = null,
            mainQuests = listOf(
                ReviewMainQuest("雅思冲刺", deadlineEpochDay = 20_340, chapterCount = 9, periodMinutes = 120),
                ReviewMainQuest("旧目标", deadlineEpochDay = 20_290, chapterCount = null, periodMinutes = 0),
            ),
            sideQuests = emptyList(),
        )
        assertTrue(digest.contains("雅思冲刺，截止2025-09-09（还剩40天），共9 个章节，本期投入120分钟"))
        assertTrue(digest.contains("旧目标，截止2025-07-21（已过期10天），本期投入0分钟"))
    }

    @Test
    fun `支线连击区分今天打过与断签`() {
        val digest = ReviewDigest.build(
            granularityLabel = "本周 vs 上周",
            todayEpochDay = 20_300,
            current = snapshot(),
            previous = null,
            mainQuests = emptyList(),
            sideQuests = listOf(
                ReviewSideQuest("背单词", streakDays = 12, bestStreak = 20, lastDoneEpochDay = 20_300, periodFocusCount = 6),
                ReviewSideQuest("晨跑", streakDays = 0, bestStreak = 3, lastDoneEpochDay = 20_296, periodFocusCount = 1),
                ReviewSideQuest("冥想", streakDays = 0, bestStreak = 0, lastDoneEpochDay = null, periodFocusCount = 0),
            ),
        )
        assertTrue(digest.contains("背单词：当前连击12天（最佳20），今天打过卡，本期专注6次"))
        assertTrue(digest.contains("晨跑：当前连击0天（最佳3），最近一次打卡距今4天，本期专注1次"))
        assertTrue(digest.contains("冥想：当前连击0天（最佳0），从未打卡，本期专注0次"))
    }

    @Test
    fun `章节JSON只取数量且坏数据兜底为null`() {
        assertEquals(3, ReviewDigest.chapterCount("""[{"title":"a","hours":8},{"title":"b","hours":4},{"title":"c","hours":2}]"""))
        assertNull(ReviewDigest.chapterCount(null))
        assertNull(ReviewDigest.chapterCount(""))
        assertNull(ReviewDigest.chapterCount("不是JSON"))
    }
}
