package com.wsy.ci.widget

import android.content.Context
import androidx.glance.appwidget.updateAll

/** 小组件刷新入口：数据变化（开始/结束计时、任务增删改）后调用。 */
object CiWidgetUpdater {
    suspend fun updateAll(context: Context) {
        CiTodayWidget().updateAll(context)
        CiTimerWidget().updateAll(context)
    }
}
