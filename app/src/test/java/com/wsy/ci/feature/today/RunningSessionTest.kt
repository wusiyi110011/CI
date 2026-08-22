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

import com.wsy.ci.core.db.SessionEntity
import com.wsy.ci.core.db.TaskEntity
import com.wsy.ci.core.db.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RunningSessionTest {

    @Test
    fun `实际记录已结束时不再展示旧的计时快照`() {
        val staleOpen = session(id = 7)
        val completed = staleOpen.copy(endAt = 20_000)

        assertNull(reconcileRunningSession(staleOpen, listOf(completed)))
    }

    @Test
    fun `范围查询先看到新计时时立即展示`() {
        val staleOpen = session(id = 7)
        val newOpen = session(id = 8)

        assertEquals(newOpen, reconcileRunningSession(staleOpen, listOf(newOpen)))
    }

    @Test
    fun `跨范围的计时仍使用专用查询结果`() {
        val open = session(id = 7)

        assertEquals(open, reconcileRunningSession(open, emptyList()))
    }

    @Test
    fun `任务流滞后时先投影刚提交的完成状态`() {
        val running = TaskEntity(
            id = 11,
            title = "练字",
            epochDay = 20_000,
            startMinute = 600,
            endMinute = 660,
            status = TaskStatus.RUNNING,
        )

        val projected = applyTaskStopProjection(
            tasks = listOf(running),
            projection = TaskStopProjection(
                taskId = running.id,
                status = TaskStatus.DONE,
                endMinute = 625,
            ),
        ).single()

        assertEquals(TaskStatus.DONE, projected.status)
        assertEquals(625, projected.endMinute)
    }

    private fun session(id: Long) = SessionEntity(id = id, startAt = 10_000)
}
