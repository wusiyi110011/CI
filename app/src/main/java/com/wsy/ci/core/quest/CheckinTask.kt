package com.wsy.ci.core.quest

import com.wsy.ci.core.db.QuestEntity
import com.wsy.ci.core.db.TaskEntity
import com.wsy.ci.core.economy.Difficulty

/** 当日最后一分钟：深夜打卡时结束时间截到这里，不跨天。 */
private const val LAST_MINUTE_OF_DAY = 24 * 60 - 1

/** 打卡任务的默认时长。真实时长以 session 计时为准，这只是计划轨上占的格子。 */
const val CHECKIN_DEFAULT_MINUTES = 30

/**
 * 支线点「开始打卡」时即时生成的时间块。
 *
 * 支线是习惯，本来就不会提前排时间块，但没有具体任务的话这次投入
 * 既不出现在计划轨上，也进不了任务线详情的「具体任务」列表，
 * 事后完全查不到。所以打卡时就地补一个从此刻起算的任务。
 *
 * [QuestEntity.parentQuestId] 在这里刻意不参与：任务挂的是支线自己，
 * 挂到父主线上会把支线的打卡记录混进主线的完成度里。
 */
fun checkinTaskOf(
    quest: QuestEntity,
    nowEpochDay: Long,
    nowMinute: Int,
    durationMinutes: Int = CHECKIN_DEFAULT_MINUTES,
): TaskEntity {
    val endMinute = minOf(nowMinute + durationMinutes.coerceAtLeast(1), LAST_MINUTE_OF_DAY)
        .coerceAtLeast(nowMinute + 1)
    return TaskEntity(
        title = quest.title,
        epochDay = nowEpochDay,
        startMinute = nowMinute,
        endMinute = endMinute,
        domainId = quest.domainId,
        questId = quest.id,
        difficulty = Difficulty.NORMAL,
    )
}
