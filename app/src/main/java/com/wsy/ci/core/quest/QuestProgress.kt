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

/**
 * 一条任务线在「具体任务」维度上的完成情况，纯由任务列表派生。
 *
 * 与主线卡上的「时间进度」是两回事：那个只看日历走了多远，
 * 这个看的是真正排出来的时间块做完了几个。
 */
data class QuestProgress(
    val total: Int,
    val done: Int,
    val skipped: Int,
    val running: Int,
    /** 全部任务的计划时长合计（分钟）。 */
    val plannedMinutes: Int,
    /** 已完成任务的计划时长合计（分钟）。 */
    val doneMinutes: Int,
) {
    /** 计划中（未开始、未跳过）的任务数。 */
    val pending: Int get() = total - done - skipped - running

    /** 已处理比例（完成 + 跳过），无任务时为 0。 */
    val ratio: Float get() = if (total == 0) 0f else (done + skipped).toFloat() / total

    companion object {
        /** 由任务列表算出完成情况；空列表得到全 0。 */
        fun of(tasks: List<TaskEntity>): QuestProgress {
            val doneTasks = tasks.filter { it.status == TaskStatus.DONE }
            return QuestProgress(
                total = tasks.size,
                done = doneTasks.size,
                skipped = tasks.count { it.status == TaskStatus.SKIPPED },
                running = tasks.count { it.status == TaskStatus.RUNNING },
                plannedMinutes = tasks.sumOf { it.endMinute - it.startMinute },
                doneMinutes = doneTasks.sumOf { it.endMinute - it.startMinute },
            )
        }
    }
}
