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

    private fun segment(start: Int, end: Int): TaskSegment = TaskSegment(
        task = TaskEntity(
            title = "测试任务",
            epochDay = 20_000L,
            startMinute = start,
            endMinute = end,
        ),
        startMinute = start,
        endMinute = end,
        isContinuation = false,
    )
}
