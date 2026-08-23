/*
 * Copyright 2026 吴思毅
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.wsy.ci.core.voice.skill.skills

import com.wsy.ci.core.voice.skill.AppSkill
import com.wsy.ci.core.voice.skill.SkillArgs
import com.wsy.ci.core.voice.skill.SkillExecutionContext
import com.wsy.ci.core.voice.skill.SkillOutcome
import com.wsy.ci.core.voice.skill.SkillPreview
import com.wsy.ci.core.voice.skill.SkillRisk
import com.wsy.ci.core.voice.skill.SkillRuleContext
import kotlinx.serialization.json.JsonObject

/** 在撤销窗口内恢复上一次自然语言重排。 */
object UndoRescheduleSkill : AppSkill {
    override val id = "undo_reschedule"
    override val risk = SkillRisk.MODERATE
    override val llmSpec = "撤销上一次重排；args: {}"

    override fun matchRule(text: String, ctx: SkillRuleContext): SkillArgs? =
        if (UNDO_WORDS.any { text.contains(it) } && RESCHEDULE_WORDS.any { text.contains(it) }) emptyMap()
        else null

    override fun parseLlmArgs(args: JsonObject, ctx: SkillRuleContext): SkillArgs? = emptyMap()

    override fun preview(args: SkillArgs, ctx: SkillRuleContext) =
        SkillPreview("撤销重排", lines = listOf("恢复上一次调整前的任务时间"))

    override suspend fun execute(args: SkillArgs, ctx: SkillExecutionContext): SkillOutcome =
        if (ctx.rescheduleFlow.undoLastRescheduleNow()) {
            SkillOutcome.Done("已撤销上一次重排")
        } else {
            SkillOutcome.Failed("当前没有可撤销的重排")
        }

    private val UNDO_WORDS = listOf("撤销", "还原", "反悔")
    private val RESCHEDULE_WORDS = listOf("重排", "调整", "安排", "移动时间")
}
