/*
 * Copyright 2026 吴思毅
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.wsy.ci.core.data

import com.wsy.ci.core.db.CiDatabase
import com.wsy.ci.core.db.QuestEntity
import com.wsy.ci.core.db.SessionEntity
import com.wsy.ci.core.db.TaskEntity
import com.wsy.ci.core.economy.Economy
import com.wsy.ci.core.util.TimeFormat
import java.time.DayOfWeek
import java.time.LocalDate

/** 当前进行中的专注及其可展示目标。 */
data class VoiceCurrentFocus(
    val session: SessionEntity,
    val task: TaskEntity?,
    val quest: QuestEntity?,
) {
    val title: String
        get() = task?.title ?: quest?.title ?: "自由专注"
}

/** 语音查询用的打卡概览。 */
data class VoiceCheckinStats(
    val todayEpochDay: Long,
    val checkedInToday: Boolean,
    val streakDays: Int,
    val checkedInDays: Int,
)

/** 语音查询用的周统计概览。 */
data class VoiceWeeklyStats(
    val fromEpochDay: Long,
    val toEpochDay: Long,
    val focusMinutes: Int,
    val sessionCount: Int,
    val earnedCi: Long,
    val spentCi: Long,
    val checkinDays: Int,
)

/**
 * 语音高频查询的唯一数据入口。
 * 查询在这里做时间裁剪和流水聚合，技能只负责把稳定的数据格式化成口语结果。
 */
class VoiceStatsRepository(private val db: CiDatabase) {

    suspend fun currentFocus(): VoiceCurrentFocus? {
        val session = db.sessionDao().openSession() ?: return null
        val task = session.taskId?.let { db.taskDao().byId(it) }
        val questId = task?.questId ?: session.questId
        val quest = questId?.let { db.questDao().byId(it) }
        return VoiceCurrentFocus(session, task, quest)
    }

    suspend fun balance(): Long = db.ledgerDao().balance()

    suspend fun checkinStats(today: Long = LocalDate.now().toEpochDay()): VoiceCheckinStats {
        val days = db.ledgerDao().checkinEpochDaysSince(today - CHECKIN_LOOKBACK_DAYS)
            .toSet()
        return VoiceCheckinStats(
            todayEpochDay = today,
            checkedInToday = today in days,
            streakDays = Economy.checkinStreak(days, today),
            checkedInDays = days.count { it in (today - 6)..today },
        )
    }

    suspend fun weeklyStats(today: Long = LocalDate.now().toEpochDay()): VoiceWeeklyStats {
        val from = LocalDate.ofEpochDay(today).with(DayOfWeek.MONDAY).toEpochDay()
        val to = from + 6
        val startAt = TimeFormat.dayStartMillis(from)
        val endExclusive = TimeFormat.dayStartMillis(to + 1)
        val sessions = db.sessionDao().endedIntersecting(startAt, endExclusive)
        val minutes = sessions.sumOf { session ->
            val end = session.endAt ?: return@sumOf 0
            val clippedStart = maxOf(session.startAt, startAt)
            val clippedEnd = minOf(end, endExclusive)
            TimeFormat.millisToMinutes((clippedEnd - clippedStart).coerceAtLeast(0))
        }
        val ledger = db.ledgerDao().byTimeRange(startAt, endExclusive - 1)
        val checkinDays = ledger.asSequence()
            .filter { it.type.name == "EARN_STREAK" }
            .mapNotNull { it.refId }
            .filter { it in from..to }
            .toSet()
        return VoiceWeeklyStats(
            fromEpochDay = from,
            toEpochDay = to,
            focusMinutes = minutes,
            sessionCount = sessions.size,
            earnedCi = ledger.filter { it.amount > 0 }.sumOf { it.amount },
            spentCi = -ledger.filter { it.amount < 0 }.sumOf { it.amount },
            checkinDays = checkinDays.size,
        )
    }

    private companion object {
        const val CHECKIN_LOOKBACK_DAYS = 400L
    }
}
