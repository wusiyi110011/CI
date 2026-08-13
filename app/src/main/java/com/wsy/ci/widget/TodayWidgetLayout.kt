package com.wsy.ci.widget

import android.os.SystemClock
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import com.wsy.ci.MainActivity
import com.wsy.ci.R
import com.wsy.ci.core.widget.TodayWidgetMode
import com.wsy.ci.core.widget.TodayWidgetUi
import com.wsy.ci.core.widget.WidgetTaskRow

/**
 * 今日日程小组件的界面。层次全部由语义色阶 + 圆角 + 留白表达——
 * Glance 没有 elevation，卡片不能投影，压暗行只能靠底色「凹」回底板。
 */
@Composable
internal fun TodayWidgetContent(ui: TodayWidgetUi) {
    Column(
        modifier = GlanceModifier.fillMaxSize()
            .background(CiWidgetPalette.surfaceContainerLow)
            .padding(16.dp),
    ) {
        WidgetHeader(ui.headerStat)
        Divider()
        when (ui.mode) {
            TodayWidgetMode.EMPTY -> EmptyContent()
            else -> ScheduleContent(ui)
        }
    }
}

@Composable
private fun WidgetHeader(headerStat: String?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = GlanceModifier.fillMaxWidth()
            .clickable(actionStartActivity<MainActivity>()),
    ) {
        Text(
            "📅 今日日程",
            style = TextStyle(
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = GlanceTheme.colors.onSurface,
            ),
        )
        Spacer(modifier = GlanceModifier.defaultWeight())
        if (headerStat != null) {
            Text(
                headerStat,
                style = TextStyle(fontSize = 13.sp, color = GlanceTheme.colors.onSurfaceVariant),
            )
        }
    }
}

@Composable
private fun Divider() {
    Spacer(
        modifier = GlanceModifier.fillMaxWidth()
            .padding(vertical = 10.dp)
            .height(1.dp)
            .background(CiWidgetPalette.outlineVariant),
    )
}

@Composable
private fun ColumnScope.EmptyContent() {
    Column(
        modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "今天还没有安排任务",
            style = TextStyle(fontSize = 14.sp, color = GlanceTheme.colors.onSurfaceVariant),
        )
        Text(
            "点击去「复利」App 添加",
            style = TextStyle(fontSize = 13.sp, color = GlanceTheme.colors.outline),
            modifier = GlanceModifier.padding(top = 6.dp),
        )
        Spacer(modifier = GlanceModifier.height(6.dp))
        Box(
            modifier = GlanceModifier
                .width(104.dp)
                .height(36.dp)
                .cornerRadius(12.dp)
                .background(GlanceTheme.colors.surfaceVariant)
                .clickable(actionStartActivity<MainActivity>()),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "打开复利  →",
                style = TextStyle(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = GlanceTheme.colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                ),
            )
        }
    }
}

@Composable
private fun ColumnScope.ScheduleContent(ui: TodayWidgetUi) {
    if (ui.focusing != null) {
        FocusingCard(ui.focusing.title, ui.focusing.timeText, ui.focusing.startAt)
    }
    if (ui.mode == TodayWidgetMode.ALL_DONE && ui.doneSummary != null) {
        DoneBanner(ui.doneSummary)
    }
    if (ui.listLabel != null) {
        Text(
            ui.listLabel,
            style = TextStyle(
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = GlanceTheme.colors.onSurfaceVariant,
            ),
            modifier = GlanceModifier.padding(top = 10.dp, bottom = 6.dp),
        )
    }
    LazyColumn(modifier = GlanceModifier.defaultWeight()) {
        items(ui.rows, itemId = { it.taskId }) { row ->
            Column {
                TaskRow(row)
                Spacer(modifier = GlanceModifier.height(4.dp))
            }
        }
    }
}

/** 计时态卡片：进行中的任务 + 跳秒计时 + 结束按钮。 */
@Composable
private fun FocusingCard(title: String, timeText: String, startAt: Long) {
    Column(
        modifier = GlanceModifier.fillMaxWidth()
            .cornerRadius(20.dp)
            .background(GlanceTheme.colors.primaryContainer)
            .padding(16.dp),
    ) {
        Text(
            "🔴 专注中",
            style = TextStyle(
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = GlanceTheme.colors.onErrorContainer,
            ),
            modifier = GlanceModifier
                .cornerRadius(8.dp)
                .background(GlanceTheme.colors.errorContainer)
                .padding(horizontal = 8.dp, vertical = 3.dp),
        )
        Text(
            title,
            maxLines = 1,
            style = TextStyle(
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                color = GlanceTheme.colors.onPrimaryContainer,
            ),
            modifier = GlanceModifier.padding(top = 8.dp),
        )
        if (timeText.isNotEmpty()) {
            Text(
                timeText,
                style = TextStyle(fontSize = 13.sp, color = GlanceTheme.colors.onSurfaceVariant),
                modifier = GlanceModifier.padding(top = 2.dp),
            )
        }
        ElapsedChronometer(startAt)
        StopButton()
    }
}

