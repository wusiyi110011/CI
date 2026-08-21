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

package com.wsy.ci.core.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.wsy.ci.core.designsystem.DEFAULT_DOMAIN_COLOR_ARGB
import com.wsy.ci.core.economy.Difficulty
import com.wsy.ci.core.economy.FocusOutcome
import com.wsy.ci.core.economy.Rarity

/** 学习领域：一条头衔成长线 + 累计经验。titlesJson 为 6 级头衔名 JSON 数组（LLM 生成，空则用通用兜底表）。 */
@Entity(tableName = "domains")
data class DomainEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val colorArgb: Long = DEFAULT_DOMAIN_COLOR_ARGB,
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
    /**
     * 支线可选归属的主线：「每天背 30 个单词」挂在「雅思冲刺」下面。
     * 只对支线有意义，主线恒为 null；不填表示这条支线独立存在。
     */
    val parentQuestId: Long? = null,
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

/** 任务四态。[label] 是 UI 文案，落库走 name，改 label 不影响数据。 */
enum class TaskStatus(val label: String) {
    PLANNED("计划中"),
    RUNNING("进行中"),
    DONE("已完成"),
    SKIPPED("已跳过"),
}

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
    /**
     * 直接对着任务线开的专注（支线打卡常这么用），没有具体任务时靠它记住算给谁。
     * 有 [taskId] 时以任务自己的 questId 为准，这里可以为空。
     */
    val questId: Long? = null,
    val startAt: Long,
    val endAt: Long? = null,
    val focus: FocusOutcome = FocusOutcome.COMPLETED,
    /** 结算出的 CI 币，冗余存储便于复盘。 */
    val rewardCi: Long = 0,
    /** 结算出的领域经验。 */
    val expGained: Long = 0,
)

enum class LedgerType { EARN_TASK, EARN_STREAK, EARN_LEVELUP, EARN_QUEST_DONE, SPEND_SHOP, ADJUST }

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
    /**
     * 兑换后有没有真正去兑现。花掉 CI 只是买下了权利，
     * 「看一场电影」得真去看了才算数，所以状态由自己在「我的」里手动标。
     */
    val fulfilled: Boolean = false,
    /**
     * 下单时的品质，随名字与价格一起冗余存下来。
     * 货架商品可以被删改，但「我兑换过一件传说」这个事实不该跟着变。
     */
    val rarity: Rarity = Rarity.COMMON,
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
