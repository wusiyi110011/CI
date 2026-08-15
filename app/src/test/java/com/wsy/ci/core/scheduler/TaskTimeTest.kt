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

package com.wsy.ci.core.scheduler

import com.wsy.ci.core.db.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskTimeTest {

    private val task = TaskEntity(
        id = 7,
        title = "入门认知与剪辑全流程实操",
        epochDay = 20_664,
        startMinute = 19 * 60,
        endMinute = 20 * 60,
    )

    @Test
    fun `跨天提前开工时日期和起点都换成此刻且时长不变`() {
        val aligned = alignedToNow(task, nowEpochDay = 20_663, nowMinute = 18 * 60 + 20)

        assertEquals(20_663, aligned.epochDay)
        assertEquals(18 * 60 + 20, aligned.startMinute)
        assertEquals(19 * 60 + 20, aligned.endMinute)
    }

    @Test
    fun `其余字段原样保留`() {
        val aligned = alignedToNow(task, nowEpochDay = 20_663, nowMinute = 600)

        assertEquals(task.id, aligned.id)
        assertEquals(task.title, aligned.title)
        assertEquals(task.difficulty, aligned.difficulty)
        assertEquals(task.status, aligned.status)
    }

    @Test
    fun `深夜开工时结束时间越过零点而不是截在当天末尾`() {
        val aligned = alignedToNow(task, nowEpochDay = 20_663, nowMinute = 23 * 60 + 30)

        assertEquals(23 * 60 + 30, aligned.startMinute)
        assertEquals(24 * 60 + 30, aligned.endMinute)
    }

    @Test
    fun `结束计时把终点换成收工时刻`() {
        val running = task.copy(startMinute = 19 * 60, endMinute = 20 * 60)
        val ended = endedAt(running, endEpochDay = task.epochDay, endMinute = 19 * 60 + 25)

        assertEquals(19 * 60, ended.startMinute)
        assertEquals(19 * 60 + 25, ended.endMinute)
    }

    @Test
    fun `跨零点收工时终点记成次日的分钟数`() {
        val running = task.copy(startMinute = 23 * 60, endMinute = 24 * 60)
        val ended = endedAt(running, endEpochDay = task.epochDay + 1, endMinute = 2 * 60)

        assertEquals(26 * 60, ended.endMinute)
    }

    @Test
    fun `秒开秒关也不产出空区间`() {
        val running = task.copy(startMinute = 19 * 60, endMinute = 20 * 60)
        val ended = endedAt(running, endEpochDay = task.epochDay, endMinute = 19 * 60)

        assertTrue(ended.endMinute > ended.startMinute)
    }
}
