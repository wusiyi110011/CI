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

/**
 * 把未计时的任务直接标完成。和 StopTimerSkill 是两回事：后者结算的是正在计时的 session。
 * 正在计时时「做完了」一律留给 StopTimerSkill 结算，所以这里要求没有进行中的 session。
 */
object CompleteTaskSkill : AppSkill {

    override val id = "complete_task"
    override val risk = SkillRisk.MODERATE
    override val llmSpec = "把一个任务直接标记为已完成；args: {\"targetId\": 候选清单里的数字id}"

    override fun matchRule(text: String, ctx: SkillRuleContext): SkillArgs? {
        if (ctx.hasRunningSession) return null
        if (SkillKeywords.FINISH_WORDS.none { text.contains(it) }) return null
        if (text.contains("任务线")) return null
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
        return SkillPreview("标记任务完成", lines = listOf("任务 · ${name ?: "未知"}"))
    }

    override suspend fun execute(args: SkillArgs, ctx: SkillExecutionContext): SkillOutcome {
        val taskId = args.targetIdOrNull() ?: return SkillOutcome.Failed("任务已失效，请重新说一次")
        val task = ctx.db.taskDao().byId(taskId)
            ?: return SkillOutcome.Failed("找不到这个任务，可能已被删除")
        // LLM 兜底路径没有规则层的计时守卫，这里补最终闸门：正在计时的完成走结算，不直接标 DONE
        if (ctx.db.sessionDao().openSession()?.taskId == taskId) {
            return SkillOutcome.Failed("「${task.title}」正在计时，请说「完成了」走结算")
        }
        if (task.status == TaskStatus.DONE) {
            return SkillOutcome.Failed("「${task.title}」已经是完成状态")
        }
        ctx.db.taskDao().update(task.copy(status = TaskStatus.DONE))
        ctx.updateWidgets()
        return SkillOutcome.Done("已标记「${task.title}」完成")
    }
}
