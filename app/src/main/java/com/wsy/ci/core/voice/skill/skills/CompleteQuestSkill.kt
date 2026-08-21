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

/** 完成任务线：与 `QuestViewModel.completeQuest` 共用 [com.wsy.ci.core.data.QuestRepository]（完结不改 task/session 数据，无需刷新小组件）。 */
object CompleteQuestSkill : AppSkill {

    override val id = "complete_quest"
    override val llmSpec = "把一条任务线标记为已完成；args: {\"targetId\": 候选清单里的数字id}"

    override fun matchRule(text: String, ctx: SkillRuleContext): SkillArgs? {
        if (SkillKeywords.FINISH_WORDS.none { text.contains(it) }) return null
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
        return SkillPreview("完成任务线", lines = listOf("任务线 · ${name ?: "未知"}"))
    }

    override suspend fun execute(args: SkillArgs, ctx: SkillExecutionContext): SkillOutcome {
        val questId = args.targetIdOrNull() ?: return SkillOutcome.Failed("任务线已失效，请重新说一次")
        val quest = ctx.db.questDao().byId(questId)
            ?: return SkillOutcome.Failed("找不到这条任务线，可能已被删除")
        if (quest.status != QuestStatus.ACTIVE) {
            return SkillOutcome.Failed("「${quest.title}」不是进行中状态，无需完成")
        }
        val done = ctx.quest.complete(questId)
            ?: return SkillOutcome.Failed("任务线已失效，请重新说一次")
        val interest = if (done.interestCi > 0) "，复利结算 +${done.interestCi} CI" else ""
        return SkillOutcome.Done("「${done.questTitle}」已完成 🎉$interest")
    }
}
