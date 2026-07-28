package com.wsy.ci.feature.stats

import com.wsy.ci.core.db.TaskEntity
import com.wsy.ci.core.db.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class TaskRecordsFilterTest {

    private fun record(
        id: Long,
        status: TaskStatus,
        domainId: Long?,
        domainName: String = "读书",
    ) = TaskRecord(
        task = TaskEntity(
            id = id,
            title = "任务$id",
            epochDay = 20_000,
            startMinute = 540,
            endMinute = 600,
            domainId = domainId,
            status = status,
        ),
        domainName = domainName,
        actualMinutes = 60,
        rewardCi = 60,
        expGained = 60,
    )

    private val records = listOf(
        record(1, TaskStatus.DONE, 1L),
        record(2, TaskStatus.SKIPPED, 1L),
        record(3, TaskStatus.PLANNED, 2L, "健身"),
        record(4, TaskStatus.RUNNING, 2L, "健身"),
        record(5, TaskStatus.DONE, null, "未分类"),
    )

    @Test
    fun `全部筛选不过滤任何任务`() {
        val result = filterRecords(records, RecordFilter.ALL, DomainFilter.All)

        assertEquals(5, result.size)
    }

    @Test
    fun `已完成只留DONE`() {
        val result = filterRecords(records, RecordFilter.DONE, DomainFilter.All)

        assertEquals(listOf(1L, 5L), result.map { it.task.id })
    }

    @Test
    fun `未完成同时含PLANNED和RUNNING`() {
        val result = filterRecords(records, RecordFilter.OPEN, DomainFilter.All)

        assertEquals(listOf(3L, 4L), result.map { it.task.id })
    }

    @Test
    fun `领域筛选与状态筛选是与的关系`() {
        val result = filterRecords(records, RecordFilter.DONE, DomainFilter.Only(1L))

        assertEquals(listOf(1L), result.map { it.task.id })
    }

    @Test
    fun `未分类领域可单独筛出而不会被当成全部`() {
        val result = filterRecords(records, RecordFilter.ALL, DomainFilter.Only(null))

        assertEquals(listOf(5L), result.map { it.task.id })
    }

    @Test
    fun `无匹配时返回空列表而非全部`() {
        val result = filterRecords(records, RecordFilter.SKIPPED, DomainFilter.Only(2L))

        assertEquals(emptyList<Long>(), result.map { it.task.id })
    }
}
