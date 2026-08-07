package com.wsy.ci.core.stats

import java.time.Instant
import java.time.ZoneId

/** 一分钟在本地日历中的归属，用于跨小时、跨午夜统计。 */
data class SessionMinuteBucket(
    val epochDay: Long,
    val dayOfWeekIndex: Int,
    val hour: Int,
)

/**
 * 从真实开始时刻起逐分钟切桶。使用 Instant 推进，跨午夜和夏令时切换都由时区规则处理。
 */
fun sessionMinuteBuckets(
    startAt: Long,
    minutes: Int,
    zoneId: ZoneId = ZoneId.systemDefault(),
): List<SessionMinuteBucket> = List(minutes.coerceAtLeast(0)) { offset ->
    val local = Instant.ofEpochMilli(startAt)
        .plusSeconds(offset * 60L)
        .atZone(zoneId)
    SessionMinuteBucket(
        epochDay = local.toLocalDate().toEpochDay(),
        dayOfWeekIndex = local.dayOfWeek.value - 1,
        hour = local.hour,
    )
}
