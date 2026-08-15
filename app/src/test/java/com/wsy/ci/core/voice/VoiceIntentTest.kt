package com.wsy.ci.core.voice

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceIntentTest {

    private val day1 = LocalDate.of(2026, 8, 16).toEpochDay()
    private val day2 = LocalDate.of(2026, 8, 17).toEpochDay()

    @Test
    fun `BlockTime转ParsedBlocker保留日期时间与原因`() {
        val intent = VoiceIntent.BlockTime(
            spans = listOf(TimeSpan(epochDay = day1, startMinute = 9 * 60, endMinute = 10 * 60 + 30)),
            reason = "要去开会",
        )
        val parsed = intent.toParsedBlockers()
        assertEquals(1, parsed.size)
        assertEquals("2026-08-16", parsed[0].date)
        assertEquals("09:00", parsed[0].start)
        assertEquals("10:30", parsed[0].end)
        assertEquals("要去开会", parsed[0].title)
    }

    @Test
    fun `多个时间段各自转成一条ParsedBlocker`() {
        val intent = VoiceIntent.BlockTime(
            spans = listOf(
                TimeSpan(epochDay = day1, startMinute = 8 * 60, endMinute = 9 * 60),
                TimeSpan(epochDay = day2, startMinute = 14 * 60, endMinute = 18 * 60),
            ),
            reason = "临时安排",
        )
        val parsed = intent.toParsedBlockers()
        assertEquals(2, parsed.size)
        assertEquals("2026-08-16", parsed[0].date)
        assertEquals("2026-08-17", parsed[1].date)
        assertEquals("14:00", parsed[1].start)
        assertEquals("18:00", parsed[1].end)
    }
}
