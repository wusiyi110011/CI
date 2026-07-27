package com.wsy.ci.core.data

import com.wsy.ci.core.db.CiDatabase
import com.wsy.ci.core.db.LedgerEntity
import com.wsy.ci.core.db.LedgerType
import com.wsy.ci.core.db.QuestType
import com.wsy.ci.core.db.SessionEntity
import com.wsy.ci.core.db.TaskStatus
import com.wsy.ci.core.economy.Difficulty
import com.wsy.ci.core.economy.Economy
import com.wsy.ci.core.economy.FocusOutcome
import java.time.LocalDate
import kotlin.math.roundToInt

/** 一次结算的产出，用于 UI 展示（入账吐司 / 升级庆祝）。 */
data class Settlement(
    val minutes: Int,
    val rewardCi: Long,
    val expGained: Long,
    val domainId: Long?,
    val newLevel: Int?,
    val levelUpRewardCi: Long,
)

/**
 * 计时与结算：开始/结束 session，结束时按 economy 公式发 CI 币、
 * 结算领域经验并处理升级奖励、维护支线连击。
 */
class TimerRepository(private val db: CiDatabase) {

    suspend fun runningSession(): SessionEntity? = db.sessionDao().openSession()

    fun observeRunningSession() = db.sessionDao().observeOpenSession()

    /** 开始计时。若已有进行中的 session 则直接返回它（幂等）。 */
    suspend fun startSession(taskId: Long?): SessionEntity {
        db.sessionDao().openSession()?.let { return it }
        val task = taskId?.let { db.taskDao().byId(it) }
        val session = SessionEntity(
            taskId = task?.id,
            domainId = task?.domainId,
            startAt = System.currentTimeMillis(),
        )
        val id = db.sessionDao().insert(session)
        task?.let { db.taskDao().update(it.copy(status = TaskStatus.RUNNING)) }
        return session.copy(id = id)
    }

    /** 结束计时并结算。focus 为专注结果（放弃 0.5 / 超时 0.9 / 按时 1.0）。 */
    suspend fun stopSession(focus: FocusOutcome): Settlement? {
        val session = db.sessionDao().openSession() ?: return null
        val now = System.currentTimeMillis()
        val minutes = ((now - session.startAt) / 60_000.0).roundToInt().coerceAtLeast(0)
        val task = session.taskId?.let { db.taskDao().byId(it) }
        val difficulty = task?.difficulty ?: Difficulty.NORMAL

        val streakDays = updateStreakAndGet(task?.questId, focus)
        val reward = Economy.taskReward(minutes, difficulty, focus, streakDays)
        val exp = Economy.expGain(minutes, difficulty)

        db.sessionDao().update(
            session.copy(endAt = now, focus = focus, rewardCi = reward, expGained = exp)
        )
        task?.let {
            val done = if (focus == FocusOutcome.ABANDONED) TaskStatus.PLANNED else TaskStatus.DONE
            db.taskDao().update(it.copy(status = done))
        }
        if (reward > 0) {
            db.ledgerDao().insert(
                LedgerEntity(
                    amount = reward,
                    type = LedgerType.EARN_TASK,
                    refId = session.id,
                    note = task?.title ?: "自由专注",
                )
            )
        }
        val levelUp = settleExp(session.domainId ?: task?.domainId, exp)
        return Settlement(
            minutes = minutes,
            rewardCi = reward,
            expGained = exp,
            domainId = session.domainId ?: task?.domainId,
            newLevel = levelUp?.first,
            levelUpRewardCi = levelUp?.second ?: 0,
        )
    }

    /** 经验入账；若跨过升级门槛，发升级奖励并返回 (新等级, 奖励)。 */
    private suspend fun settleExp(domainId: Long?, exp: Long): Pair<Int, Long>? {
        if (domainId == null || exp <= 0) return null
        val domain = db.domainDao().byId(domainId) ?: return null
        val oldLevel = Economy.levelForExp(domain.totalExp)
        val newLevel = Economy.levelForExp(domain.totalExp + exp)
        db.domainDao().addExp(domainId, exp)
        if (newLevel <= oldLevel) return null
        val bonus = Economy.levelUpReward(newLevel)
        db.ledgerDao().insert(
            LedgerEntity(
                amount = bonus,
                type = LedgerType.EARN_LEVELUP,
                refId = domainId,
                note = "「${domain.name}」升至 $newLevel 级",
            )
        )
        return newLevel to bonus
    }

    /** 支线打卡：今天首次完成则连击 +1（昨天没打卡则重置为 1）。返回结算用连击天数。 */
    private suspend fun updateStreakAndGet(questId: Long?, focus: FocusOutcome): Int {
        if (questId == null || focus == FocusOutcome.ABANDONED) return 0
        val quest = db.questDao().byId(questId) ?: return 0
        if (quest.type != QuestType.SIDE) return 0
        val today = LocalDate.now().toEpochDay()
        val newStreak = when (quest.lastDoneEpochDay) {
            today -> return quest.streakDays
            today - 1 -> quest.streakDays + 1
            else -> 1
        }
        db.questDao().update(
            quest.copy(
                streakDays = newStreak,
                bestStreak = maxOf(quest.bestStreak, newStreak),
                lastDoneEpochDay = today,
            )
        )
        return newStreak
    }
}
