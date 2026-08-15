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
