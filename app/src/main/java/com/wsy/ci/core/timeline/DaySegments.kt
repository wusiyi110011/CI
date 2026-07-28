package com.wsy.ci.core.timeline

import com.wsy.ci.core.db.TaskEntity

/**
 * 一天的分钟数。任务的 `endMinute` 是相对 `epochDay` 零点的分钟数，
 * 超过这个值就表示这块跨到了次日（23:00 开始的三小时任务 endMinute = 26*60）。
 */
const val MINUTES_PER_DAY = 24 * 60

/** 某个区间落在某一天里的那一段。[isContinuation] 表示它是更早的日子延续过来的尾巴。 */
data class DaySlice(
    val startMinute: Int,
    val endMinute: Int,
    val isContinuation: Boolean,
)

/** 任务在某一天上的一段占用；延续段只画占用框，不重复写标题。 */
data class TaskSegment(
    val task: TaskEntity,
    val startMinute: Int,
    val endMinute: Int,
    val isContinuation: Boolean,
)

/**
 * 跨天时间块的按天切分：时间线以「一天」为画布，跨零点的块要在两天各画一段，
 * 各自按自己那一段的时长占比给高度。
 */
object DaySegments {

    /**
     * 把起点落在 [fromEpochDay] 的区间 [startMinute, endMinute) 投影到 [onEpochDay] 这一天，
     * 与这天不相交（或本就是空区间）时返回 null。
     */
    fun project(
        fromEpochDay: Long,
        startMinute: Int,
        endMinute: Int,
        onEpochDay: Long,
    ): DaySlice? {
        val offset = (fromEpochDay - onEpochDay) * MINUTES_PER_DAY
        val start = startMinute + offset
        val end = endMinute + offset
        if (end <= start) return null
        if (end <= 0L || start >= MINUTES_PER_DAY) return null
        return DaySlice(
            startMinute = start.coerceAtLeast(0L).toInt(),
            endMinute = end.coerceAtMost(MINUTES_PER_DAY.toLong()).toInt(),
            isContinuation = start < 0L,
        )
    }

    /**
     * [tasks] 里落在 [epochDay] 这天的片段，按起点排序。
     * 传进来的列表要包含前一天的任务，跨天的尾巴才有得算。
     */
    fun tasksOn(tasks: List<TaskEntity>, epochDay: Long): List<TaskSegment> = tasks
        .mapNotNull { task ->
            project(task.epochDay, task.startMinute, task.endMinute, epochDay)?.let { slice ->
                TaskSegment(
                    task = task,
                    startMinute = slice.startMinute,
                    endMinute = slice.endMinute,
                    isContinuation = slice.isContinuation,
                )
            }
        }
        .sortedBy { it.startMinute }
}
