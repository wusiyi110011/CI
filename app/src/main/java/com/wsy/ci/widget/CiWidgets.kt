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
