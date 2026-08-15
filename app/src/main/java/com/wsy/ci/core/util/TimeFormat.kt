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

package com.wsy.ci.core.util

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import com.wsy.ci.core.timeline.MINUTES_PER_DAY
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

object TimeFormat {
    private val dateFmt = DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA)
    private val shortDateFmt = DateTimeFormatter.ofPattern("M/d", Locale.CHINA)
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")

    /**
     * 当日分钟数 → 时刻文案。跨天的时间块 minute 会超过一天（23:00 起的三小时任务
     * 结束在 26:00），这时补上「次日」前缀，读起来才是 02:00 而不是 26:00。
     */
    fun minuteOfDay(minute: Int): String {
        val days = Math.floorDiv(minute, MINUTES_PER_DAY)
        val inDay = Math.floorMod(minute, MINUTES_PER_DAY)
        val clock = "%02d:%02d".format(inDay / 60, inDay % 60)
        return when (days) {
            0 -> clock
            1 -> "次日 $clock"
            else -> "${days}天后 $clock"
        }
    }

    fun date(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).format(dateFmt)

    fun shortDate(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).format(shortDateFmt)

    fun clock(epochMillis: Long): String =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault())
            .format(timeFmt)

    fun duration(minutes: Int): String = when {
        minutes < 60 -> "${minutes}分钟"
        minutes % 60 == 0 -> "${minutes / 60}小时"
        else -> "${minutes / 60}小时${minutes % 60}分"
    }

    /** 毫秒时长 → 分钟，四舍五入，与结算时的口径一致（负数按 0 算）。 */
    fun millisToMinutes(millis: Long): Int =
        (millis / 60_000.0).roundToInt().coerceAtLeast(0)

    fun elapsed(millis: Long): String {
        val totalSec = millis / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    }

    /** epochMillis → 当日分钟数（本地时区），用于把 session 画到时间线上。 */
    fun millisToMinuteOfDay(epochMillis: Long): Int {
        val t = LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault())
        return t.hour * 60 + t.minute
    }

    fun millisToEpochDay(epochMillis: Long): Long =
        Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()

    fun dayStartMillis(epochDay: Long): Long =
        LocalDate.ofEpochDay(epochDay).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    fun dayEndMillis(epochDay: Long): Long = dayStartMillis(epochDay + 1) - 1
}

/** 当前本地日期；保持收集时会在每次跨过午夜后自动发出新值。 */
fun currentEpochDayFlow(): Flow<Long> = flow {
    while (true) {
        val today = LocalDate.now()
        emit(today.toEpochDay())
        val untilMidnight = TimeFormat.dayStartMillis(today.toEpochDay() + 1) -
            System.currentTimeMillis()
        delay(untilMidnight.coerceAtLeast(1_000L))
    }
}
