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

package com.wsy.ci.core.voice.skill

import com.wsy.ci.llm.ParsedSkillCall

/**
 * 语音意图路由（取代原 `VoiceCommandParser` 的角色）：
 * 规则层按登记顺序找第一个命中的技能；LLM 兜底按 skill 字段查表并做参数候选校验，
 * 查不到或校验不过返回 null（退化为未识别，不静默执行）。
 */
class VoiceSkillRouter(private val registry: SkillRegistry) {

    fun matchByRule(text: String, ctx: SkillRuleContext): SkillInvocation? {
        for (skill in registry.skills) {
            val args = skill.matchRule(text, ctx)
            if (args != null) return SkillInvocation(skill, args)
        }
        return null
    }

    fun matchFromLlm(parsed: ParsedSkillCall, ctx: SkillRuleContext): SkillInvocation? {
        val skill = registry.byId(parsed.skill) ?: return null
        val args = skill.parseLlmArgs(parsed.args, ctx) ?: return null
        return SkillInvocation(skill, args)
    }
}
