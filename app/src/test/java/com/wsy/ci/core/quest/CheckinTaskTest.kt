package com.wsy.ci.core.quest

import com.wsy.ci.core.db.QuestEntity
import com.wsy.ci.core.db.QuestType
import com.wsy.ci.core.db.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CheckinTaskTest {

    private fun sideQuest(
        id: Long = 7,
        domainId: Long? = 3,
        parentQuestId: Long? = null,
    ) = QuestEntity(
        id = id,
        domainId = domainId,
        parentQuestId = parentQuestId,
        type = QuestType.SIDE,
        title = "每日剪辑实操打卡",
    )

    @Test
    fun `打卡任务从此刻起算默认时长并继承支线的名字与领域`() {
        val task = checkinTaskOf(sideQuest(), nowEpochDay = 20_300, nowMinute = 9 * 60 + 15)

        assertEquals("每日剪辑实操打卡", task.title)
        assertEquals(20_300, task.epochDay)
        assertEquals(9 * 60 + 15, task.startMinute)
        assertEquals(9 * 60 + 15 + CHECKIN_DEFAULT_MINUTES, task.endMinute)
        assertEquals(3L, task.domainId)
        assertEquals(TaskStatus.PLANNED, task.status)
    }

    @Test
    fun `打卡任务挂在支线自己身上而不是它归属的主线`() {
        val task = checkinTaskOf(
            sideQuest(id = 7, parentQuestId = 99),
            nowEpochDay = 20_300,
            nowMinute = 8 * 60,
        )

        assertEquals(7L, task.questId)
    }

    @Test
    fun `深夜打卡结束时间越过零点`() {
        val task = checkinTaskOf(sideQuest(), nowEpochDay = 20_300, nowMinute = 23 * 60 + 50)

        assertEquals(24 * 60 + 20, task.endMinute)
        assertTrue(task.endMinute > task.startMinute)
    }

    @Test
    fun `起点已是当天最后一分钟时仍产出非空区间`() {
        val task = checkinTaskOf(sideQuest(), nowEpochDay = 20_300, nowMinute = 24 * 60 - 1)

        assertTrue(task.endMinute > task.startMinute)
    }
}
