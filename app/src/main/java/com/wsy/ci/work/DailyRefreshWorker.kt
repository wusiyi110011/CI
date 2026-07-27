package com.wsy.ci.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.wsy.ci.CiApp
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/** 每日 00:00 附近执行：刷新今日商店精选。后续里程碑在此追加连击结算、次日排程。 */
class DailyRefreshWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? CiApp ?: return Result.failure()
        app.container.shopRepository.ensureTodayPicks()
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "daily-refresh"

        fun schedule(context: Context) {
            val now = LocalDateTime.now()
            val nextMidnight = LocalDate.now().plusDays(1).atStartOfDay()
            val initialDelay = Duration.between(now, nextMidnight)
            val request = PeriodicWorkRequestBuilder<DailyRefreshWorker>(Duration.ofDays(1))
                .setInitialDelay(initialDelay)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }
    }
}
