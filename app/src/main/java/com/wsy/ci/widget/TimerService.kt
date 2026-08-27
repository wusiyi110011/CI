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

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.wsy.ci.CiApp
import com.wsy.ci.MainActivity
import com.wsy.ci.core.economy.FocusOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 专注计时前台服务：保证计时期间进程不被杀，通知栏实时显示已计时时长，
 * 并提供「完成」快捷动作。真实计时状态以数据库 open session 为准，服务只是保活与展示。
 */
class TimerService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1).takeIf { it >= 0 }
                val questId = intent.getLongExtra(EXTRA_QUEST_ID, -1).takeIf { it >= 0 }
                val title = intent.getStringExtra(EXTRA_TITLE) ?: "专注中"
                // startForegroundService 要求 5 秒内进入前台，必须先于异步写库同步调用，
                // 否则数据库繁忙时会抛 ForegroundServiceDidNotStartException。
                // 计时起点先用当前时间占位，写库完成后再用真实 startAt 校准 chronometer。
                startForeground(NOTIFICATION_ID, buildNotification(title, System.currentTimeMillis()))
                scope.launch {
                    val session = app().container.timerRepository.startSession(taskId, questId)
                    getSystemService(NotificationManager::class.java)
                        .notify(NOTIFICATION_ID, buildNotification(title, session.startAt))
                    // 写库完成后再刷小组件，调用方不用各自处理「服务异步写库」的竞态
                    CiWidgetUpdater.updateAll(this@TimerService)
                }
            }
            ACTION_COMPLETE -> {
                scope.launch {
                    app().container.timerRepository.stopSession(FocusOutcome.COMPLETED)
                    CiWidgetUpdater.updateAll(this@TimerService)
                    stopSelfCleanly()
                }
            }
            else -> stopSelfCleanly()
        }
        // 计时真源是数据库 open session（见类注释）；服务被杀后由用户操作（界面/widget/语音）
        // 重新拉起即可，系统重启一个拿不到 intent 的空服务只会走 stopSelfCleanly，没有意义。
        return START_NOT_STICKY
    }

    private fun app() = applicationContext as CiApp

    private fun stopSelfCleanly() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(title: String, startAt: Long): android.app.Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "专注计时", NotificationManager.IMPORTANCE_LOW)
        )
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, TimerService::class.java).setAction(ACTION_COMPLETE),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title)
            .setContentText("专注计时中")
            .setUsesChronometer(true)
            .setWhen(startAt)
            .setOngoing(true)
            .setContentIntent(openApp)
            .addAction(android.R.drawable.ic_media_pause, "完成", stop)
            .build()
    }

    override fun onDestroy() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "focus_timer"
        private const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.wsy.ci.timer.START"
        /** 仅供通知栏的「完成」动作使用：结算当前专注后停止服务。 */
        const val ACTION_COMPLETE = "com.wsy.ci.timer.COMPLETE"
        const val EXTRA_TASK_ID = "taskId"
        const val EXTRA_QUEST_ID = "questId"
        const val EXTRA_TITLE = "title"

        /** [questId] 只在没有具体任务、直接对着任务线打卡时才需要传。 */
        fun start(context: Context, taskId: Long?, title: String, questId: Long? = null) {
            val intent = Intent(context, TimerService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_TASK_ID, taskId ?: -1L)
                .putExtra(EXTRA_QUEST_ID, questId ?: -1L)
                .putExtra(EXTRA_TITLE, title)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            // 应用内、语音和小组件都已经先完成了 Repository 结算；这里只撤掉保活服务。
            // 不能再发送「完成」动作，否则会排入第二次 stopSession，造成界面与任务延迟同步。
            context.stopService(Intent(context, TimerService::class.java))
        }
    }
}
