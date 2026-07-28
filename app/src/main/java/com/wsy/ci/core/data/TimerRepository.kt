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
import com.wsy.ci.core.scheduler.alignedToNow
import com.wsy.ci.core.scheduler.endedAt
import com.wsy.ci.core.util.TimeFormat
import java.time.LocalDate
import kotlin.math.roundToInt

/** 连续打卡回溯窗口：一年多，足够覆盖任何现实中的连续记录。 */
private const val CHECKIN_LOOKBACK_DAYS = 400L

/** 一次结算的产出，用于 UI 展示（入账吐司 / 升级庆祝）。 */
data class Settlement(
    val minutes: Int,
    val rewardCi: Long,
    val expGained: Long,
    val domainId: Long?,
    val newLevel: Int?,
    val levelUpRewardCi: Long,
    /** 本次触发的连续打卡天数；0 表示今天已领过或本次是放弃。 */
    val checkinStreak: Int = 0,
    val checkinRewardCi: Long = 0,
)

/**
 * 计时与结算：开始/结束 session，结束时按 economy 公式发 CI 币、
 * 结算领域经验并处理升级奖励、维护支线连击。
 */
class TimerRepository(private val db: CiDatabase) {

    suspend fun runningSession(): SessionEntity? = db.sessionDao().openSession()

    fun observeRunningSession() = db.sessionDao().observeOpenSession()

    /**
     * 开始计时。若已有进行中的 session 则直接返回它（幂等）。
     *
     * 关联了任务时，把任务块对齐到此刻（见 [alignedToNow]）：从任务线详情或日程屏
     * 提前开工的任务会连日期一起搬到今天，计划轨于是反映真实开工时间。
     */
    suspend fun startSession(taskId: Long?, questId: Long? = null): SessionEntity {
        db.sessionDao().openSession()?.let { return it }
        val now = System.currentTimeMillis()
        val task = taskId?.let { db.taskDao().byId(it) }
        // 没有具体任务时（对着支线直接打卡），领域从任务线上取
        val quest = (task?.questId ?: questId)?.let { db.questDao().byId(it) }
        val session = SessionEntity(
            taskId = task?.id,
            domainId = task?.domainId ?: quest?.domainId,
            questId = quest?.id,
            startAt = now,
        )
        val id = db.sessionDao().insert(session)
        task?.let {
            val aligned = alignedToNow(
                task = it,
                nowEpochDay = TimeFormat.millisToEpochDay(now),
                nowMinute = TimeFormat.millisToMinuteOfDay(now),
            )
            db.taskDao().update(aligned.copy(status = TaskStatus.RUNNING))
        }
        return session.copy(id = id)
    }

