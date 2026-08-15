package com.wsy.ci.core.voice.skill.skills

import com.wsy.ci.core.db.QuestStatus
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

/** 归档任务线：照抄 `QuestViewModel.archiveQuest`，归档后随时可以恢复。 */
object ArchiveQuestSkill : AppSkill {

    override val id = "archive_quest"
    override val llmSpec = "把一条任务线归档；args: {\"targetId\": 候选清单里的数字id}"

    override fun matchRule(text: String, ctx: SkillRuleContext): SkillArgs? {
        if (ARCHIVE_WORDS.none { text.contains(it) }) return null
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
        return SkillPreview("归档任务线", lines = listOf("任务线 · ${name ?: "未知"}", "归档后随时可以恢复"))
    }

    override suspend fun execute(args: SkillArgs, ctx: SkillExecutionContext): SkillOutcome {
        val questId = args.targetIdOrNull() ?: return SkillOutcome.Failed("任务线已失效，请重新说一次")
        val quest = ctx.db.questDao().byId(questId)
            ?: return SkillOutcome.Failed("找不到这条任务线，可能已被删除")
        if (quest.status != QuestStatus.ACTIVE) {
            return SkillOutcome.Failed("「${quest.title}」不是进行中状态，无需归档")
        }
        ctx.db.questDao().update(quest.copy(status = QuestStatus.ARCHIVED))
        return SkillOutcome.Done("已归档「${quest.title}」")
    }

    private val ARCHIVE_WORDS = listOf("归档", "封存")
}
