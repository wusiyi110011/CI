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

package com.wsy.ci.core.scheduler

import com.wsy.ci.core.db.TaskEntity
import com.wsy.ci.core.timeline.MINUTES_PER_DAY

/**
 * 把任务块对齐到「此刻」：日期换成 [nowEpochDay]、起点换成 [nowMinute]，时长保持不变。
 *
 * 用在开始计时的那一刻——计划轨于是反映真正的开工时间，而不是当初排的时段。
 * 允许与当天别的块重叠，计划轨会把重叠的块并排画出来（见 `TaskLanes`），
 * 所以这里不做任何避让。
 *
 * 深夜开工时结束时间会越过零点（endMinute 超过一天的分钟数），时间线按天切成两段画，
 * 不再把时长硬截在当天末尾。
 */
fun alignedToNow(task: TaskEntity, nowEpochDay: Long, nowMinute: Int): TaskEntity {
    val duration = (task.endMinute - task.startMinute).coerceAtLeast(1)
    return task.copy(
        epochDay = nowEpochDay,
        startMinute = nowMinute,
        endMinute = nowMinute + duration,
    )
}

/**
 * 把任务块的结束时间改成真正停下来的那一刻（[endEpochDay] 的 [endMinute]）。
 *
 * 用在结束专注的那一刻，计划轨于是反映真实的收工时间。跨零点收工时 endMinute
 * 会超过一天的分钟数，交给时间线按天切段；再快也保证至少占住一分钟，不产出空区间。
 */
fun endedAt(task: TaskEntity, endEpochDay: Long, endMinute: Int): TaskEntity {
    val relative = (endEpochDay - task.epochDay) * MINUTES_PER_DAY + endMinute
    return task.copy(endMinute = relative.coerceAtLeast(task.startMinute + 1L).toInt())
}
