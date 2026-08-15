package com.wsy.ci.voice

import com.wsy.ci.core.data.TimerRepository
import com.wsy.ci.core.db.CiDatabase
import com.wsy.ci.core.db.TaskEntity
import com.wsy.ci.core.db.TaskStatus
import com.wsy.ci.core.voice.VoiceTarget
import com.wsy.ci.core.voice.VoiceTargetKind
import java.time.LocalDate
import kotlinx.coroutines.flow.first

/** 语音意图的落地执行：候选清单、日程查询、开始任务。widget 刷新由调用方负责。 */
class VoiceCommandExecutor(
    private val db: CiDatabase,
    private val timer: TimerRepository,
) {
    /** 供 [com.wsy.ci.core.voice.VoiceTargetMatcher] 匹配「开始某任务」用的候选清单。 */
    suspend fun candidates(): List<VoiceTarget> {
        val today = LocalDate.now().toEpochDay()
        val tasks = db.taskDao().byRange(today - CANDIDATE_LOOKBACK_DAYS, today + CANDIDATE_LOOKAHEAD_DAYS)
            .filter { it.status == TaskStatus.PLANNED }
            .map { VoiceTarget(it.id, it.title, VoiceTargetKind.TASK) }
        val quests = db.questDao().observeAll().first().map { VoiceTarget(it.id, it.title, VoiceTargetKind.QUEST) }
        val domains = db.domainDao().observeAll().first().map { VoiceTarget(it.id, it.name, VoiceTargetKind.DOMAIN) }
        return tasks + quests + domains
    }

    suspend fun querySchedule(from: Long, to: Long): List<TaskEntity> = db.taskDao().byRange(from, to)

    suspend fun startTask(target: VoiceTarget): Result<Unit> = when (target.kind) {
        VoiceTargetKind.TASK -> {
            timer.startSession(taskId = target.id)
            Result.success(Unit)
        }
        VoiceTargetKind.QUEST -> {
            val today = LocalDate.now().toEpochDay()
            val todayTask = db.taskDao().byRange(today, today)
                .firstOrNull { it.questId == target.id && it.status == TaskStatus.PLANNED }
            if (todayTask != null) {
                timer.startSession(taskId = todayTask.id)
            } else {
                timer.startSession(taskId = null, questId = target.id)
            }
            Result.success(Unit)
        }
        VoiceTargetKind.DOMAIN ->
            Result.failure(IllegalArgumentException("领域不能直接开始，换成具体任务名试试"))
    }

    private companion object {
        const val CANDIDATE_LOOKBACK_DAYS = 7L
        const val CANDIDATE_LOOKAHEAD_DAYS = 14L
    }
}
