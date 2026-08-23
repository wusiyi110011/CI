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

import com.wsy.ci.core.db.TaskStatus
import com.wsy.ci.core.voice.VoiceTargetKind
import com.wsy.ci.core.voice.VoiceTargetMatcher
import com.wsy.ci.core.voice.skill.AppSkill
import com.wsy.ci.core.voice.skill.SkillArgs
import com.wsy.ci.core.voice.skill.SkillExecutionContext
import com.wsy.ci.core.voice.skill.SkillOutcome
import com.wsy.ci.core.voice.skill.SkillPreview
import com.wsy.ci.core.voice.skill.SkillRuleContext
import com.wsy.ci.core.voice.skill.SkillRisk
import com.wsy.ci.core.voice.skill.targetIdOrNull
import com.wsy.ci.core.voice.skill.targetOrNull
import kotlinx.serialization.json.JsonObject

/** 跳过任务：照抄 `QuestViewModel.skipTask`，任务标记为已跳过（时间块仍在日程上）。 */
object SkipTaskSkill : AppSkill {

    override val id = "skip_task"
    override val risk = SkillRisk.MODERATE
    override val llmSpec = "把一个任务标记为已跳过；args: {\"targetId\": 候选清单里的数字id}"

    override fun matchRule(text: String, ctx: SkillRuleContext): SkillArgs? {
        if (SKIP_WORDS.none { text.contains(it) }) return null
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
        return SkillPreview("跳过任务", lines = listOf("任务 · ${name ?: "未知"}"))
    }

    override suspend fun execute(args: SkillArgs, ctx: SkillExecutionContext): SkillOutcome {
        val taskId = args.targetIdOrNull() ?: return SkillOutcome.Failed("任务已失效，请重新说一次")
        val task = ctx.db.taskDao().byId(taskId)
            ?: return SkillOutcome.Failed("找不到这个任务，可能已被删除")
        if (ctx.db.sessionDao().openSession()?.taskId == taskId) {
            return SkillOutcome.Failed("「${task.title}」正在计时，先结束专注再跳过吧")
        }
        ctx.db.taskDao().update(task.copy(status = TaskStatus.SKIPPED))
        ctx.updateWidgets()
        return SkillOutcome.Done("已跳过「${task.title}」")
    }

    private val SKIP_WORDS = listOf("跳过", "略过")
}
