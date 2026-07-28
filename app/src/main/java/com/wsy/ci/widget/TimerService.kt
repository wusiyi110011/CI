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
                scope.launch {
                    val session = app().container.timerRepository.startSession(taskId, questId)
                    startForeground(NOTIFICATION_ID, buildNotification(title, session.startAt))
                }
            }
            ACTION_STOP -> {
                scope.launch {
                    app().container.timerRepository.stopSession(FocusOutcome.COMPLETED)
                    stopSelfCleanly()
                }
            }
            else -> stopSelfCleanly()
        }
        return START_STICKY
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
            Intent(this, TimerService::class.java).setAction(ACTION_STOP),
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
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "focus_timer"
        private const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.wsy.ci.timer.START"
        const val ACTION_STOP = "com.wsy.ci.timer.STOP"
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
            context.startService(
                Intent(context, TimerService::class.java).setAction(ACTION_STOP)
            )
        }
    }
}
