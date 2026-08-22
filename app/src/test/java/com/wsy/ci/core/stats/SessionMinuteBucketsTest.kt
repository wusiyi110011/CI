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

    @Test
    fun `跨周期专注会裁剪到当前周期边界`() {
        val rangeStart = Instant.parse("2026-09-01T00:00:00Z").toEpochMilli()
        val rangeEnd = Instant.parse("2026-10-01T00:00:00Z").toEpochMilli()
        val sessionStart = Instant.parse("2026-08-31T23:30:00Z").toEpochMilli()
        val sessionEnd = Instant.parse("2026-09-01T00:30:00Z").toEpochMilli()

        val slice = intersectSessionTime(sessionStart, sessionEnd, rangeStart, rangeEnd)

        assertEquals(rangeStart, slice?.startAt)
        assertEquals(sessionEnd, slice?.endAt)
    }

    @Test
    fun `不与周期相交的专注不会进入统计`() {
        assertEquals(null, intersectSessionTime(100, 200, 300, 400))
        assertEquals(null, intersectSessionTime(400, 500, 300, 400))
    }
}
