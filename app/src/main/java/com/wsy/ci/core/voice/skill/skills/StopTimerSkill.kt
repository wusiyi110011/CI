package com.wsy.ci.core.voice.skill.skills

import com.wsy.ci.core.economy.FocusOutcome
import com.wsy.ci.core.voice.skill.AppSkill
import com.wsy.ci.core.voice.skill.SkillArgs
import com.wsy.ci.core.voice.skill.SkillDestination
import com.wsy.ci.core.voice.skill.SkillExecutionContext
import com.wsy.ci.core.voice.skill.SkillOutcome
import com.wsy.ci.core.voice.skill.SkillPreview
import com.wsy.ci.core.voice.skill.SkillRuleContext
import com.wsy.ci.widget.TimerService
import kotlinx.serialization.json.JsonObject

/**
 * 结束当前专注并结算。照抄 `TodayViewModel.stopTimer` 的组合：
 * Repository 直调拿结算结果 → `TimerService.stop` 撤通知 → 刷新小组件。
 * 排在 CompleteTask / CompleteQuest 之后，兜住那些没匹配到具体目标的完成类说法。
 */
object StopTimerSkill : AppSkill {

    override val id = "stop_timer"
    override val llmSpec = "结束当前正在计时的专注并结算奖励；args: {}"

    override fun matchRule(text: String, ctx: SkillRuleContext): SkillArgs? {
        if (SkillKeywords.FINISH_WORDS.none { text.contains(it) }) return null
        // 「XX 任务线完成了」是 CompleteQuest 的说法，这里不抢
        if (text.contains("任务线")) return null
        return emptyMap()
    }

    override fun parseLlmArgs(args: JsonObject, ctx: SkillRuleContext): SkillArgs? = emptyMap()

    override fun preview(args: SkillArgs, ctx: SkillRuleContext): SkillPreview =
        SkillPreview("结束专注", lines = listOf("结算当前进行中的计时并发放奖励"))

    override suspend fun execute(args: SkillArgs, ctx: SkillExecutionContext): SkillOutcome {
        val settlement = ctx.timer.stopSession(FocusOutcome.COMPLETED)
            ?: return SkillOutcome.Failed("当前没有进行中的专注")
        TimerService.stop(ctx.appContext)
        ctx.updateWidgets()
        return SkillOutcome.Done(
            message = "已结算 ${settlement.minutes} 分钟，+${settlement.rewardCi} CI",
            navigateTo = SkillDestination.TODAY,
            title = "结算完成",
        )
    }
}
