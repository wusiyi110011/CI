package com.wsy.ci.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.wsy.ci.MainActivity
import com.wsy.ci.R
import com.wsy.ci.core.widget.TodayWidgetUi

/**
 * 2×1 小组件：只关心「现在该干什么」。位置太小放不下二次确认，
 * 结束按钮点一次就结算（要慎重操作请用大组件或进应用）。
 */
@Composable
internal fun TimerWidgetContent(ui: TodayWidgetUi) {
    Column(
        modifier = GlanceModifier.fillMaxSize()
            .background(CiWidgetPalette.surfaceContainerLow)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val focusing = ui.focusing
        val next = ui.rows.firstOrNull { it.showStartButton }
        when {
            focusing != null -> {
                Text(
                    focusing.title,
                    maxLines = 1,
                    style = TextStyle(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = GlanceTheme.colors.onSurface,
                    ),
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = GlanceModifier.padding(top = 6.dp)
                        .clickable(actionRunCallback<StopTimerAction>()),
                ) {
                    Image(
                        provider = ImageProvider(R.drawable.ic_ci_stop),
                        contentDescription = null,
                        modifier = GlanceModifier.width(16.dp).height(16.dp),
                    )
                    Text(
                        "结束",
                        style = TextStyle(fontSize = 13.sp, color = GlanceTheme.colors.error),
                        modifier = GlanceModifier.padding(start = 4.dp),
                        maxLines = 1,
                    )
                }
            }

            next != null -> {
                Text(
                    next.title,
                    maxLines = 1,
                    style = TextStyle(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = GlanceTheme.colors.onSurface,
                    ),
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = GlanceModifier.padding(top = 6.dp)
                        .clickable(
                            actionRunCallback<StartTimerAction>(
                                actionParametersOf(
                                    TASK_ID_KEY to next.taskId,
                                    TASK_TITLE_KEY to next.title,
                                )
                            )
                        ),
                ) {
                    Image(
                        provider = ImageProvider(R.drawable.ic_ci_focus_timer),
                        contentDescription = null,
                        modifier = GlanceModifier.width(16.dp).height(16.dp),
                    )
                    Text(
                        "开始",
                        style = TextStyle(fontSize = 13.sp, color = GlanceTheme.colors.onSurfaceVariant),
                        modifier = GlanceModifier.padding(start = 4.dp),
                        maxLines = 1,
                    )
                }
            }

            else -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = GlanceModifier.clickable(actionStartActivity<MainActivity>()),
            ) {
                Text(
                    "今日无待办",
                    style = TextStyle(
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        color = GlanceTheme.colors.onSurfaceVariant,
                    ),
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = GlanceModifier.padding(top = 2.dp),
                ) {
                    Image(
                        provider = ImageProvider(R.drawable.ic_ci_open),
                        contentDescription = null,
                        modifier = GlanceModifier.width(16.dp).height(16.dp),
                    )
                    Text(
                        "打开应用",
                        style = TextStyle(
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            color = GlanceTheme.colors.onSurfaceVariant,
                        ),
                        modifier = GlanceModifier.padding(start = 4.dp),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
