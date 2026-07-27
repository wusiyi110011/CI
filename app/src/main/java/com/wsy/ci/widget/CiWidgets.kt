package com.wsy.ci.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.wsy.ci.CiApp
import com.wsy.ci.MainActivity
import com.wsy.ci.core.db.TaskEntity
import com.wsy.ci.core.db.TaskStatus
import com.wsy.ci.core.economy.FocusOutcome
import com.wsy.ci.core.util.TimeFormat
import java.time.LocalDate
import kotlinx.coroutines.flow.first

private val TASK_ID_KEY = ActionParameters.Key<Long>("taskId")
private val TASK_TITLE_KEY = ActionParameters.Key<String>("taskTitle")

/** 小组件数据快照。 */
private data class WidgetState(
    val runningTitle: String?,
    val runningStartAt: Long?,
    val tasks: List<TaskEntity>,
)

private suspend fun loadState(context: Context): WidgetState {
    val container = (context.applicationContext as CiApp).container
    val today = LocalDate.now().toEpochDay()
    val tasks = container.db.taskDao().observeByDay(today).first()
    val open = container.db.sessionDao().openSession()
    val runningTask = open?.taskId?.let { id -> tasks.firstOrNull { it.id == id } }
    return WidgetState(
        runningTitle = if (open != null) (runningTask?.title ?: "自由专注") else null,
        runningStartAt = open?.startAt,
        tasks = tasks,
    )
}

/** 大组件：今日任务列表，每项一键开始；顶部显示进行中任务与结束按钮。 */
class CiTodayWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = loadState(context)
        provideContent {
            GlanceTheme(colors = CiGlanceColors) {
                TodayWidgetContent(state)
            }
        }
    }
}

class CiTodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CiTodayWidget()
}

/** 小组件：当前/下一个任务 + 单按钮。 */
class CiTimerWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = loadState(context)
        provideContent {
            GlanceTheme(colors = CiGlanceColors) {
                TimerWidgetContent(state)
            }
        }
    }
}

class CiTimerWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CiTimerWidget()
}

@Composable
private fun TodayWidgetContent(state: WidgetState) {
    Column(
        modifier = GlanceModifier.fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = GlanceModifier.fillMaxWidth()) {
            Text(
                "⏱ 今日",
                style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp),
                modifier = GlanceModifier.clickable(actionStartActivity<MainActivity>()),
            )
            Spacer(modifier = GlanceModifier.defaultWeight())
            if (state.runningTitle != null) {
                Text(
                    "■ 结束「${state.runningTitle}」",
                    style = TextStyle(color = GlanceTheme.colors.secondary, fontSize = 14.sp),
                    modifier = GlanceModifier.clickable(actionRunCallback<StopTimerAction>()),
                )
            }
        }
        Spacer(modifier = GlanceModifier.padding(4.dp))
        LazyColumn {
            items(state.tasks, itemId = { it.id }) { task ->
                WidgetTaskRow(task, timerBusy = state.runningTitle != null)
            }
        }
    }
}

@Composable
private fun WidgetTaskRow(task: TaskEntity, timerBusy: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = GlanceModifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Text(
            TimeFormat.minuteOfDay(task.startMinute),
            style = TextStyle(fontSize = 12.sp, color = GlanceTheme.colors.onSurfaceVariant),
            modifier = GlanceModifier.width(44.dp),
        )
        val prefix = when (task.status) {
            TaskStatus.DONE -> "✓ "
            TaskStatus.RUNNING -> "▶ "
            TaskStatus.SKIPPED -> "⤫ "
            TaskStatus.PLANNED -> ""
        }
        Text(
            prefix + task.title,
            style = TextStyle(fontSize = 14.sp),
            maxLines = 1,
            modifier = GlanceModifier.defaultWeight(),
        )
        if (task.status == TaskStatus.PLANNED && !timerBusy) {
            Text(
                "▶开始",
                style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 14.sp),
                modifier = GlanceModifier
                    .cornerRadius(8.dp)
                    .padding(horizontal = 6.dp)
                    .clickable(
                        actionRunCallback<StartTimerAction>(
                            actionParametersOf(TASK_ID_KEY to task.id, TASK_TITLE_KEY to task.title)
                        )
                    ),
            )
        }
    }
}

@Composable
private fun TimerWidgetContent(state: WidgetState) {
    Column(
        modifier = GlanceModifier.fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (state.runningTitle != null) {
            Text(state.runningTitle, style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 15.sp), maxLines = 1)
            Text(
                "专注中 · 点击结束",
                style = TextStyle(color = GlanceTheme.colors.secondary, fontSize = 13.sp),
                modifier = GlanceModifier.padding(top = 6.dp)
                    .clickable(actionRunCallback<StopTimerAction>()),
            )
        } else {
            val next = state.tasks.firstOrNull { it.status == TaskStatus.PLANNED }
            if (next != null) {
                Text(next.title, style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 15.sp), maxLines = 1)
                Text(
                    "▶ 开始（${TimeFormat.minuteOfDay(next.startMinute)}）",
                    style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 13.sp),
                    modifier = GlanceModifier.padding(top = 6.dp)
                        .clickable(
                            actionRunCallback<StartTimerAction>(
                                actionParametersOf(TASK_ID_KEY to next.id, TASK_TITLE_KEY to next.title)
                            )
                        ),
                )
            } else {
                Text(
                    "今日无待办\n点击打开应用",
                    style = TextStyle(fontSize = 13.sp),
                    modifier = GlanceModifier.clickable(actionStartActivity<MainActivity>()),
                )
            }
        }
    }
}

class StartTimerAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val taskId = parameters[TASK_ID_KEY]
        val title = parameters[TASK_TITLE_KEY] ?: "专注中"
        TimerService.start(context, taskId, title)
        CiWidgetUpdater.updateAll(context)
    }
}

/** 小组件上的结束默认按「按时完成」结算；要选其他结果请进应用操作。 */
class StopTimerAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val container = (context.applicationContext as CiApp).container
        container.timerRepository.stopSession(FocusOutcome.COMPLETED)
        TimerService.stop(context)
        CiWidgetUpdater.updateAll(context)
    }
}
