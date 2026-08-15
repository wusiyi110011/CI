package com.wsy.ci.core.voice.skill

/**
 * 技能注册表：登记顺序即规则匹配优先级，每个 skill 的触发词不要跟别的重叠。
 * 加新能力 = 写一个 [AppSkill] + 在这里登记一行，不再需要改解析器/映射器/执行器四处。
 */
class SkillRegistry(val skills: List<AppSkill>) {

    init {
        val ids = skills.map { it.id }
        require(ids.size == ids.toSet().size) { "技能 id 重复：${ids.groupingBy { it }.eachCount().filterValues { it > 1 }.keys}" }
    }

    fun byId(id: String): AppSkill? = skills.firstOrNull { it.id == id }
}