/**
 * 跳秒计时。Glance 的 Text 不会自己走秒，所以这里嵌一个原生 [android.widget.Chronometer]：
 * 它由系统侧按秒重绘，不需要小组件反复被唤醒。
 *
 * Chronometer 的基准是 [SystemClock.elapsedRealtime] 时间轴，而 session 存的是墙钟毫秒，
 * 两者要换算一次才对得上。
 */
@Composable
private fun ElapsedChronometer(startAt: Long) {
    val context = LocalContext.current
    val base = SystemClock.elapsedRealtime() - (System.currentTimeMillis() - startAt)
    val views = RemoteViews(context.packageName, R.layout.ci_widget_chronometer).apply {
        setChronometer(R.id.ci_widget_chronometer, base, "已专注 %s", true)
        setTextColor(
            R.id.ci_widget_chronometer,
            GlanceTheme.colors.onPrimaryContainer.getColor(context).toArgb(),
        )
    }
    AndroidRemoteViews(
        remoteViews = views,
        modifier = GlanceModifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
    )
}

/**
 * 结束专注。第一次点击进入确认态，窗口内再点一次才真结算——
 * 桌面误触一下就写 session 和流水，代价太高。确认态没有自动倒计时（Glance 没有定时器），
 * 过期判定放在 [ConfirmStopAction] 的下一次点击里。
 */
@Composable
private fun StopButton() {
    val armed = isStopArmed(currentState(STOP_ARMED_AT_KEY), System.currentTimeMillis())
    Text(
        if (armed) "再点一次确认结束" else "结束专注",
        style = TextStyle(
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = if (armed) GlanceTheme.colors.onErrorContainer else GlanceTheme.colors.onError,
        ),
        modifier = GlanceModifier.fillMaxWidth()
            .cornerRadius(22.dp)
            .background(if (armed) GlanceTheme.colors.errorContainer else GlanceTheme.colors.error)
            .padding(vertical = 12.dp)
            .clickable(actionRunCallback<ConfirmStopAction>()),
    )
}

@Composable
private fun DoneBanner(summary: String) {
    Column(
        modifier = GlanceModifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "🎉 今日安排已全部完成",
            style = TextStyle(
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = GlanceTheme.colors.onSurface,
            ),
        )
        Text(
            summary,
            style = TextStyle(fontSize = 13.sp, color = GlanceTheme.colors.onSurfaceVariant),
            modifier = GlanceModifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun TaskRow(row: WidgetTaskRow) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = GlanceModifier.fillMaxWidth()
            .cornerRadius(14.dp)
            .background(
                // 已落定的行退回底板色，未落定的行浮在上面——代替 Glance 没有的阴影。
                if (row.dimmed) CiWidgetPalette.surfaceContainerLow else GlanceTheme.colors.surface
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                row.timeText,
                style = TextStyle(fontSize = 12.sp, color = GlanceTheme.colors.onSurfaceVariant),
            )
            Text(
                row.title,
                maxLines = 1,
                style = TextStyle(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (row.dimmed) {
                        GlanceTheme.colors.onSurfaceVariant
                    } else {
                        GlanceTheme.colors.onSurface
                    },
                    textDecoration = if (row.strikethrough) TextDecoration.LineThrough else TextDecoration.None,
                ),
            )
        }
        if (row.showStartButton) {
            StartButton(row)
        } else if (row.badgeText != null) {
            StatusBadge(row.badgeText, row.badgeAccent)
        }
    }
}

@Composable
private fun StartButton(row: WidgetTaskRow) {
    // 计时是全局单例：已有专注在跑时按钮置灰，点了只提示，不打断也不排队。
    val action = if (row.startEnabled) {
        actionRunCallback<StartTimerAction>(
            actionParametersOf(TASK_ID_KEY to row.taskId, TASK_TITLE_KEY to row.title)
        )
    } else {
        actionRunCallback<BlockedStartAction>()
    }
    Text(
        "开始",
        style = TextStyle(
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = if (row.startEnabled) {
                GlanceTheme.colors.onPrimaryContainer
            } else {
                GlanceTheme.colors.onSurfaceVariant
            },
        ),
        modifier = GlanceModifier
            .cornerRadius(18.dp)
            .background(
                if (row.startEnabled) {
                    GlanceTheme.colors.primaryContainer
                } else {
                    GlanceTheme.colors.surfaceVariant
                }
            )
            .padding(horizontal = 16.dp, vertical = 9.dp)
            .clickable(action),
    )
}

@Composable
private fun StatusBadge(text: String, accent: Boolean) {
    if (accent) {
        Text(
            text,
            style = TextStyle(
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = GlanceTheme.colors.onTertiaryContainer,
            ),
            modifier = GlanceModifier
                .cornerRadius(8.dp)
                .background(GlanceTheme.colors.tertiaryContainer)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
    } else {
        Text(
            text,
            style = TextStyle(fontSize = 12.sp, color = GlanceTheme.colors.outline),
            modifier = GlanceModifier.padding(horizontal = 4.dp),
        )
    }
}
