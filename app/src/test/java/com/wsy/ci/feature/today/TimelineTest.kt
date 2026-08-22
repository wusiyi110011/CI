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

package com.wsy.ci.feature.today

import com.wsy.ci.core.db.TaskEntity
import com.wsy.ci.core.db.QuestEntity
import com.wsy.ci.core.db.QuestType
import com.wsy.ci.core.db.SessionEntity
import com.wsy.ci.core.db.TaskStatus
import com.wsy.ci.core.timeline.TaskSegment
import com.wsy.ci.core.util.TimeFormat
import org.junit.Assert.assertEquals
import org.junit.Test

class TimelineTest {

    @Test
    fun `相邻任务不算时间冲突`() {
        val segments = listOf(
            segment(60, 90),
            segment(90, 120),
        )

        assertEquals(0, timelineConflictCount(segments))
    }

    @Test
    fun `重叠任务分别被标记为冲突`() {
        val segments = listOf(
            segment(60, 120),
            segment(90, 150),
            segment(180, 210),
        )

        assertEquals(2, timelineConflictCount(segments))
    }

    @Test
    fun `周视图不同天的相同时段不算冲突`() {
        // 回归：整周拍平去数会把每天 9:00–10:00 误判成重叠，数量虚高到任务数
        val monday = 20_000L
        val tasks = (0..6).map { offset ->
            task("周$offset", monday + offset, 540, 600)
        }

        assertEquals(0, weekConflictCount(tasks, monday))
    }

    @Test
    fun `周视图同一天的重叠仍然计数`() {
        val monday = 20_000L
        val tasks = listOf(
            task("甲", monday, 540, 600),
            task("乙", monday, 550, 610),
            task("丙", monday + 1, 540, 600),
        )

        assertEquals(2, weekConflictCount(tasks, monday))
    }

    @Test
    fun `跨零点延续段按画出的那一天参与冲突判定`() {
        val monday = 20_000L
        val tasks = listOf(
            // 周日 23:00–次日 1:00，在周一列画出 0:00–1:00 的延续段
            task("夜车", monday - 1, 23 * 60, 25 * 60),
            task("早读", monday, 30, 90),
        )

        assertEquals(2, weekConflictCount(tasks, monday))
    }

    @Test
    fun `已完成或已跳过的块不算时间冲突`() {
        // 真实场景：跳过 19:30 的课、改做 20:12 的另一门，历史翻篇了不该再报重叠
        val segments = listOf(
            segment(19 * 60 + 30, 20 * 60 + 40, TaskStatus.SKIPPED),
            segment(20 * 60 + 12, 20 * 60 + 52, TaskStatus.DONE),
        )

        assertEquals(0, timelineConflictCount(segments))
    }

    @Test
    fun `进行中的块仍参与冲突判定`() {
        val segments = listOf(
            segment(19 * 60 + 30, 20 * 60 + 40, TaskStatus.RUNNING),
            segment(20 * 60 + 12, 20 * 60 + 52),
        )

        assertEquals(2, timelineConflictCount(segments))
    }

    @Test
    fun `实际专注没有具体任务时显示任务线名称`() {
        val epochDay = 20_000L
        val startAt = TimeFormat.dayStartMillis(epochDay) + 9 * 60 * 60_000L
        val blocks = sessionsToBlocks(
            sessions = listOf(
                SessionEntity(
                    id = 1L,
                    questId = 7L,
                    startAt = startAt,
                    endAt = startAt + 25 * 60_000L,
                ),
            ),
            tasks = emptyList(),
            nowMillis = startAt + 25 * 60_000L,
            epochDay = epochDay,
            quests = listOf(
                QuestEntity(id = 7L, type = QuestType.SIDE, title = "每日英语"),
            ),
        )

        assertEquals("每日英语", blocks.single().title)
    }

    private fun segment(start: Int, end: Int, status: TaskStatus = TaskStatus.PLANNED): TaskSegment =
        TaskSegment(
            task = TaskEntity(
                title = "测试任务",
                epochDay = 20_000L,
                startMinute = start,
                endMinute = end,
                status = status,
            ),
            startMinute = start,
            endMinute = end,
            isContinuation = false,
        )

    private fun task(title: String, epochDay: Long, start: Int, end: Int): TaskEntity =
        TaskEntity(
            title = title,
            epochDay = epochDay,
            startMinute = start,
            endMinute = end,
        )
}
