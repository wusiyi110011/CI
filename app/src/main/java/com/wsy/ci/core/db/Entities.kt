package com.wsy.ci.core.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.wsy.ci.core.economy.Difficulty
import com.wsy.ci.core.economy.FocusOutcome
import com.wsy.ci.core.economy.Rarity

/** 学习领域：一条头衔成长线 + 累计经验。titlesJson 为 6 级头衔名 JSON 数组（LLM 生成，空则用通用兜底表）。 */
@Entity(tableName = "domains")
data class DomainEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val colorArgb: Long = 0xFF3F6B35,
    val totalExp: Long = 0,
    val titlesJson: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val archived: Boolean = false,
)

enum class QuestType { MAIN, SIDE }
enum class QuestStatus { ACTIVE, DONE, ARCHIVED }

/**
 * 主线/支线定义。主线：章节结构（chaptersJson）+ 截止日期；
 * 支线：周期性习惯，连击字段生效，当天错过则连击清零。
 */
@Entity(
    tableName = "quests",
    indices = [Index("domainId"), Index("status")],
)
data class QuestEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val domainId: Long? = null,
    val type: QuestType,
    val title: String,
    val description: String = "",
    val status: QuestStatus = QuestStatus.ACTIVE,
    /** 主线章节结构，JSON（M3 由 LLM 生成）。 */
    val chaptersJson: String? = null,
    /** 主线截止日 epochDay。 */
    val deadlineEpochDay: Long? = null,
    /** 支线当前连击天数。 */
    val streakDays: Int = 0,
    /** 支线历史最佳连击。 */
    val bestStreak: Int = 0,
    /** 支线最近一次打卡日 epochDay，用于判断断签。 */
    val lastDoneEpochDay: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

enum class TaskStatus { PLANNED, RUNNING, DONE, SKIPPED }

/** 排程任务实例：某天的一个计划时间块。时间用 epochDay + 当日分钟数表达。 */
@Entity(
    tableName = "tasks",
    indices = [Index("epochDay"), Index("questId"), Index("domainId"), Index("status")],
)
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val epochDay: Long,
    val startMinute: Int,
    val endMinute: Int,
    val domainId: Long? = null,
    val questId: Long? = null,
    val difficulty: Difficulty = Difficulty.NORMAL,
    val status: TaskStatus = TaskStatus.PLANNED,
    /** 锁定块：重排引擎不得移动。 */
    val locked: Boolean = false,
    val note: String = "",
)

/** 实际计时记录：一次专注 session，与任务可关联可独立。 */
@Entity(
    tableName = "sessions",
    indices = [Index("taskId"), Index("startAt")],
)
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long? = null,
    val domainId: Long? = null,
    val startAt: Long,
    val endAt: Long? = null,
    val focus: FocusOutcome = FocusOutcome.COMPLETED,
    /** 结算出的 CI 币，冗余存储便于复盘。 */
    val rewardCi: Long = 0,
    /** 结算出的领域经验。 */
    val expGained: Long = 0,
)

enum class LedgerType { EARN_TASK, EARN_STREAK, EARN_LEVELUP, SPEND_SHOP, ADJUST }

/** CI 币流水账。amount 正为入账、负为支出。 */
@Entity(tableName = "ledger", indices = [Index("at"), Index("type")])
data class LedgerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val at: Long = System.currentTimeMillis(),
    val amount: Long,
    val type: LedgerType,
    val refId: Long? = null,
    val note: String = "",
)

/** 商城商品（货架常驻，原价可购）。 */
@Entity(tableName = "shop_items")
data class ShopItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String = "",
    val emoji: String = "🎁",
    val priceCi: Long,
    val rarity: Rarity,
    val active: Boolean = true,
    /** 储蓄目标：冻结进度（已存 CI），攒够解锁。0 表示未开启储蓄。 */
    val savedCi: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
)

/** 今日精选：每日 4 个折扣位。 */
@Entity(tableName = "daily_picks", indices = [Index("epochDay")])
data class DailyPickEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val itemId: Long,
    val discountPercent: Int,
    val purchased: Boolean = false,
)

/** 购买记录。 */
@Entity(tableName = "purchases", indices = [Index("at")])
data class PurchaseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: Long,
    val itemName: String,
    val pricePaid: Long,
    val at: Long = System.currentTimeMillis(),
)

/** 占位事件（临时有事）：重排引擎视为不可用时段。 */
@Entity(tableName = "blockers", indices = [Index("epochDay")])
data class BlockerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val startMinute: Int,
    val endMinute: Int,
    val title: String,
)
