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
import com.wsy.ci.core.db.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SchedulerTest {

    private fun task(
        id: Long,
        start: Int,
        end: Int,
        locked: Boolean = false,
        status: TaskStatus = TaskStatus.PLANNED,
        questId: Long? = null,
    ) = TaskEntity(
        id = id, title = "t$id", epochDay = 0,
        startMinute = start, endMinute = end,
        locked = locked, status = status, questId = questId,
    )

    private val window = Slot(8 * 60, 22 * 60)

    @Test
    fun `无冲突时保持原时间`() {
        val tasks = listOf(task(1, 9 * 60, 10 * 60), task(2, 14 * 60, 15 * 60))
        val result = Scheduler.reschedule(tasks, emptyList(), window)
        assertEquals(9 * 60, result.tasks.first { it.id == 1L }.startMinute)
        assertEquals(14 * 60, result.tasks.first { it.id == 2L }.startMinute)
        assertTrue(result.unplaced.isEmpty())
    }

    @Test
    fun `blocker占用后任务顺延到最近空档`() {
        val tasks = listOf(task(1, 14 * 60, 16 * 60))
        val blockers = listOf(Slot(14 * 60, 17 * 60))
        val result = Scheduler.reschedule(tasks, blockers, window)
        val moved = result.tasks.first { it.id == 1L }
        assertEquals(8 * 60, moved.startMinute)
        assertEquals(10 * 60, moved.endMinute)
    }

    @Test
    fun `锁定块不动其他任务绕开`() {
        val tasks = listOf(
            task(1, 9 * 60, 10 * 60, locked = true),
            task(2, 9 * 60, 11 * 60),
        )
        val result = Scheduler.reschedule(tasks, emptyList(), window)
        assertEquals(9 * 60, result.tasks.first { it.id == 1L }.startMinute)
        // t2(120分钟) 与锁定块冲突，8:00 前空档只有 60 分钟塞不下 → 顺延到 10:00
        val t2 = result.tasks.first { it.id == 2L }
        assertEquals(10 * 60, t2.startMinute)
        assertEquals(12 * 60, t2.endMinute)
    }

    @Test
    fun `已完成任务不参与重排`() {
        val tasks = listOf(
            task(1, 9 * 60, 10 * 60, status = TaskStatus.DONE),
            task(2, 9 * 60 + 30, 10 * 60 + 30),
        )
        val result = Scheduler.reschedule(tasks, emptyList(), window)
        assertEquals(9 * 60, result.tasks.first { it.id == 1L }.startMinute)
        // t2 与已完成块冲突，挪到最早空档 8:00
        assertEquals(8 * 60, result.tasks.first { it.id == 2L }.startMinute)
    }

    @Test
    fun `nowMinute之前的时间不可用`() {
        val tasks = listOf(task(1, 8 * 60, 9 * 60))
        val result = Scheduler.reschedule(tasks, emptyList(), window, nowMinute = 12 * 60)
        assertEquals(12 * 60, result.tasks.first { it.id == 1L }.startMinute)
    }

    @Test
    fun `日窗口结束后一分钟任务也不能排进过去`() {
        val tasks = listOf(task(1, window.endMinute - 1, window.endMinute))

        val result = Scheduler.reschedule(
            tasks = tasks,
            blockers = emptyList(),
            dayWindow = window,
            nowMinute = window.endMinute,
        )

        assertEquals(listOf(1L), result.unplaced.map { it.id })
    }

    @Test
    fun `原时间无冲突的任务不被挪动即便优先级低`() {
        val tasks = listOf(
            task(1, 9 * 60, 12 * 60, questId = 100),
            task(2, 10 * 60, 13 * 60, questId = 200),
        )
        val deadlines = mapOf(100L to 30L, 200L to 10L)
        val result = Scheduler.reschedule(
            tasks, listOf(Slot(8 * 60, 9 * 60)), window,
            deadlineByQuestId = deadlines,
        )
        // t2 截止更近先安置：原时间 10:00 空着 → 不动；t1 与 t2 重叠 → 顺延到 13:00
        val t2 = result.tasks.first { it.id == 2L }
        assertEquals(10 * 60, t2.startMinute)
        val t1 = result.tasks.first { it.id == 1L }
        assertEquals(13 * 60, t1.startMinute)
    }

    @Test
    fun `全天占满塞不下进unplaced且保留原时间`() {
        val tasks = listOf(task(1, 9 * 60, 10 * 60))
        val blockers = listOf(Slot(window.startMinute, window.endMinute))
        val result = Scheduler.reschedule(tasks, blockers, window)
        assertEquals(1, result.unplaced.size)
        assertEquals(9 * 60, result.tasks.first { it.id == 1L }.startMinute)
    }

    @Test
    fun `movedFrom只报告被移动的任务`() {
        val original = listOf(task(1, 14 * 60, 16 * 60), task(2, 18 * 60, 19 * 60))
        val result = Scheduler.reschedule(original, listOf(Slot(14 * 60, 17 * 60)), window)
        val moved = result.movedFrom(original)
        assertEquals(setOf(1L), moved.keys)
        assertEquals(14 * 60 to 8 * 60, moved[1L])
    }

    @Test
    fun `freeSlots正确切割窗口`() {
        val free = Scheduler.freeSlots(
            Slot(480, 1320),
            listOf(Slot(540, 600), Slot(900, 960)),
        )
        assertEquals(listOf(Slot(480, 540), Slot(600, 900), Slot(960, 1320)), free)
    }

    @Test
    fun `重叠占用合并处理`() {
        val free = Scheduler.freeSlots(
            Slot(480, 720),
            listOf(Slot(500, 600), Slot(550, 650)),
        )
        assertEquals(listOf(Slot(480, 500), Slot(650, 720)), free)
    }
}
