package com.wsy.ci.core.scheduler

import com.wsy.ci.core.db.TaskEntity

/** 当日最后一分钟。对齐到深夜时用它兜底，避免时间块跨到第二天。 */
private const val LAST_MINUTE_OF_DAY = 24 * 60 - 1

/**
 * 把任务块对齐到「此刻」：日期换成 [nowEpochDay]、起点换成 [nowMinute]，时长保持不变。
 *
 * 用在开始计时的那一刻——计划轨于是反映真正的开工时间，而不是当初排的时段。
 * 允许与当天别的块重叠，计划轨会把重叠的块并排画出来（见 `TaskLanes`），
 * 所以这里不做任何避让。
 *
 * 深夜开始导致时长放不下时，结束时间截到当天末尾；起点已经是当天最后一分钟的
 * 极端情况下仍保证 endMinute > startMinute，不产出空区间。
 */
fun alignedToNow(task: TaskEntity, nowEpochDay: Long, nowMinute: Int): TaskEntity {
    val duration = (task.endMinute - task.startMinute).coerceAtLeast(1)
    val endMinute = minOf(nowMinute + duration, LAST_MINUTE_OF_DAY)
        .coerceAtLeast(nowMinute + 1)
    return task.copy(
        epochDay = nowEpochDay,
        startMinute = nowMinute,
        endMinute = endMinute,
    )
}
