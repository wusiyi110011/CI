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
import android.widget.Toast
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState
import com.wsy.ci.CiApp
import com.wsy.ci.core.economy.FocusOutcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal val TASK_ID_KEY = ActionParameters.Key<Long>("taskId")
internal val TASK_TITLE_KEY = ActionParameters.Key<String>("taskTitle")

/**
 * 「结束专注」的二次确认时刻。Glance 里没有定时器，所以确认态不会自动过期，
 * 而是在下一次点击时按这个时刻判定：超窗就重新武装，不会误结算。
 */
internal val STOP_ARMED_AT_KEY = longPreferencesKey("stopArmedAt")

/** 二次确认的有效期。 */
internal const val STOP_CONFIRM_WINDOW_MILLIS = 8_000L

/** 判断确认态是否仍然有效，渲染与点击两侧共用一套口径。 */
internal fun isStopArmed(armedAt: Long?, now: Long): Boolean =
    armedAt != null && armedAt > 0L && now - armedAt <= STOP_CONFIRM_WINDOW_MILLIS

/** 开始一段专注。计时是全局单例，调用方需保证此刻没有别的 session 在跑。 */
class StartTimerAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val taskId = parameters[TASK_ID_KEY]
        val title = parameters[TASK_TITLE_KEY] ?: "专注中"
        // 上一轮遗留的确认态清掉，免得新 session 的第一次点击就被当成确认。
        updateAppWidgetState(context, glanceId) { it.remove(STOP_ARMED_AT_KEY) }
        TimerService.start(context, taskId, title)
        CiWidgetUpdater.updateAll(context)
    }
}

/** 已有专注在跑时点其他任务的「开始」：只提示，不排队、不打断当前计时。 */
class BlockedStartAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        withContext(Dispatchers.Main) {
            Toast.makeText(context, "先结束当前专注任务", Toast.LENGTH_SHORT).show()
        }
    }
}

/**
 * 今日日程小组件的「结束专注」：第一次点击进入确认态，第二次（窗口内）才真结算。
 * 桌面上误触代价高——一旦结算就写了 session 和流水，所以这里多挡一道。
 */
class ConfirmStopAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        var confirmed = false
        updateAppWidgetState(context, glanceId) { prefs ->
            val now = System.currentTimeMillis()
            confirmed = isStopArmed(prefs[STOP_ARMED_AT_KEY], now)
            if (confirmed) prefs.remove(STOP_ARMED_AT_KEY) else prefs[STOP_ARMED_AT_KEY] = now
        }
        if (confirmed) {
            stopAndSettle(context)
        } else {
            CiTodayWidget().update(context, glanceId)
        }
    }
}

/** 2×1 计时小组件上的结束：位置小、只有一个按钮，点了直接结算。 */
class StopTimerAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        stopAndSettle(context)
    }
}

/**
 * 结束并结算。奖励公式全在 [com.wsy.ci.core.data.TimerRepository.stopSession] 里，
 * 小组件只负责触发，绝不自己算。默认按「按时完成」结算，要选其他结果请进应用。
 */
private suspend fun stopAndSettle(context: Context) {
    val container = (context.applicationContext as CiApp).container
    container.timerRepository.stopSession(FocusOutcome.COMPLETED)
    TimerService.stop(context)
    CiWidgetUpdater.updateAll(context)
}
