package com.wsy.ci.core.widget

import com.wsy.ci.core.db.TaskEntity
import com.wsy.ci.core.db.TaskStatus
import com.wsy.ci.core.util.TimeFormat

/**
 * 今日日程小组件的四种形态。判定顺序见 [TodayWidgetModel.build]，
 * 计时态优先于其他一切（哪怕今天一条安排都没有，自由专注也要占住卡片）。
 */
enum class TodayWidgetMode {
    /** 今天没有安排，也没在计时。 */
    EMPTY,

    /** 有安排，等着开始。 */
    IDLE,

    /** 有一段专注正在跑。 */
    FOCUSING,

    /** 今天的安排全部落定（完成或跳过）。 */
    ALL_DONE,
}

/** 列表里一行任务的呈现结果。右侧要么是「开始」按钮，要么是状态角标，两者互斥。 */
data class WidgetTaskRow(
    val taskId: Long,
    val title: String,
    /** 「09:00–10:30」，跨天带「次日」前缀。 */
    val timeText: String,
    val status: TaskStatus,
    /** 压暗行（已完成/已跳过）：Glance 没有 elevation，层次只能靠底色深浅表达。 */
    val dimmed: Boolean,
    /** 已完成的标题打删除线。 */
    val strikethrough: Boolean,
    val showStartButton: Boolean,
    /** 已有专注在跑时按钮置灰，点了只提示不排队——计时是全局单例。 */
    val startEnabled: Boolean,
    val badgeText: String?,
    /** 角标是否用强调色（已跳过用 tertiary，已完成只用弱化文字）。 */
    val badgeAccent: Boolean,
)

/** 计时态突出显示的那张卡。[taskId] 为空表示不挂任务的自由专注。 */
data class FocusingCard(
    val taskId: Long?,
    val title: String,
    /** 自由专注没有计划时段，这里是空串。 */
    val timeText: String,
    /** session 开始的毫秒时间戳，交给 Chronometer 做基准。 */
    val startAt: Long,
)

/** 小组件一次渲染需要的全部内容，Glance 侧只做摆放不做判断。 */
data class TodayWidgetUi(
    val mode: TodayWidgetMode,
    /** 「2 / 7 已完成」，空态下为 null。 */
    val headerStat: String?,
    val focusing: FocusingCard?,
    /** 全部完成时的收尾文案。 */
    val doneSummary: String?,
    /** 计时态下列表上方的分组标题。 */
    val listLabel: String?,
    val rows: List<WidgetTaskRow>,
)

private const val FREE_FOCUS_TITLE = "自由专注"

/**
 * 把今日任务 + 当前 session 整理成小组件要显示的东西。
 *
 * 纯函数、不碰 Android 框架，因此可直接单测；Glance 那侧只负责把结果摆到屏幕上。
 */
object TodayWidgetModel {

    /**
     * @param tasks 今天的任务，顺序不限（内部按起始时刻排）
     * @param openSessionTaskId 正在跑的 session 挂的任务；session 存在但没挂任务时传 null
     * @param openSessionStartAt 正在跑的 session 起始毫秒；没有在跑时传 null
     * @param focusedMinutesToday 今天已结束专注的合计分钟，只在全部完成时用得上
     */
    fun build(
        tasks: List<TaskEntity>,
        openSessionTaskId: Long?,
        openSessionStartAt: Long?,
        focusedMinutesToday: Int,
    ): TodayWidgetUi {
        val sorted = tasks.sortedWith(compareBy({ it.startMinute }, { it.id }))
        val isFocusing = openSessionStartAt != null
        val focusingTask = openSessionTaskId?.let { id -> sorted.firstOrNull { it.id == id } }

        val mode = when {
            isFocusing -> TodayWidgetMode.FOCUSING
            sorted.isEmpty() -> TodayWidgetMode.EMPTY
            sorted.all { it.status == TaskStatus.DONE || it.status == TaskStatus.SKIPPED } ->
                TodayWidgetMode.ALL_DONE
            else -> TodayWidgetMode.IDLE
        }

        val focusing = openSessionStartAt?.let { startAt ->
            FocusingCard(
                taskId = focusingTask?.id,
                title = focusingTask?.title ?: FREE_FOCUS_TITLE,
                timeText = focusingTask?.let { timeRange(it) } ?: "",
                startAt = startAt,
            )
        }

        // 正在专注的那条已经在上方卡片里了，列表不再重复。
        val rows = sorted.filter { it.id != focusingTask?.id }.map { row(it, isFocusing) }

        return TodayWidgetUi(
            mode = mode,
            headerStat = if (mode == TodayWidgetMode.EMPTY) null else headerStat(sorted),
            focusing = focusing,
            doneSummary = if (mode == TodayWidgetMode.ALL_DONE) {
                "共专注 ${TimeFormat.duration(focusedMinutesToday)}，明天见"
            } else {
                null
            },
            listLabel = if (mode == TodayWidgetMode.FOCUSING && rows.isNotEmpty()) "今天其余安排" else null,
            rows = rows,
        )
    }

    private fun headerStat(tasks: List<TaskEntity>): String {
        val done = tasks.count { it.status == TaskStatus.DONE }
        return "$done / ${tasks.size} 已完成"
    }

    private fun timeRange(task: TaskEntity): String =
        "${TimeFormat.minuteOfDay(task.startMinute)}–${TimeFormat.minuteOfDay(task.endMinute)}"

    private fun row(task: TaskEntity, timerBusy: Boolean): WidgetTaskRow {
        val settled = task.status == TaskStatus.DONE || task.status == TaskStatus.SKIPPED
        return WidgetTaskRow(
            taskId = task.id,
            title = task.title,
            timeText = timeRange(task),
            status = task.status,
            dimmed = settled,
            strikethrough = task.status == TaskStatus.DONE,
            showStartButton = task.status == TaskStatus.PLANNED,
            startEnabled = !timerBusy,
            badgeText = when (task.status) {
                TaskStatus.DONE -> "已完成"
                TaskStatus.SKIPPED -> "已跳过"
                TaskStatus.RUNNING -> "进行中"
                TaskStatus.PLANNED -> null
            },
            badgeAccent = task.status == TaskStatus.SKIPPED,
        )
    }
}
