package com.wsy.ci.widget

import android.content.Context
import com.wsy.ci.CiApp
import com.wsy.ci.core.util.TimeFormat
import com.wsy.ci.core.widget.TodayWidgetModel
import com.wsy.ci.core.widget.TodayWidgetUi
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow

/**
 * 小组件要显示的内容，随 Room 的三条 Flow 自动更新。
 *
 * 必须是 Flow 而不是「读一次快照」：Glance 的 composition 是长驻的，`updateAll()`
 * 只触发重组、不重跑 `provideGlance`——快照式取数会把小组件卡在旧数据上
 * （表现为按钮状态变了但任务列表不动）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun todayUiFlow(context: Context): Flow<TodayWidgetUi> {
    val container = (context.applicationContext as CiApp).container
    return currentDayFlow().flatMapLatest { today ->
        combine(
            container.db.taskDao().observeByDay(today),
            container.db.sessionDao().observeOpenSession(),
            container.db.sessionDao().observeByTimeRange(
                TimeFormat.dayStartMillis(today),
                TimeFormat.dayEndMillis(today),
            ),
        ) { tasks, open, sessions ->
            val focusedMillis = sessions.sumOf { s -> s.endAt?.let { it - s.startAt } ?: 0L }
            TodayWidgetModel.build(
                tasks = tasks,
                openSessionTaskId = open?.taskId,
                openSessionStartAt = open?.startAt,
                focusedMinutesToday = TimeFormat.millisToMinutes(focusedMillis),
            )
        }
    }
}

/**
 * 当前日期，跨零点自动再发一次。
 *
 * composition 一驻就是几小时，「今天」不能只在开头算一次，
 * 否则午夜之后小组件会一直停留在昨天的安排上。
 */
private fun currentDayFlow(): Flow<Long> = flow {
    while (true) {
        val today = LocalDate.now()
        emit(today.toEpochDay())
        val untilMidnight = TimeFormat.dayStartMillis(today.toEpochDay() + 1) - System.currentTimeMillis()
        delay(untilMidnight.coerceAtLeast(1_000L))
    }
}
