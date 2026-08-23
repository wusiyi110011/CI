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
import com.wsy.ci.core.db.TaskStatus
import com.wsy.ci.core.voice.VoiceTarget
import com.wsy.ci.core.voice.VoiceTargetKind
import com.wsy.ci.core.voice.VoiceTargetMatcher
import com.wsy.ci.core.voice.label
import com.wsy.ci.core.voice.skill.AppSkill
import com.wsy.ci.core.voice.skill.SkillArgs
import com.wsy.ci.core.voice.skill.SkillDestination
import com.wsy.ci.core.voice.skill.SkillExecutionContext
import com.wsy.ci.core.voice.skill.SkillOutcome
import com.wsy.ci.core.voice.skill.SkillPreview
import com.wsy.ci.core.voice.skill.SkillRuleContext
import com.wsy.ci.core.voice.skill.SkillRisk
import com.wsy.ci.core.voice.skill.targetOrNull
import com.wsy.ci.widget.TimerService
import java.time.LocalDate
import kotlinx.serialization.json.JsonObject

/**
 * 开始专注。迁移原 `VoiceCommandParser` 的「开始」分支，执行改走 `TimerService.start`——
 * 修复原来绕开前台服务直调 Repository、进程被杀时 session 失保活的 bug。
 * 排除「重新」：那是恢复任务线的说法（RestoreQuestSkill）。
 */
object StartTimerSkill : AppSkill {

    override val id = "start_timer"
    override val risk = SkillRisk.MODERATE
    override val llmSpec = "开始某个任务/主线/支线的专注计时；args: {\"targetId\": 候选清单里的数字id}"

    override fun matchRule(text: String, ctx: SkillRuleContext): SkillArgs? {
        if (text.contains("重新")) return null
        if (START_VERBS.none { text.contains(it) }) return null
        val target = VoiceTargetMatcher.match(text, ctx.candidates, ctx.pinyinOf)
            ?.takeIf { it.kind != VoiceTargetKind.SHOP_ITEM } ?: return null
        return mapOf("target" to target)
    }

    override fun parseLlmArgs(args: JsonObject, ctx: SkillRuleContext): SkillArgs? {
        val target = args.targetOrNull(ctx, VoiceTargetKind.TASK, VoiceTargetKind.QUEST, VoiceTargetKind.DOMAIN)
            ?: return null
        return mapOf("target" to target)
    }

    override fun preview(args: SkillArgs, ctx: SkillRuleContext): SkillPreview {
        val target = args["target"] as? VoiceTarget
            ?: return SkillPreview("开始专注")
        return SkillPreview("开始专注", lines = listOf("${target.kind.label} · ${target.name}"))
    }

    override suspend fun execute(args: SkillArgs, ctx: SkillExecutionContext): SkillOutcome {
        val target = args["target"] as? VoiceTarget
            ?: return SkillOutcome.Failed("目标已失效，请重新说一次")
        if (ctx.db.sessionDao().openSession() != null) {
            return SkillOutcome.Failed("已有进行中的专注，结束后才能开始新任务")
        }
        when (target.kind) {
            VoiceTargetKind.TASK -> {
                val task = ctx.db.taskDao().byId(target.id)
                    ?: return SkillOutcome.Failed("找不到「${target.name}」，可能已被删除")
                if (task.status != TaskStatus.PLANNED) {
                    return SkillOutcome.Failed("「${target.name}」是${task.status.label}状态，不能开始")
                }
                TimerService.start(ctx.appContext, task.id, task.title)
            }
            VoiceTargetKind.QUEST -> {
                val quest = ctx.db.questDao().byId(target.id)
                    ?: return SkillOutcome.Failed("找不到「${target.name}」，可能已被删除")
                if (quest.status != QuestStatus.ACTIVE) {
                    return SkillOutcome.Failed("「${target.name}」已${questStatusLabel(quest.status)}，不能开始")
                }
                // 今天已排好时间块就从具体任务开始，没有则对着任务线自由专注
                val today = LocalDate.now().toEpochDay()
                val todayTask = ctx.db.taskDao().byRange(today, today)
                    .firstOrNull { it.questId == target.id && it.status == TaskStatus.PLANNED }
                if (todayTask != null) {
                    TimerService.start(ctx.appContext, todayTask.id, todayTask.title)
                } else {
                    TimerService.start(ctx.appContext, null, quest.title, quest.id)
                }
            }
            VoiceTargetKind.DOMAIN ->
                return SkillOutcome.Failed("领域不能直接开始，换成具体任务名试试")
            VoiceTargetKind.SHOP_ITEM ->
                return SkillOutcome.Failed("商品不是学习目标")
        }
        // 小组件刷新由 TimerService 在写库完成后统一处理（服务内 updateAll），这里不重复刷
        return SkillOutcome.Done(navigateTo = SkillDestination.TODAY)
    }

    private fun questStatusLabel(status: QuestStatus): String = when (status) {
        QuestStatus.ACTIVE -> "进行中"
        QuestStatus.DONE -> "完成"
        QuestStatus.ARCHIVED -> "归档"
    }

    /**
     * 刻意不含「干」「搞」这类泛指动词：它们会截胡「干完了/搞定了/不搞了」等完成或放弃
     * 语义（完成类见 [SkillKeywords.FINISH_WORDS]，放弃类见 AbandonTimerSkill）。
     */
    private val START_VERBS = listOf("开始", "启动", "我要做", "我要学", "去学", "去学习", "来一发")
}
