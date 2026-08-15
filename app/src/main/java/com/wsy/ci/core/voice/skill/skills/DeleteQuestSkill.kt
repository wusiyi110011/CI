package com.wsy.ci.core.voice.skill.skills

import androidx.room.withTransaction
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

/**
 * 删除任务线：照抄 `QuestViewModel.deleteQuest` 三步顺序
 * （解除任务关联 → 松开挂靠支线 → 删除）。危险操作，确认卡片走警示样式。
 */
object DeleteQuestSkill : AppSkill {

    override val id = "delete_quest"
    override val llmSpec = "删掉一条任务线；args: {\"targetId\": 候选清单里的数字id}"

    override fun matchRule(text: String, ctx: SkillRuleContext): SkillArgs? {
        if (SkillKeywords.DELETE_WORDS.none { text.contains(it) }) return null
        val quest = VoiceTargetMatcher.match(text, ctx.candidates, ctx.pinyinOf)
            ?.takeIf { it.kind == VoiceTargetKind.QUEST } ?: return null
        return mapOf("targetId" to quest.id)
    }

    override fun parseLlmArgs(args: JsonObject, ctx: SkillRuleContext): SkillArgs? {
        val quest = args.targetOrNull(ctx, VoiceTargetKind.QUEST) ?: return null
        return mapOf("targetId" to quest.id)
    }

    override fun preview(args: SkillArgs, ctx: SkillRuleContext): SkillPreview {
        val questId = args.targetIdOrNull()
        val name = ctx.candidates.firstOrNull { it.id == questId }?.name
        return SkillPreview(
            title = "删除任务线",
            lines = listOf("任务线 · ${name ?: "未知"}", "任务线将被删除，已排出的时间块保留"),
            dangerous = true,
        )
    }

    override suspend fun execute(args: SkillArgs, ctx: SkillExecutionContext): SkillOutcome {
        val questId = args.targetIdOrNull() ?: return SkillOutcome.Failed("任务线已失效，请重新说一次")
        val quest = ctx.db.questDao().byId(questId)
            ?: return SkillOutcome.Failed("找不到这条任务线，可能已被删除")
        // 事务包住三步，避免中途失败留下「任务线已删、任务还挂着 questId」的半删态
        ctx.db.withTransaction {
            ctx.db.taskDao().detachFromQuest(quest.id)
            ctx.db.questDao().detachChildren(quest.id)
            ctx.db.questDao().delete(quest)
        }
        ctx.updateWidgets()
        return SkillOutcome.Done("已删除「${quest.title}」，它排出的时间块保留在日程里")
    }
}
