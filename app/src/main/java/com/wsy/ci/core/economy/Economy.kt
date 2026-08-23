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

package com.wsy.ci.core.economy

import kotlin.math.floor

/** 任务难度，系数参与 CI 币与经验双轨结算。 */
enum class Difficulty(val label: String, val factor: Double) {
    EASY("轻松", 0.8),
    NORMAL("一般", 1.0),
    HARD("烧脑", 1.3),
    EPIC("攻坚", 1.6),
}

/** 专注结果：按时完成 / 中途放弃 / 超时完成。 */
enum class FocusOutcome(val label: String, val factor: Double) {
    COMPLETED("按时完成", 1.0),
    ABANDONED("中途放弃", 0.5),
    OVERTIME("超时完成", 0.9),
}

/** 商品品质，每日精选抽取权重 45/35/15/5。 */
enum class Rarity(val label: String, val weight: Int) {
    COMMON("普通", 45),
    RARE("稀有", 35),
    EPIC("史诗", 15),
    LEGENDARY("传说", 5),
}

object Economy {

    /** 1 元人民币 ≈ 20 CI 的锚定汇率。 */
    const val CI_PER_YUAN = 20

    /** 头衔升级累计经验门槛（达到即升到 2/3/4/5/6 级）。 */
    val LEVEL_THRESHOLDS = longArrayOf(600, 2100, 5400, 12000, 24000)

    const val MAX_LEVEL = 6

    private const val STREAK_TIER1_DAYS = 7
    private const val STREAK_TIER2_DAYS = 30
    private const val STREAK_CAP_DAYS = 100
    private const val STREAK_TIER1_BONUS = 0.10
    private const val STREAK_TIER2_BONUS = 0.25
    private const val STREAK_CAP_BONUS = 0.50

    /** 支线连击加成：7 天 +10%，30 天 +25%，100 天封顶 +50%。 */
    fun streakBonus(streakDays: Int): Double = when {
        streakDays >= STREAK_CAP_DAYS -> STREAK_CAP_BONUS
        streakDays >= STREAK_TIER2_DAYS -> STREAK_TIER2_BONUS
        streakDays >= STREAK_TIER1_DAYS -> STREAK_TIER1_BONUS
        else -> 0.0
    }

    private const val DURATION_TIER1_MINUTES = 30
    private const val DURATION_TIER2_MINUTES = 60
    private const val DURATION_TIER2_RATE = 1.5
    private const val DURATION_TIER3_RATE = 2.0

    /**
     * 时长阶梯加权后的分钟数：单次专注坐得越久，后面的分钟越值钱。
     * 前 30 分钟 ×1，第 30–60 分钟 ×1.5，超过 60 分钟的部分 ×2，分段累加而非整段跳档。
     *
     * 例：40 分钟 = 30×1 + 10×1.5 = 45；90 分钟 = 30×1 + 30×1.5 + 30×2 = 135。
     *
     * 只作用于 CI 币，不动经验——经验是「投入了多少时间」的诚实记录，
     * 加了阶梯就不再是分钟数的线性映射，等级门槛也会跟着失真。
     */
    fun weightedMinutes(actualMinutes: Int): Double {
        if (actualMinutes <= 0) return 0.0
        val tier1 = minOf(actualMinutes, DURATION_TIER1_MINUTES)
        val tier2 = (minOf(actualMinutes, DURATION_TIER2_MINUTES) - DURATION_TIER1_MINUTES)
            .coerceAtLeast(0)
        val tier3 = (actualMinutes - DURATION_TIER2_MINUTES).coerceAtLeast(0)
        return tier1 + tier2 * DURATION_TIER2_RATE + tier3 * DURATION_TIER3_RATE
    }

    /** 本次专注的整体时长倍率（加权分钟 / 实际分钟），给 UI 展示用；0 分钟兜底为 1。 */
    fun durationMultiplier(actualMinutes: Int): Double =
        if (actualMinutes <= 0) 1.0 else weightedMinutes(actualMinutes) / actualMinutes

    /**
     * 单次任务 CI 币 = 加权分钟 × 难度系数 × 专注系数 × (1 + 连击加成)，向下取整。
     * 加权分钟见 [weightedMinutes]；连击加成仅支线任务传入非零 streakDays。
     */
    fun taskReward(
        actualMinutes: Int,
        difficulty: Difficulty,
        focus: FocusOutcome,
        streakDays: Int = 0,
    ): Long {
        if (actualMinutes <= 0) return 0
        val base = weightedMinutes(actualMinutes) * difficulty.factor * focus.factor
        return floor(base * (1 + streakBonus(streakDays))).toLong()
    }

