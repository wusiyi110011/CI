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

package com.wsy.ci.core.voice.skill.skills

import com.wsy.ci.core.economy.FocusOutcome
import com.wsy.ci.core.voice.skill.AppSkill
import com.wsy.ci.core.voice.skill.SkillArgs
import com.wsy.ci.core.voice.skill.SkillDestination
import com.wsy.ci.core.voice.skill.SkillExecutionContext
import com.wsy.ci.core.voice.skill.SkillOutcome
import com.wsy.ci.core.voice.skill.SkillPreview
import com.wsy.ci.core.voice.skill.SkillRuleContext
import com.wsy.ci.core.voice.skill.SkillRisk
import com.wsy.ci.core.voice.skill.textOrNull
import com.wsy.ci.widget.TimerService
import kotlinx.serialization.json.JsonObject

/**
 * 放弃当前专注。`TimerService.ACTION_COMPLETE` 的通知按钮固定按完成结算，
 * 不能靠它传放弃语义，所以和 StopTimerSkill 一样走 Repository 直调 + `Service.stop()` 撤通知。
 */
object AbandonTimerSkill : AppSkill {

    override val id = "abandon_timer"
    override val risk = SkillRisk.MODERATE
    override val llmSpec = "放弃当前正在计时的专注，按中途放弃系数结算；args: {\"note\":\"可选备注\"}"

    override fun matchRule(text: String, ctx: SkillRuleContext): SkillArgs? {
        if (ABANDON_WORDS.none { text.contains(it) }) return null
        return emptyMap()
    }

    override fun parseLlmArgs(args: JsonObject, ctx: SkillRuleContext): SkillArgs? = buildMap {
        args.textOrNull("note", 300)?.let { put("note", it) }
    }

    override fun preview(args: SkillArgs, ctx: SkillRuleContext): SkillPreview =
        SkillPreview("放弃本次专注", lines = listOf("按中途放弃系数 ×0.5 结算").plus((args["note"] as? String)?.let { "备注：$it" }.orEmpty().let { if (it.isBlank()) emptyList() else listOf(it) }))

    override suspend fun execute(args: SkillArgs, ctx: SkillExecutionContext): SkillOutcome {
        val settlement = ctx.timer.stopSession(FocusOutcome.ABANDONED, args["note"] as? String ?: "")
            ?: return SkillOutcome.Failed("当前没有进行中的专注")
        TimerService.stop(ctx.appContext)
        ctx.updateWidgets()
        return SkillOutcome.Done(
            message = "已放弃本次专注（${settlement.minutes} 分钟，+${settlement.rewardCi} CI）",
            navigateTo = SkillDestination.TODAY,
            title = "已放弃",
        )
    }

    /** 刻意不含「算了」：它是「帮我算了一下」的高频子串，会误吞无关语句。 */
    private val ABANDON_WORDS = listOf("放弃", "不搞了", "不干了", "不学了", "不弄了")
}
