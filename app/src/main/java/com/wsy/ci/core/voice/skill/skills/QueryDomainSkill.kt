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

import com.wsy.ci.core.economy.Economy
import com.wsy.ci.core.title.Titles
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

/** 领域进度查询（只读）：等级、经验、当前头衔与距下一级进度。 */
object QueryDomainSkill : AppSkill {

    override val id = "query_domain"
    override val risk = SkillRisk.SAFE
    override val llmSpec = "查看某学习领域的等级与经验进度；args: {\"targetId\": 候选清单里的数字id}"

    override fun matchRule(text: String, ctx: SkillRuleContext): SkillArgs? {
        val asksProgress = text.contains("领域") || PROGRESS_WORDS.any { text.contains(it) }
        if (!asksProgress) return null
        val domain = VoiceTargetMatcher.match(text, ctx.candidates, ctx.pinyinOf)
            ?.takeIf { it.kind == VoiceTargetKind.DOMAIN } ?: return null
        return mapOf("targetId" to domain.id)
    }

    override fun parseLlmArgs(args: JsonObject, ctx: SkillRuleContext): SkillArgs? {
        val domain = args.targetOrNull(ctx, VoiceTargetKind.DOMAIN) ?: return null
        return mapOf("targetId" to domain.id)
    }

    override fun preview(args: SkillArgs, ctx: SkillRuleContext): SkillPreview {
        val domainId = args.targetIdOrNull()
        val name = ctx.candidates.firstOrNull { it.id == domainId }?.name
        return SkillPreview("查看领域进度", lines = listOf("领域 · ${name ?: "未知"}"))
    }

    override suspend fun execute(args: SkillArgs, ctx: SkillExecutionContext): SkillOutcome {
        val domainId = args.targetIdOrNull() ?: return SkillOutcome.Failed("领域已失效，请重新说一次")
        val domain = ctx.db.domainDao().byId(domainId)
            ?: return SkillOutcome.Failed("找不到这个领域，可能已被删除")
        val level = Economy.levelForExp(domain.totalExp)
        val next = Economy.expToNextLevel(domain.totalExp)
        val progress = next?.let { "距下一级还差 $it 经验" } ?: "已满级"
        val message = buildString {
            append("「${domain.name}」 Lv.$level · 累计经验 ${domain.totalExp}")
            append(" · 头衔「${Titles.currentTitle(domain)}」\n")
            append(progress)
        }
        return SkillOutcome.Done(message, title = "领域进度")
    }

    private val PROGRESS_WORDS = listOf("进度", "等级", "经验", "头衔", "多少级", "怎么样", "如何", "学得")
}
