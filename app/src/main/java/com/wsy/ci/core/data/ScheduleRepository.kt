package com.wsy.ci.core.data

import com.wsy.ci.core.db.BlockerEntity
import com.wsy.ci.core.db.CiDatabase
import com.wsy.ci.core.db.QuestType
import com.wsy.ci.core.db.TaskEntity
import com.wsy.ci.core.scheduler.RescheduleResult
import com.wsy.ci.core.scheduler.Scheduler
import com.wsy.ci.core.scheduler.Slot
import com.wsy.ci.core.util.TimeFormat
import java.time.LocalDate
import java.time.LocalTime

/** 排程落库：blocker 管理、某日重排（预览 + 应用）、AI 路线生成的任务批量落库。 */
class ScheduleRepository(private val db: CiDatabase) {

    fun observeBlockers(epochDay: Long) = db.blockerDao().observeByDay(epochDay)

    suspend fun addBlocker(blocker: BlockerEntity): Long = db.blockerDao().insert(blocker)

    suspend fun removeBlocker(blocker: BlockerEntity) = db.blockerDao().delete(blocker)

    /** 计算某日重排预览（不落库）。当天则避开已过去的时间。 */
    suspend fun previewReschedule(epochDay: Long): Pair<RescheduleResult, List<TaskEntity>> {
        val tasks = db.taskDao().byRange(epochDay, epochDay)
        val blockers = db.blockerDao().byDay(epochDay).map { Slot(it.startMinute, it.endMinute) }
        val deadlines = db.questDao().activeByType(QuestType.MAIN)
            .mapNotNull { q -> q.deadlineEpochDay?.let { q.id to it } }
            .toMap()
        val nowMinute = if (epochDay == LocalDate.now().toEpochDay()) {
            LocalTime.now().let { it.hour * 60 + it.minute }
        } else null
        val result = Scheduler.reschedule(
            tasks = tasks,
            blockers = blockers,
            nowMinute = nowMinute,
            deadlineByQuestId = deadlines,
        )
        return result to tasks
    }

    /** 应用重排结果。 */
    suspend fun applyReschedule(result: RescheduleResult) {
        result.tasks.forEach { db.taskDao().update(it) }
    }

    /** AI 路线确认后：领域（可复用已有）+ 主线 + 章节任务不落时间，只建任务线。 */
    suspend fun describeDiff(result: RescheduleResult, original: List<TaskEntity>): List<String> {
        val titles = original.associateBy({ it.id }, { it.title })
        val moved = result.movedFrom(original)
        val lines = moved.map { (id, fromTo) ->
            "「${titles[id]}」${TimeFormat.minuteOfDay(fromTo.first)} → ${TimeFormat.minuteOfDay(fromTo.second)}"
        }
        val stuck = result.unplaced.map { "「${it.title}」今天塞不下了，保留原时间待处理" }
        return lines + stuck
    }
}
