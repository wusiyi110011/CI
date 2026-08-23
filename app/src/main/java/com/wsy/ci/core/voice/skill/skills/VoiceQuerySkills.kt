/*
 * Copyright 2026 吴思毅
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.wsy.ci.core.voice.skill.skills

import com.wsy.ci.core.data.VoiceStatsRepository
import com.wsy.ci.core.voice.skill.AppSkill
import com.wsy.ci.core.voice.skill.SkillArgs
import com.wsy.ci.core.voice.skill.SkillDestination
import com.wsy.ci.core.voice.skill.SkillExecutionContext
import com.wsy.ci.core.voice.skill.SkillOutcome
import com.wsy.ci.core.voice.skill.SkillPreview
import com.wsy.ci.core.voice.skill.SkillRisk
import com.wsy.ci.core.voice.skill.SkillRuleContext
import com.wsy.ci.core.util.TimeFormat
import kotlinx.serialization.json.JsonObject

/** 查询当前正在进行的专注。 */
object QueryCurrentFocusSkill : AppSkill {
    override val id = "query_current_focus"
    override val risk = SkillRisk.SAFE
    override val llmSpec = "查看当前正在进行的专注；args: {}"

    override fun matchRule(text: String, ctx: SkillRuleContext): SkillArgs? =
        if (CURRENT_WORDS.any { text.contains(it) }) emptyMap() else null

    override fun parseLlmArgs(args: JsonObject, ctx: SkillRuleContext): SkillArgs? = emptyMap()

    override fun preview(args: SkillArgs, ctx: SkillRuleContext) =
        SkillPreview("当前专注", lines = listOf("查看正在计时的任务与已用时"))

    override suspend fun execute(args: SkillArgs, ctx: SkillExecutionContext): SkillOutcome {
        val focus = (ctx.stats ?: VoiceStatsRepository(ctx.db)).currentFocus()
            ?: return SkillOutcome.Done("当前没有进行中的专注", title = "当前专注")
        val now = System.currentTimeMillis()
        val elapsed = TimeFormat.elapsed((now - focus.session.startAt).coerceAtLeast(0))
        val target = focus.task?.title ?: focus.quest?.title ?: "自由专注"
        val timing = focus.task?.let { task ->
            val plannedEnd = TimeFormat.dayStartMillis(task.epochDay) + task.endMinute * MILLIS_PER_MINUTE
            val remaining = plannedEnd - now
            if (remaining > 0) "，计划剩余 ${TimeFormat.elapsed(remaining)}" else "，已超过计划结束时间"
        } ?: "，本次没有预设结束时间"
        return SkillOutcome.Done(
            "正在专注「$target」，已用时 $elapsed$timing",
            navigateTo = SkillDestination.TODAY,
            title = "当前专注",
        )
    }

    private val CURRENT_WORDS = listOf("当前专注", "现在专注", "正在专注", "正在计时", "还在计时", "还剩多久", "专注状态", "现在在干嘛", "我在做什么")
    private const val MILLIS_PER_MINUTE = 60_000L
}

/** 查询 CI 余额。 */
object QueryBalanceSkill : AppSkill {
    override val id = "query_balance"
    override val risk = SkillRisk.SAFE
    override val llmSpec = "查看当前 CI 余额；args: {}"

    override fun matchRule(text: String, ctx: SkillRuleContext): SkillArgs? =
        if (BALANCE_WORDS.any { text.contains(it) } && !text.contains("商城")) emptyMap() else null

    override fun parseLlmArgs(args: JsonObject, ctx: SkillRuleContext): SkillArgs? = emptyMap()

    override fun preview(args: SkillArgs, ctx: SkillRuleContext) =
        SkillPreview("查看 CI 余额", lines = listOf("读取当前 CI 币余额"))

    override suspend fun execute(args: SkillArgs, ctx: SkillExecutionContext): SkillOutcome =
        SkillOutcome.Done("当前余额：${(ctx.stats ?: VoiceStatsRepository(ctx.db)).balance()} CI", title = "CI 余额")

    private val BALANCE_WORDS = listOf("余额", "有多少 CI", "有多少CI", "多少币", "多少金币", "我的钱")
}

/** 查询本周打卡和连续天数。 */
object QueryCheckinSkill : AppSkill {
    override val id = "query_checkin"
    override val risk = SkillRisk.SAFE
    override val llmSpec = "查看今天是否打卡及连续打卡天数；args: {}"

    override fun matchRule(text: String, ctx: SkillRuleContext): SkillArgs? =
        if (CHECKIN_WORDS.any { text.contains(it) }) emptyMap() else null

    override fun parseLlmArgs(args: JsonObject, ctx: SkillRuleContext): SkillArgs? = emptyMap()

    override fun preview(args: SkillArgs, ctx: SkillRuleContext) =
        SkillPreview("查看打卡", lines = listOf("查看今日打卡与连续天数"))

    override suspend fun execute(args: SkillArgs, ctx: SkillExecutionContext): SkillOutcome {
        val stats = (ctx.stats ?: VoiceStatsRepository(ctx.db)).checkinStats()
        val today = if (stats.checkedInToday) "今天已打卡" else "今天还没打卡"
        return SkillOutcome.Done("$today，连续打卡 ${stats.streakDays} 天；近七天 ${stats.checkedInDays} 天", title = "打卡记录")
    }

    private val CHECKIN_WORDS = listOf("打卡", "签到", "连续了几天", "连击", "连续天数", "坚持了多久")
}

/** 查询本周专注时长、收益和打卡天数。 */
object QueryWeeklyStatsSkill : AppSkill {
    override val id = "query_weekly_stats"
    override val risk = SkillRisk.SAFE
    override val llmSpec = "查看本周学习统计；args: {}"

    override fun matchRule(text: String, ctx: SkillRuleContext): SkillArgs? =
        if (WEEK_WORDS.any { text.contains(it) } && STATS_WORDS.any { text.contains(it) }) emptyMap() else null

    override fun parseLlmArgs(args: JsonObject, ctx: SkillRuleContext): SkillArgs? = emptyMap()

    override fun preview(args: SkillArgs, ctx: SkillRuleContext) =
        SkillPreview("本周统计", lines = listOf("专注时长、场次、CI 收支与打卡天数"))

    override suspend fun execute(args: SkillArgs, ctx: SkillExecutionContext): SkillOutcome {
        val stats = (ctx.stats ?: VoiceStatsRepository(ctx.db)).weeklyStats()
        return SkillOutcome.Done(
            "本周已专注 ${TimeFormat.duration(stats.focusMinutes)}，${stats.sessionCount} 次；" +
                "收入 ${stats.earnedCi} CI，支出 ${stats.spentCi} CI，打卡 ${stats.checkinDays} 天",
            navigateTo = SkillDestination.STATS,
            title = "本周统计",
        )
    }

    private val WEEK_WORDS = listOf("本周", "这周", "这一周")
    private val STATS_WORDS = listOf("统计", "复盘", "学了多久", "专注多久", "赚了多少", "表现")
}
