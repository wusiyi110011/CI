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
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonObject

/**
 * 结束当前专注并结算。照抄 `TodayViewModel.stopTimer` 的组合：
 * Repository 直调拿结算结果 → `TimerService.stop` 撤通知 → 刷新小组件。
 * 排在 CompleteTask / CompleteQuest 之后，兜住那些没匹配到具体目标的完成类说法。
 */
object StopTimerSkill : AppSkill {

    override val id = "stop_timer"
    override val risk = SkillRisk.MODERATE
    override val llmSpec =
        "结束当前正在计时的专注并结算奖励；args: {\"outcome\":\"COMPLETED|OVERTIME|ABANDONED\",\"note\":\"可选完成备注\"}"

    override fun matchRule(text: String, ctx: SkillRuleContext): SkillArgs? {
        val outcome = when {
            OVERTIME_WORDS.any { text.contains(it) } -> FocusOutcome.OVERTIME
            SkillKeywords.FINISH_WORDS.any { text.contains(it) } -> FocusOutcome.COMPLETED
            else -> return null
        }
        // 「任务线完成了」在空闲时留给 CompleteQuest；计时中则必须结算，不能绕过专注守卫。
        if (text.contains("任务线") && !ctx.hasRunningSession) return null
        val note = extractNote(text)
        if (outcome == FocusOutcome.COMPLETED && note == null) return emptyMap()
        return buildMap {
            put("outcome", outcome)
            note?.let { put("note", it) }
        }
    }

    override fun parseLlmArgs(args: JsonObject, ctx: SkillRuleContext): SkillArgs? {
        val raw = (args["outcome"] as? JsonPrimitive)?.content?.trim()?.uppercase()
        if (args.containsKey("outcome") && raw == null) return null
        val outcome = raw?.let { value -> FocusOutcome.entries.firstOrNull { it.name == value } }
            ?: if (raw == null) FocusOutcome.COMPLETED else return null
        val note = args.textOrNull("note", maxLength = 300)
        if (raw == null && note == null) return emptyMap()
        return buildMap {
            put("outcome", outcome)
            if (note != null) put("note", note)
        }
    }

    override fun preview(args: SkillArgs, ctx: SkillRuleContext): SkillPreview {
        val outcome = args["outcome"] as? FocusOutcome ?: FocusOutcome.COMPLETED
        val lines = mutableListOf(
            when (outcome) {
                FocusOutcome.COMPLETED -> "按时完成，结算奖励"
                FocusOutcome.OVERTIME -> "超时完成，按超时系数结算"
                FocusOutcome.ABANDONED -> "中途放弃，按系数 ×0.5 结算"
            }
        )
        (args["note"] as? String)?.takeIf { it.isNotBlank() }?.let { lines += "备注：$it" }
        return SkillPreview("结束专注", lines = lines)
    }

    override suspend fun execute(args: SkillArgs, ctx: SkillExecutionContext): SkillOutcome {
        val outcome = args["outcome"] as? FocusOutcome ?: FocusOutcome.COMPLETED
        val note = (args["note"] as? String).orEmpty()
        val settlement = ctx.timer.stopSession(outcome, note)
            ?: return SkillOutcome.Failed("当前没有进行中的专注")
        TimerService.stop(ctx.appContext)
        ctx.updateWidgets()
        val message = when (outcome) {
            FocusOutcome.ABANDONED -> "已放弃本次专注（${settlement.minutes} 分钟，+${settlement.rewardCi} CI）"
            FocusOutcome.OVERTIME -> "已按超时完成结算 ${settlement.minutes} 分钟，+${settlement.rewardCi} CI"
            FocusOutcome.COMPLETED -> "已结算 ${settlement.minutes} 分钟，+${settlement.rewardCi} CI"
        }
        return SkillOutcome.Done(
            message = message,
            navigateTo = SkillDestination.TODAY,
            title = "结算完成",
        )
    }

    private fun extractNote(text: String): String? =
        NOTE_REGEX.find(text)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }

    private val OVERTIME_WORDS = listOf("超时完成", "超时了", "加班完成", "晚点完成")
    private val NOTE_REGEX = Regex("""(?:备注|心得|学到|记下)[:：]?\s*(.+)$""")
}
