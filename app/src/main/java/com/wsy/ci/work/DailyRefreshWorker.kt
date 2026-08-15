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
