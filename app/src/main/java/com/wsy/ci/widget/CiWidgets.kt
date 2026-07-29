package com.wsy.ci.widget

import android.content.Context
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.glance.GlanceId
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import kotlinx.coroutines.flow.first

/** 大组件：今日全部安排，逐条可一键开始；有专注在跑时切到计时态。 */
class CiTodayWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val flow = todayUiFlow(context)
        val initial = flow.first()
        provideContent {
            val ui by flow.collectAsState(initial)
            GlanceTheme(colors = CiGlanceColors) {
                TodayWidgetContent(ui)
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
        val flow = todayUiFlow(context)
        val initial = flow.first()
        provideContent {
            val ui by flow.collectAsState(initial)
            GlanceTheme(colors = CiGlanceColors) {
                TimerWidgetContent(ui)
            }
        }
    }
}

class CiTimerWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CiTimerWidget()
}
