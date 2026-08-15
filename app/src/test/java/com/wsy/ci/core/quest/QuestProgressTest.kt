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

package com.wsy.ci.core.quest

import com.wsy.ci.core.db.TaskEntity
import com.wsy.ci.core.db.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class QuestProgressTest {

    private fun task(
        status: TaskStatus,
        startMinute: Int = 19 * 60,
        endMinute: Int = 20 * 60,
    ) = TaskEntity(
        title = "任务",
        epochDay = 20_300,
        startMinute = startMinute,
        endMinute = endMinute,
        status = status,
    )

    @Test
    fun `空任务列表得到全零且比例为零`() {
        val progress = QuestProgress.of(emptyList())

        assertEquals(0, progress.total)
        assertEquals(0, progress.pending)
        assertEquals(0f, progress.ratio, 0f)
    }

    @Test
    fun `四态任务分别计数`() {
        val tasks = listOf(
            task(TaskStatus.DONE),
            task(TaskStatus.DONE),
            task(TaskStatus.SKIPPED),
            task(TaskStatus.RUNNING),
            task(TaskStatus.PLANNED),
        )

        val progress = QuestProgress.of(tasks)

        assertEquals(5, progress.total)
        assertEquals(2, progress.done)
        assertEquals(1, progress.skipped)
        assertEquals(1, progress.running)
        assertEquals(1, progress.pending)
        assertEquals(0.6f, progress.ratio, 1e-6f)
    }

    @Test
    fun `时长只把已完成任务计入doneMinutes`() {
        val tasks = listOf(
            task(TaskStatus.DONE, 19 * 60, 20 * 60),
            task(TaskStatus.PLANNED, 14 * 60, 16 * 60 + 30),
        )

        val progress = QuestProgress.of(tasks)

        assertEquals(60 + 150, progress.plannedMinutes)
        assertEquals(60, progress.doneMinutes)
    }
}
