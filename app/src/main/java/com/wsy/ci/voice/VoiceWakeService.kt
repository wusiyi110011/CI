/*
 * Copyright 2026 吴思毅
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.wsy.ci.voice

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.wsy.ci.CiApp
import com.wsy.ci.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch

/** 用户显式开启后常驻的麦克风前台服务；通知始终展示当前唤醒短语和停止入口。 */
class VoiceWakeService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var runtimeJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            app().container.appSettings.setWakeWordEnabled(false)
            stopSelfCleanly()
            return START_NOT_STICKY
        }
        if (!app().container.appSettings.wakeWordEnabled.value) {
            stopSelfCleanly()
            return START_NOT_STICKY
        }
        startMicrophoneForeground()
        val previous = runtimeJob
        runtimeJob = scope.launch {
            previous?.cancelAndJoin()
            if (app().container.appSettings.wakeWordEnabled.value) {
                app().container.voiceWakeRuntime.run().onFailure {
                    app().container.appSettings.setWakeWordEnabled(false)
                    stopSelfCleanly()
                }
            }
        }
        return START_STICKY
    }

    private fun startMicrophoneForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "语音唤醒", NotificationManager.IMPORTANCE_LOW),
        )
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, VoiceWakeService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val phrase = app().container.appSettings.wakePhrase.value
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("语音助手正在监听")
            .setContentText("说出“$phrase”后开始讲话")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "停止监听", stop)
            .build()
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
        )
    }

    private fun app(): CiApp = applicationContext as CiApp

    private fun stopSelfCleanly() {
        app().container.voiceWakeRuntime.stop()
        runtimeJob?.cancel()
        runtimeJob = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        app().container.voiceWakeRuntime.stop()
        scope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "voice_wake"
        private const val NOTIFICATION_ID = 1002
        private const val ACTION_START = "com.wsy.ci.voice.WAKE_START"
        private const val ACTION_STOP = "com.wsy.ci.voice.WAKE_STOP"

        fun start(context: Context) {
            context.startForegroundService(
                Intent(context, VoiceWakeService::class.java).setAction(ACTION_START),
            )
        }

        /** 暂停服务但保留用户开关，手动按住说话结束后可以自动恢复。 */
        fun pause(context: Context) {
            context.stopService(Intent(context, VoiceWakeService::class.java))
        }
    }
}