    /**
     * 结束计时并结算。focus 为专注结果（放弃 0.5 / 超时 0.9 / 按时 1.0）。
     * [note] 是可选的完成描述：关联了任务就追加进 `task.note`，
     * 自由专注没有任务可写，则落到本次入账的流水备注里，不静默丢弃。
     */
    suspend fun stopSession(focus: FocusOutcome, note: String = ""): Settlement? {
        val session = db.sessionDao().openSession() ?: return null
        val now = System.currentTimeMillis()
        val minutes = ((now - session.startAt) / 60_000.0).roundToInt().coerceAtLeast(0)
        val task = session.taskId?.let { db.taskDao().byId(it) }
        val difficulty = task?.difficulty ?: Difficulty.NORMAL

        // 有任务以任务的归属为准，没有任务就用 session 上记的任务线（支线直接打卡）
        val streakDays = updateStreakAndGet(task?.questId ?: session.questId, focus)
        val reward = Economy.taskReward(minutes, difficulty, focus, streakDays)
        val exp = Economy.expGain(minutes, difficulty)

        db.sessionDao().update(
            session.copy(endAt = now, focus = focus, rewardCi = reward, expGained = exp)
        )
        val trimmedNote = note.trim()
        task?.let {
            val done = if (focus == FocusOutcome.ABANDONED) TaskStatus.PLANNED else TaskStatus.DONE
            // 计划轨记真实收工时刻：开工时已对齐到此刻，收工时把终点也收到此刻
            val ended = endedAt(
                task = it,
                endEpochDay = TimeFormat.millisToEpochDay(now),
                endMinute = TimeFormat.millisToMinuteOfDay(now),
            )
            db.taskDao().update(
                ended.copy(status = done, note = appendNote(it.note, trimmedNote))
            )
        }
        if (reward > 0) {
            db.ledgerDao().insert(
                LedgerEntity(
                    amount = reward,
                    type = LedgerType.EARN_TASK,
                    refId = session.id,
                    note = task?.title ?: trimmedNote.ifBlank { "自由专注" },
                )
            )
        }
        val levelUp = settleExp(session.domainId ?: task?.domainId, exp)
        val (checkinStreak, checkinReward) = settleCheckin(now, focus)
        return Settlement(
            minutes = minutes,
            rewardCi = reward,
            expGained = exp,
            domainId = session.domainId ?: task?.domainId,
            newLevel = levelUp?.first,
            levelUpRewardCi = levelUp?.second ?: 0,
            checkinStreak = checkinStreak,
            checkinRewardCi = checkinReward,
        )
    }

    /**
     * 删掉一次专注记录，并把它当初发出的 CI 币和领域经验一并撤回，账才对得上。
     *
     * 打卡奖不退：那是按「那天有没有专注」发的一次性奖励，跟着单条记录退会把
     * 连续打卡的判定搅乱。任务本身也不动——它是计划轨上的块，跟这条记录是两回事。
     */
    suspend fun deleteSession(sessionId: Long) {
        val session = db.sessionDao().byId(sessionId) ?: return
        db.ledgerDao().deleteTaskEarning(sessionId)
        session.domainId?.takeIf { session.expGained > 0 }?.let { domainId ->
            db.domainDao().addExp(domainId, -session.expGained)
        }
        db.sessionDao().deleteById(sessionId)
    }

    /**
     * 每日打卡结算：当天首次完成专注时发一笔定额奖，连续天数越长越高。
     * 返回 (连续天数, 奖励)，今天已领过或不该发时返回 0 to 0。
     *
     * 连续天数直接从 sessions 推导、发没发过靠 ledger 去重，
     * 所以这套机制没有引入任何新表或新字段。
     *
     * 放弃不算打卡——那天没有真正的投入。注意本次 session 的 endAt
     * 在调用前已经写好了，所以今天会被算进 daysWithFocus。
     */
    private suspend fun settleCheckin(nowMillis: Long, focus: FocusOutcome): Pair<Int, Long> {
        if (focus == FocusOutcome.ABANDONED) return 0 to 0
        val today = TimeFormat.millisToEpochDay(nowMillis)
        if (db.ledgerDao().checkinCount(today) > 0) return 0 to 0

        val since = TimeFormat.dayStartMillis(today - CHECKIN_LOOKBACK_DAYS)
        val daysWithFocus = db.sessionDao().completedStartsSince(since)
            .map { TimeFormat.millisToEpochDay(it) }
            .toSet()
        val streak = Economy.checkinStreak(daysWithFocus, today)
        val reward = Economy.checkinReward(streak)
        if (reward <= 0) return 0 to 0

        db.ledgerDao().insert(
            LedgerEntity(
                amount = reward,
                type = LedgerType.EARN_STREAK,
                refId = today,
                note = "连续打卡 $streak 天",
            )
        )
        return streak to reward
    }

    /**
     * 把本次完成描述追加到任务原备注末尾（一个任务可能被多次专注，逐条累积成日志）。
     * 原备注为空时直接取代，避免开头多出一个空行。
     */
    private fun appendNote(existing: String, added: String): String = when {
        added.isBlank() -> existing
        existing.isBlank() -> added
        else -> "$existing\n$added"
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