    private const val CHECKIN_TIER1_DAYS = 3
    private const val CHECKIN_TIER2_DAYS = 7
    private const val CHECKIN_TIER3_DAYS = 14
    private const val CHECKIN_BASE_CI = 20L
    private const val CHECKIN_TIER1_CI = 40L
    private const val CHECKIN_TIER2_CI = 80L
    private const val CHECKIN_TIER3_CI = 150L

    /**
     * 每日打卡奖励：当天首次完成专注时发一笔定额，连续天数越长单笔越高。
     * 1–2 天 20、3–6 天 40、7–13 天 80、14 天及以上 150。
     *
     * 与 [streakBonus] 是两套独立机制：那个按比例加成单次任务收益、只对支线生效；
     * 这个是全局每日定额，跟任务挂不挂支线无关。
     */
    fun checkinReward(streakDays: Int): Long = when {
        streakDays >= CHECKIN_TIER3_DAYS -> CHECKIN_TIER3_CI
        streakDays >= CHECKIN_TIER2_DAYS -> CHECKIN_TIER2_CI
        streakDays >= CHECKIN_TIER1_DAYS -> CHECKIN_TIER1_CI
        streakDays >= 1 -> CHECKIN_BASE_CI
        else -> 0
    }

    /**
     * 截至 [today] 的连续打卡天数，从有专注记录的日期集合往前逐日数。
     *
     * [today] 当天没有记录直接返回 0——打卡的语义是「今天来过」，不是「昨天来过」。
     * 漏一天即断，严格清零，不设补签。
     */
    fun checkinStreak(daysWithFocus: Set<Long>, today: Long): Int {
        if (today !in daysWithFocus) return 0
        var count = 0
        var day = today
        while (day in daysWithFocus) {
            count++
            day--
        }
        return count
    }

    /**
     * 今天首次打卡后的连续天数。[previousCheckinDays] 是此前已落账的打卡日期，
     * 今天正在当前事务里结算、尚未写入流水，因此先补入 [today] 再回溯。
     */
    fun checkinStreakAfterToday(previousCheckinDays: Set<Long>, today: Long): Int =
        checkinStreak(previousCheckinDays + today, today)

    /** 领域经验 = 实际专注分钟 × 难度系数（经验只增不减，与 CI 币双轨）。 */
    fun expGain(actualMinutes: Int, difficulty: Difficulty): Long {
        if (actualMinutes <= 0) return 0
        return floor(actualMinutes * difficulty.factor).toLong()
    }

    /** 由累计经验得到当前头衔等级（1..6）。 */
    fun levelForExp(totalExp: Long): Int =
        1 + LEVEL_THRESHOLDS.count { totalExp >= it }

    /** 距下一级还差多少经验；满级返回 null。 */
    fun expToNextLevel(totalExp: Long): Long? {
        val level = levelForExp(totalExp)
        if (level >= MAX_LEVEL) return null
        return LEVEL_THRESHOLDS[level - 1] - totalExp
    }

    /** 当前等级区间的进度 0..1，用于进度条；满级恒为 1。 */
    fun levelProgress(totalExp: Long): Float {
        val level = levelForExp(totalExp)
        if (level >= MAX_LEVEL) return 1f
        val floor = if (level == 1) 0L else LEVEL_THRESHOLDS[level - 2]
        val ceil = LEVEL_THRESHOLDS[level - 1]
        return ((totalExp - floor).toFloat() / (ceil - floor)).coerceIn(0f, 1f)
    }

    /** 升级一次性 CI 币奖励 = 升级后等级 × 100。 */
    fun levelUpReward(newLevel: Int): Long = newLevel * 100L

    /** 主线完结的复利结算利率：按该主线名下累计入账 CI 的一成发利息。 */
    const val QUEST_INTEREST_RATE = 0.10

    /**
     * 主线完成的「复利结算」：本金是逐次专注已经领走的 CI 币，完结时按
     * [QUEST_INTEREST_RATE] 发一笔利息，向下取整。全工程唯一字面意义上的复利。
     */
    fun questInterest(totalEarnedCi: Long): Long =
        if (totalEarnedCi <= 0) 0L else floor(totalEarnedCi * QUEST_INTEREST_RATE).toLong()

    /** 商品建议品质：按锚定价格档位划分。 */
    fun suggestRarity(priceCi: Long): Rarity = when {
        priceCi < 500 -> Rarity.COMMON
        priceCi < 2000 -> Rarity.RARE
        priceCi <= 10_000 -> Rarity.EPIC
        else -> Rarity.LEGENDARY
    }
}
