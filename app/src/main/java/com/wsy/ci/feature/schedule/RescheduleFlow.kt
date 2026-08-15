package com.wsy.ci.feature.schedule

import com.wsy.ci.core.data.ScheduleChangedException
import com.wsy.ci.core.data.ScheduleRepository
import com.wsy.ci.core.db.BlockerEntity
import com.wsy.ci.core.db.TaskEntity
import com.wsy.ci.core.scheduler.RescheduleResult
import com.wsy.ci.core.util.TimeFormat
import com.wsy.ci.feature.today.TodayViewModel.NlState
import com.wsy.ci.feature.today.TodayViewModel.UndoSchedule
import com.wsy.ci.llm.ParsedBlocker
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * 「占位事件 → 重排 diff 预览 → 确认落库 → 短时撤销」的通用流程，从 `TodayViewModel` 抽出
 * 以便语音指令复用同一条链路（不用重写重排、diff 展示和撤销这一整套）。
 *
 * [NlState] / [UndoSchedule] 仍然物理定义在 `TodayViewModel` 里——Kotlin 不支持嵌套
 * typealias，为了让 `TodayScreen.kt` / `TodayDialogs.kt` 对 `TodayViewModel.NlState` 的引用
 * 保持零改动，这两个类型没有真的搬家，这里只是导入复用。
 */
class RescheduleFlow(
    private val schedule: ScheduleRepository,
    private val scope: CoroutineScope,
    private val onApplied: suspend () -> Unit,
) {
    val nlState = MutableStateFlow<NlState>(NlState.Idle)

    /** 短时窗口内可撤销的重排结果；只保存在内存里，不增加 Room 字段。 */
    val undoSchedule = MutableStateFlow<UndoSchedule?>(null)
    private var undoExpiryJob: Job? = null

    /** 确认占位事件并生成各受影响日期的重排 diff，实际插入延迟到应用阶段。 */
    fun confirmBlockers(parsed: List<ParsedBlocker>) {
        scope.launch {
            val entities = parsed.mapNotNull { it.toEntityOrNull() }
                .distinctBy { Triple(it.epochDay, it.startMinute, it.endMinute to it.title) }
            if (entities.isEmpty()) {
                nlState.value = NlState.Error("时间段无法解析，请换个说法")
                return@launch
            }
            val days = entities.map { it.epochDay }.distinct().sorted()
            val results = mutableListOf<Pair<Long, RescheduleResult>>()
            val lines = mutableListOf<String>()
            val originalTasks = mutableListOf<TaskEntity>()
            for (day in days) {
                val (result, original) = schedule.previewReschedule(day, entities)
                results.add(day to result)
                originalTasks += original
                lines.addAll(
                    schedule.describeDiff(result, original)
                        .map { "${TimeFormat.shortDate(day)} $it" }
                )
            }
            if (lines.isEmpty()) lines.add("确认后将记录占位，现有任务无需移动")
            nlState.value = NlState.Diff(
                results = results,
                lines = lines,
                pendingBlockers = entities,
                originalTasks = originalTasks.distinctBy { it.id },
            )
        }
    }

    fun applyDiff(diff: NlState.Diff) {
        if (!nlState.compareAndSet(diff, NlState.Loading)) return
        scope.launch {
            try {
                val appliedTasks = diff.results.flatMap { it.second.tasks }.distinctBy { it.id }
                val insertedBlockers = schedule.applyReschedules(
                    results = diff.results.map { it.second },
                    pendingBlockers = diff.pendingBlockers,
                    expectedBeforeTasks = diff.originalTasks,
                )
                // 只在事务成功后暴露撤销入口，且保存实际写入的 blocker 主键。
                offerUndo(UndoSchedule(diff.originalTasks, appliedTasks, insertedBlockers))
                // 数据写入完成后才把动作暴露给 UI，避免 Snackbar 先于时间线刷新出现。
                nlState.value = NlState.Idle
                onApplied()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: ScheduleChangedException) {
                nlState.value = NlState.Error("计划已发生变化，请重新预览后再应用")
            } catch (_: Exception) {
                nlState.value = NlState.Error("调整应用失败，原计划未改变")
            }
        }
    }

    /** 在短时窗口内恢复重排前的任务和本次新增 blocker。 */
    fun undoLastReschedule() {
        scope.launch {
            val snapshot = undoSchedule.value ?: return@launch
            undoExpiryJob?.cancel()
            undoSchedule.value = null
            schedule.undoReschedule(
                beforeTasks = snapshot.beforeTasks,
                appliedTasks = snapshot.appliedTasks,
                insertedBlockers = snapshot.insertedBlockers,
            )
            onApplied()
        }
    }

    /** Snackbar 被动关闭时清掉撤销入口，避免旧操作再次被误用。 */
    fun dismissUndo() {
        undoExpiryJob?.cancel()
        undoSchedule.value = null
    }

    /** 放弃预览：尚未落库，因此只关闭预览。 */
    fun cancelDiff() {
        scope.launch { nlState.value = NlState.Idle }
    }

    fun dismissNl() {
        nlState.value = NlState.Idle
    }

    private fun offerUndo(snapshot: UndoSchedule) {
        undoExpiryJob?.cancel()
        undoSchedule.value = snapshot
        undoExpiryJob = scope.launch {
            delay(UNDO_WINDOW_MILLIS)
            undoSchedule.value = null
        }
    }

    private fun ParsedBlocker.toEntityOrNull(): BlockerEntity? = try {
        val day = LocalDate.parse(date).toEpochDay()
        val startMin = parseHm(start) ?: return null
        val endMin = parseHm(end) ?: return null
        if (endMin <= startMin) null
        else BlockerEntity(epochDay = day, startMinute = startMin, endMinute = endMin, title = title)
    } catch (_: Exception) {
        null
    }

    private fun parseHm(text: String): Int? {
        val parts = text.trim().split(":")
        if (parts.size != 2) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        if (h !in 0..23 || m !in 0..59) return null
        return h * 60 + m
    }

    private companion object {
        /** Snackbar 的可操作窗口，足够完成一次确认又不会长期保留旧快照。 */
        const val UNDO_WINDOW_MILLIS = 8_000L
    }
}
