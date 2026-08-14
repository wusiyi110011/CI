package com.wsy.ci.core.widget

import com.wsy.ci.core.db.TaskEntity
import com.wsy.ci.core.db.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TodayWidgetModelTest {

    private fun task(
        id: Long,
        start: Int,
        end: Int,
        title: String = "任务$id",
        status: TaskStatus = TaskStatus.PLANNED,
    ) = TaskEntity(
        id = id, title = title, epochDay = 0,
        startMinute = start, endMinute = end, status = status,
    )

    private fun build(
        tasks: List<TaskEntity>,
        openTaskId: Long? = null,
        openStartAt: Long? = null,
        focusedMinutes: Int = 0,
    ) = TodayWidgetModel.build(tasks, openTaskId, openStartAt, focusedMinutes)

    @Test
    fun `无任务且没在计时是空态`() {
        val ui = build(emptyList())

        assertEquals(TodayWidgetMode.EMPTY, ui.mode)
        assertNull(ui.headerStat)
        assertTrue(ui.rows.isEmpty())
    }

    @Test
    fun `任务按起始时刻排序并格式化起止时间`() {
        val tasks = listOf(
            task(2, 10 * 60 + 45, 12 * 60),
            task(1, 9 * 60, 10 * 60 + 30),
        )

        val ui = build(tasks)

        assertEquals(listOf(1L, 2L), ui.rows.map { it.taskId })
        assertEquals("09:00–10:30", ui.rows[0].timeText)
    }

    @Test
    fun `跨天任务的结束时刻带次日前缀`() {
        val ui = build(listOf(task(1, 23 * 60, 25 * 60)))

        assertEquals("23:00–次日 01:00", ui.rows[0].timeText)
    }

    @Test
    fun `头部统计只数已完成`() {
        val tasks = listOf(
            task(1, 9 * 60, 10 * 60, status = TaskStatus.DONE),
            task(2, 10 * 60, 11 * 60, status = TaskStatus.SKIPPED),
            task(3, 11 * 60, 12 * 60),
        )

        val ui = build(tasks)

        assertEquals("1 / 3 已完成", ui.headerStat)
    }

    @Test
    fun `计时中的任务从列表移到卡片`() {
        val tasks = listOf(task(1, 9 * 60, 10 * 60), task(2, 10 * 60, 11 * 60))

        val ui = build(tasks, openTaskId = 1L, openStartAt = 1_700_000_000_000L)

        assertEquals(TodayWidgetMode.FOCUSING, ui.mode)
        assertEquals(1L, ui.focusing?.taskId)
        assertEquals("09:00–10:00", ui.focusing?.timeText)
        assertEquals(1_700_000_000_000L, ui.focusing?.startAt)
        assertEquals(listOf(2L), ui.rows.map { it.taskId })
        assertEquals("今天其余安排", ui.listLabel)
    }

    @Test
    fun `不挂任务的自由专注也进入计时态`() {
        val ui = build(emptyList(), openTaskId = null, openStartAt = 1_700_000_000_000L)

        assertEquals(TodayWidgetMode.FOCUSING, ui.mode)
        assertEquals("自由专注", ui.focusing?.title)
        assertEquals("", ui.focusing?.timeText)
        assertNull(ui.listLabel)
    }

    @Test
    fun `有专注在跑时其余任务的开始按钮置灰`() {
        val tasks = listOf(task(1, 9 * 60, 10 * 60), task(2, 10 * 60, 11 * 60))

        val ui = build(tasks, openTaskId = 1L, openStartAt = 1L)

        assertTrue(ui.rows[0].showStartButton)
        assertFalse(ui.rows[0].startEnabled)
    }

    @Test
    fun `空闲时开始按钮可点`() {
        val ui = build(listOf(task(1, 9 * 60, 10 * 60)))

        assertTrue(ui.rows[0].showStartButton)
        assertTrue(ui.rows[0].startEnabled)
    }

    @Test
    fun `已完成的行压暗且标题打删除线`() {
        val ui = build(listOf(task(1, 9 * 60, 10 * 60, status = TaskStatus.DONE)))

        val row = ui.rows[0]
        assertTrue(row.dimmed)
        assertTrue(row.strikethrough)
        assertFalse(row.showStartButton)
        assertEquals("已完成", row.badgeText)
        assertFalse(row.badgeAccent)
    }

    @Test
    fun `已跳过的行压暗但不打删除线`() {
        val ui = build(listOf(task(1, 9 * 60, 10 * 60, status = TaskStatus.SKIPPED)))

        val row = ui.rows[0]
        assertTrue(row.dimmed)
        assertFalse(row.strikethrough)
        assertEquals("已跳过", row.badgeText)
        assertTrue(row.badgeAccent)
    }

    @Test
    fun `全部落定时进入完成态并汇总专注时长`() {
        val tasks = listOf(
            task(1, 9 * 60, 10 * 60, status = TaskStatus.DONE),
            task(2, 10 * 60, 11 * 60, status = TaskStatus.SKIPPED),
        )

        val ui = build(tasks, focusedMinutes = 260)

        assertEquals(TodayWidgetMode.ALL_DONE, ui.mode)
        assertEquals("共专注 4小时20分，明天见", ui.doneSummary)
        assertEquals(2, ui.rows.size)
    }

    @Test
    fun `还有未开始任务时不是完成态`() {
        val tasks = listOf(
            task(1, 9 * 60, 10 * 60, status = TaskStatus.DONE),
            task(2, 10 * 60, 11 * 60),
        )

        val ui = build(tasks)

        assertEquals(TodayWidgetMode.IDLE, ui.mode)
        assertNull(ui.doneSummary)
        assertNull(ui.listLabel)
    }
}
