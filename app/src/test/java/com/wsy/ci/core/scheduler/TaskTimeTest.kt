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
    fun `深夜开工时结束时间截到当天末尾`() {
        val aligned = alignedToNow(task, nowEpochDay = 20_663, nowMinute = 23 * 60 + 30)

        assertEquals(23 * 60 + 30, aligned.startMinute)
        assertEquals(24 * 60 - 1, aligned.endMinute)
    }

    @Test
    fun `起点已是当天最后一分钟也不产出空区间`() {
        val aligned = alignedToNow(task, nowEpochDay = 20_663, nowMinute = 24 * 60 - 1)

        assertTrue(aligned.endMinute > aligned.startMinute)
    }
}
