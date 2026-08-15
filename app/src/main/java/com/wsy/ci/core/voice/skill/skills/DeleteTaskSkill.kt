package com.wsy.ci.core.voice.skill.skills

import com.wsy.ci.core.voice.VoiceTargetKind
import com.wsy.ci.core.voice.VoiceTargetMatcher
import com.wsy.ci.core.voice.skill.AppSkill
import com.wsy.ci.core.voice.skill.SkillArgs
import com.wsy.ci.core.voice.skill.SkillExecutionContext
import com.wsy.ci.core.voice.skill.SkillOutcome
import com.wsy.ci.core.voice.skill.SkillPreview
import com.wsy.ci.core.voice.skill.SkillRuleContext
import com.wsy.ci.core.voice.skill.targetIdOrNull
import com.wsy.ci.core.voice.skill.targetOrNull
import kotlinx.serialization.json.JsonObject

/** 删除任务：照抄 `QuestViewModel.deleteTask`。危险操作，确认卡片走警示样式。 */
object DeleteTaskSkill : AppSkill {

    override val id = "delete_task"
    override val llmSpec = "删掉一个任务；args: {\"targetId\": 候选清单里的数字id}"

    override fun matchRule(text: String, ctx: SkillRuleContext): SkillArgs? {
        if (SkillKeywords.DELETE_WORDS.none { text.contains(it) }) return null
        val task = VoiceTargetMatcher.match(text, ctx.candidates, ctx.pinyinOf)
            ?.takeIf { it.kind == VoiceTargetKind.TASK } ?: return null
        return mapOf("targetId" to task.id)
    }

    override fun parseLlmArgs(args: JsonObject, ctx: SkillRuleContext): SkillArgs? {
        val task = args.targetOrNull(ctx, VoiceTargetKind.TASK) ?: return null
        return mapOf("targetId" to task.id)
    }

    override fun preview(args: SkillArgs, ctx: SkillRuleContext): SkillPreview {
        val taskId = args.targetIdOrNull()
        val name = ctx.candidates.firstOrNull { it.id == taskId }?.name
        return SkillPreview(
            title = "删除任务",
            lines = listOf("任务 · ${name ?: "未知"}", "该操作不可撤销"),
            dangerous = true,
        )
    }

    override suspend fun execute(args: SkillArgs, ctx: SkillExecutionContext): SkillOutcome {
        val taskId = args.targetIdOrNull() ?: return SkillOutcome.Failed("任务已失效，请重新说一次")
        val task = ctx.db.taskDao().byId(taskId)
            ?: return SkillOutcome.Failed("找不到这个任务，可能已被删除")
        if (ctx.db.sessionDao().openSession()?.taskId == taskId) {
            return SkillOutcome.Failed("「${task.title}」正在计时，先结束专注再删除吧")
        }
        ctx.db.taskDao().delete(task)
        ctx.updateWidgets()
        return SkillOutcome.Done("已删除任务「${task.title}」")
    }
}
