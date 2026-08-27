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
import com.wsy.ci.core.db.TaskStatus

/** 一段时间区间 [startMinute, endMinute)，当日分钟数表示。 */
data class Slot(val startMinute: Int, val endMinute: Int) {
    init {
        require(endMinute > startMinute) { "空区间：$startMinute..$endMinute" }
    }

    val duration: Int get() = endMinute - startMinute
}

/** 重排结果：新任务列表 + 挤不下的任务（保留原时间并标记冲突，交 UI 提示）。 */
data class RescheduleResult(
    val tasks: List<TaskEntity>,
    val unplaced: List<TaskEntity>,
) {
    /** 与原排布的差异（taskId → 新起始分钟），用于 diff 预览。 */
    fun movedFrom(original: List<TaskEntity>): Map<Long, Pair<Int, Int>> {
        val before = original.associateBy({ it.id }, { it.startMinute })
        return tasks.mapNotNull { t ->
            val old = before[t.id] ?: return@mapNotNull null
            if (old != t.startMinute) t.id to (old to t.startMinute) else null
        }.toMap()
    }
}

/**
 * 确定性贪心重排引擎（不依赖 LLM）：
 * - 固定不动：锁定块、已完成/进行中/已跳过任务
 * - 不可用：blockers（临时有事）与日窗口之外
 * - 可移动任务按优先级（主线截止近者优先 → 原起始时间）依次塞入最早可用空档
 * - 塞不下的任务保留原时间返回在 unplaced，由用户决定删改
 */
object Scheduler {

    val DEFAULT_DAY_WINDOW = Slot(7 * 60, 23 * 60)

    fun reschedule(
        tasks: List<TaskEntity>,
        blockers: List<Slot>,
        dayWindow: Slot = DEFAULT_DAY_WINDOW,
        nowMinute: Int? = null,
        deadlineByQuestId: Map<Long, Long> = emptyMap(),
    ): RescheduleResult {
        // 时长非法（endMinute <= startMinute）的脏数据不参与放置：
        // movable 会被当成 0 分钟塞进任意空档产出非法区间落库，fixed 会让 Slot 构造直接抛异常。
        // 与「塞不下不静默丢弃」同一口径：原样保留并计入 unplaced，交 UI 提示用户修正。
        val (broken, validTasks) = tasks.partition { it.endMinute <= it.startMinute }
        val (movable, fixed) = validTasks.partition { it.isMovable() }

        val occupied = buildList {
            fixed.forEach { add(Slot(it.startMinute, it.endMinute)) }
            addAll(blockers)
            // 已过去的时间不可用
            nowMinute?.takeIf { it > dayWindow.startMinute }?.let {
                add(Slot(dayWindow.startMinute, it.coerceAtMost(dayWindow.endMinute)))
            }
        }.filter { it.endMinute > dayWindow.startMinute && it.startMinute < dayWindow.endMinute }

        var free = freeSlots(dayWindow, occupied)
        val ordered = movable.sortedWith(
            compareBy(
                { task -> task.questId?.let { deadlineByQuestId[it] } ?: Long.MAX_VALUE },
                { it.startMinute },
                { it.id },
            )
        )

        val placed = mutableListOf<TaskEntity>()
        val unplaced = broken.toMutableList()
        for (task in ordered) {
            val duration = task.endMinute - task.startMinute
            // 原时间还空着就不动，制造最小扰动
            val keep = free.firstOrNull {
                it.startMinute <= task.startMinute && task.endMinute <= it.endMinute
            }
            if (keep != null) {
                placed.add(task)
                free = carve(free, keep, task.startMinute, task.endMinute)
                continue
            }
            val slot = free.firstOrNull { it.duration >= duration }
            if (slot == null) {
                unplaced.add(task)
                continue
            }
            placed.add(task.copy(startMinute = slot.startMinute, endMinute = slot.startMinute + duration))
            free = carve(free, slot, slot.startMinute, slot.startMinute + duration)
        }

        val result = (fixed + placed + unplaced).sortedBy { it.startMinute }
        return RescheduleResult(tasks = result, unplaced = unplaced)
    }

    private fun TaskEntity.isMovable(): Boolean =
        !locked && status == TaskStatus.PLANNED

    /** 从窗口中减去占用，得到升序空档表。 */
    fun freeSlots(window: Slot, occupied: List<Slot>): List<Slot> {
        val sorted = occupied
            .map { Slot(it.startMinute.coerceAtLeast(window.startMinute), it.endMinute.coerceAtMost(window.endMinute)) }
            .filter { it.endMinute > it.startMinute }
            .sortedBy { it.startMinute }
        val free = mutableListOf<Slot>()
        var cursor = window.startMinute
        for (busy in sorted) {
            if (busy.startMinute > cursor) free.add(Slot(cursor, busy.startMinute))
            cursor = maxOf(cursor, busy.endMinute)
        }
        if (cursor < window.endMinute) free.add(Slot(cursor, window.endMinute))
        return free
    }

    /** 从 slot 中占掉 [start, end) 后更新空档表。 */
    private fun carve(free: List<Slot>, slot: Slot, start: Int, end: Int): List<Slot> =
        free.flatMap {
            if (it != slot) listOf(it)
            else buildList {
                if (start > it.startMinute) add(Slot(it.startMinute, start))
                if (end < it.endMinute) add(Slot(end, it.endMinute))
            }
        }
}
