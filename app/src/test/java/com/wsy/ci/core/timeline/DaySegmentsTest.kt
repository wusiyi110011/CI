package com.wsy.ci.core.timeline

import com.wsy.ci.core.db.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DaySegmentsTest {

    private val day = 20_664L

    private fun task(start: Int, end: Int, epochDay: Long = day) = TaskEntity(
        id = 1, title = "剪辑实操", epochDay = epochDay,
        startMinute = start, endMinute = end,
    )

    @Test
    fun `当天内的任务原样落在当天`() {
        val slice = DaySegments.project(day, 19 * 60, 20 * 60, onEpochDay = day)!!
        assertEquals(19 * 60, slice.startMinute)
        assertEquals(20 * 60, slice.endMinute)
        assertFalse(slice.isContinuation)
    }

    @Test
    fun `跨天任务在起始日只画到当天末尾`() {
        val slice = DaySegments.project(day, 23 * 60, 26 * 60, onEpochDay = day)!!
        assertEquals(23 * 60, slice.startMinute)
        assertEquals(24 * 60, slice.endMinute)
        assertFalse(slice.isContinuation)
    }

    @Test
    fun `跨天任务在次日从零点起画且标记为延续`() {
        val slice = DaySegments.project(day, 23 * 60, 26 * 60, onEpochDay = day + 1)!!
        assertEquals(0, slice.startMinute)
        assertEquals(2 * 60, slice.endMinute)
        assertTrue(slice.isContinuation)
    }

    @Test
    fun `与目标日不相交时没有片段`() {
        assertNull(DaySegments.project(day, 23 * 60, 26 * 60, onEpochDay = day + 2))
        assertNull(DaySegments.project(day, 9 * 60, 10 * 60, onEpochDay = day + 1))
        assertNull(DaySegments.project(day, 9 * 60, 10 * 60, onEpochDay = day - 1))
    }

    @Test
    fun `空区间不产出片段`() {
        assertNull(DaySegments.project(day, 10 * 60, 10 * 60, onEpochDay = day))
    }

    @Test
    fun `按天取片段时前一天的尾巴也算进来并按起点排序`() {
        val tasks = listOf(
            task(9 * 60, 10 * 60),
            task(23 * 60, 26 * 60, epochDay = day - 1),
        )
        val segments = DaySegments.tasksOn(tasks, day)

        assertEquals(2, segments.size)
        assertEquals(0, segments[0].startMinute)
        assertEquals(2 * 60, segments[0].endMinute)
        assertTrue(segments[0].isContinuation)
        assertEquals(9 * 60, segments[1].startMinute)
        assertFalse(segments[1].isContinuation)
    }

    @Test
    fun `别的日子的任务不会混进来`() {
        val tasks = listOf(task(9 * 60, 10 * 60, epochDay = day - 1))
        assertTrue(DaySegments.tasksOn(tasks, day).isEmpty())
    }
}
