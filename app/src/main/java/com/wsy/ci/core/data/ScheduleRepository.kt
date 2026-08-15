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

package com.wsy.ci.core.data

import androidx.room.withTransaction
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

/** 差异预览后任务已被其他入口修改，旧预览不得覆盖新状态。 */
class ScheduleChangedException : IllegalStateException()

/** 排程落库：blocker 管理、某日重排（预览 + 应用）、AI 路线生成的任务批量落库。 */
class ScheduleRepository(private val db: CiDatabase) {

    private data class BlockerKey(
        val epochDay: Long,
        val startMinute: Int,
        val endMinute: Int,
        val title: String,
    )

    private fun BlockerEntity.scheduleKey() =
        BlockerKey(epochDay, startMinute, endMinute, title)

    fun observeBlockers(epochDay: Long) = db.blockerDao().observeByDay(epochDay)

    suspend fun addBlocker(blocker: BlockerEntity): Long = db.blockerDao().insert(blocker)

    suspend fun removeBlocker(blocker: BlockerEntity) = db.blockerDao().delete(blocker)

    /** 计算某日重排预览（不落库）。当天则避开已过去的时间。 */
    suspend fun previewReschedule(
        epochDay: Long,
        pendingBlockers: List<BlockerEntity> = emptyList(),
    ): Pair<RescheduleResult, List<TaskEntity>> {
        val tasks = db.taskDao().byRange(epochDay, epochDay)
        val blockers = (db.blockerDao().byDay(epochDay) + pendingBlockers.filter { it.epochDay == epochDay })
            .distinctBy { it.scheduleKey() }
            .map { Slot(it.startMinute, it.endMinute) }
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

    /** 一次事务插入占位事件并应用一组重排结果，避免跨日期调整只写入一部分。 */
    suspend fun applyReschedules(
        results: List<RescheduleResult>,
        pendingBlockers: List<BlockerEntity> = emptyList(),
        expectedBeforeTasks: List<TaskEntity> = emptyList(),
    ): List<BlockerEntity> = db.withTransaction {
        val updatedTasks = results.flatMap { it.tasks }.distinctBy { it.id }
        if (expectedBeforeTasks.isNotEmpty()) {
            val expectedById = expectedBeforeTasks.associateBy { it.id }
            updatedTasks.forEach { task ->
                val expected = expectedById[task.id] ?: throw ScheduleChangedException()
                if (db.taskDao().byId(task.id) != expected) throw ScheduleChangedException()
            }
        }
        val uniquePending = pendingBlockers.distinctBy { it.scheduleKey() }
        val existingKeys = uniquePending
            .map { it.epochDay }
            .distinct()
            .flatMap { db.blockerDao().byDay(it) }
            .mapTo(mutableSetOf()) { it.scheduleKey() }
        val insertedBlockers = uniquePending.filterNot { it.scheduleKey() in existingKeys }.map { blocker ->
            val id = db.blockerDao().insert(blocker.copy(id = 0))
            blocker.copy(id = id)
        }
        updatedTasks.forEach { db.taskDao().update(it) }
        insertedBlockers
    }

    /** 一次事务按版本恢复任务快照，并删除本次自然语言调整新增的占位事件。 */
    suspend fun undoReschedule(
        beforeTasks: List<TaskEntity>,
        appliedTasks: List<TaskEntity>,
        insertedBlockers: List<BlockerEntity>,
    ) =
        db.withTransaction {
            val appliedById = appliedTasks.associateBy { it.id }
            beforeTasks.forEach { before ->
                val applied = appliedById[before.id] ?: return@forEach
                if (db.taskDao().byId(before.id) == applied) {
                    db.taskDao().update(before)
                }
            }
            insertedBlockers.forEach { db.blockerDao().delete(it) }
        }

    /** 应用单日重排结果。 */
    suspend fun applyReschedule(result: RescheduleResult) {
        applyReschedules(listOf(result))
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
