package com.wsy.ci.core.util

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object TimeFormat {
    private val dateFmt = DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA)
    private val shortDateFmt = DateTimeFormatter.ofPattern("M/d", Locale.CHINA)
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")

    fun minuteOfDay(minute: Int): String = "%02d:%02d".format(minute / 60, minute % 60)

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
