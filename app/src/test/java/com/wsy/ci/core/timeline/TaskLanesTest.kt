package com.wsy.ci.core.timeline

import org.junit.Assert.assertEquals
import org.junit.Test

class TaskLanesTest {

    @Test
    fun `空列表返回空`() {
        assertEquals(emptyList<TaskLane>(), TaskLanes.assign(emptyList()))
    }

    @Test
    fun `互不相交的块各自独占整轨`() {
        val spans = listOf(Span(540, 600), Span(600, 660), Span(720, 780))

        val lanes = TaskLanes.assign(spans)

        assertEquals(listOf(TaskLane(0, 1), TaskLane(0, 1), TaskLane(0, 1)), lanes)
    }

    @Test
    fun `两个重叠块并排各占一半`() {
        val spans = listOf(Span(540, 660), Span(600, 720))

        val lanes = TaskLanes.assign(spans)

        assertEquals(listOf(TaskLane(0, 2), TaskLane(1, 2)), lanes)
    }

    @Test
    fun `结果顺序与输入一致而不是与时间顺序一致`() {
        val spans = listOf(Span(600, 720), Span(540, 660))

        val lanes = TaskLanes.assign(spans)

        // 晚开始的那个排在第 1 栏，尽管它是输入里的第一个
        assertEquals(listOf(TaskLane(1, 2), TaskLane(0, 2)), lanes)
    }

    @Test
    fun `一簇三块重叠时同簇统一为三栏`() {
        val spans = listOf(Span(540, 720), Span(560, 700), Span(580, 680))

        val lanes = TaskLanes.assign(spans)

        assertEquals(listOf(TaskLane(0, 3), TaskLane(1, 3), TaskLane(2, 3)), lanes)
    }

    @Test
    fun `前一簇散场后新块重新独占整轨`() {
        val spans = listOf(Span(540, 660), Span(600, 720), Span(780, 840))

        val lanes = TaskLanes.assign(spans)

        assertEquals(listOf(TaskLane(0, 2), TaskLane(1, 2), TaskLane(0, 1)), lanes)
    }

    @Test
    fun `首尾相接不算重叠可以复用同一栏`() {
        val spans = listOf(Span(540, 600), Span(560, 620), Span(600, 660))

        val lanes = TaskLanes.assign(spans)

        // 第三块与第一块首尾相接，回到第 0 栏；三块传递重叠算一簇，共 2 栏
        assertEquals(listOf(TaskLane(0, 2), TaskLane(1, 2), TaskLane(0, 2)), lanes)
    }
}
