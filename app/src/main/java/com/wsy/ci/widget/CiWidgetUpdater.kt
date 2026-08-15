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
import androidx.glance.appwidget.updateAll

/** 小组件刷新入口：数据变化（开始/结束计时、任务增删改）后调用。 */
object CiWidgetUpdater {
    suspend fun updateAll(context: Context) {
        CiTodayWidget().updateAll(context)
        CiTimerWidget().updateAll(context)
    }
}
