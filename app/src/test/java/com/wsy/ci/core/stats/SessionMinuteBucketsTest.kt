package com.wsy.ci.core.stats

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionMinuteBucketsTest {

    @Test
    fun `跨午夜分钟归入各自日期与小时`() {
        val start = Instant.parse("2026-08-07T23:59:30Z").toEpochMilli()

        val buckets = sessionMinuteBuckets(start, minutes = 3, zoneId = ZoneOffset.UTC)

        assertEquals(listOf(23, 0, 0), buckets.map { it.hour })
        assertEquals(
            listOf(
                LocalDate.parse("2026-08-07").toEpochDay(),
                LocalDate.parse("2026-08-08").toEpochDay(),
                LocalDate.parse("2026-08-08").toEpochDay(),
            ),
            buckets.map { it.epochDay },
        )
        assertEquals(listOf(4, 5, 5), buckets.map { it.dayOfWeekIndex })
    }

    @Test
    fun `零分钟不产生统计桶`() {
        assertEquals(emptyList<SessionMinuteBucket>(), sessionMinuteBuckets(0L, 0, ZoneOffset.UTC))
    }
}
