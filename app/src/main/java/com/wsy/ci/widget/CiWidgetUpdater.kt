package com.wsy.ci.widget

import android.content.Context

/** 小组件刷新入口。M2 接入 Glance 后在此触发所有小组件更新；当前为占位。 */
object CiWidgetUpdater {
    suspend fun updateAll(context: Context) {
        // M2: GlanceAppWidgetManager 更新今日时间线/当前任务小组件
    }
}
