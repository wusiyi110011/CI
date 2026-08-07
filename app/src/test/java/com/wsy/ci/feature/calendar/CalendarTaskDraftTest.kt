package com.wsy.ci.feature.calendar

import com.wsy.ci.core.timeline.MINUTES_PER_DAY
import org.junit.Assert.assertEquals
import org.junit.Test

class CalendarTaskDraftTest {

    @Test
    fun `深夜新建仍落在当前选中日`() {
        val selectedDay = 20_000L

        val draft = newTaskDraft(selectedDay, currentMinute = 23 * 60 + 59)

        assertEquals(selectedDay, draft.epochDay)
        assertEquals(MINUTES_PER_DAY - 60, draft.startMinute)
        assertEquals(MINUTES_PER_DAY, draft.endMinute)
    }
}
