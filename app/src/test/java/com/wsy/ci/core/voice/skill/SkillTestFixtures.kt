package com.wsy.ci.core.voice.skill

import com.wsy.ci.core.voice.PinyinOf
import com.wsy.ci.core.voice.VoiceTarget
import com.wsy.ci.core.voice.VoiceTargetKind
import java.time.LocalDate

/** 技能单测共享的固定上下文与候选清单，所有参数注入、无 Android 依赖。 */
internal object SkillTestFixtures {

    val TODAY: LocalDate = LocalDate.of(2026, 8, 15)
    val NOW_MINUTE: Int = 10 * 60

    /** 测试用拼音表：用例候选名全是 ASCII，非汉字按约定返回小写原字符。 */
    val PINYIN: PinyinOf = { it.lowercaseChar().toString() }

    fun task(id: Long, name: String) = VoiceTarget(id, name, VoiceTargetKind.TASK)
    fun quest(id: Long, name: String) = VoiceTarget(id, name, VoiceTargetKind.QUEST)
    fun domain(id: Long, name: String) = VoiceTarget(id, name, VoiceTargetKind.DOMAIN)
    fun item(id: Long, name: String) = VoiceTarget(id, name, VoiceTargetKind.SHOP_ITEM)

    fun ctx(candidates: List<VoiceTarget>, hasRunningSession: Boolean = false) =
        SkillRuleContext(TODAY, NOW_MINUTE, candidates, PINYIN, hasRunningSession)

    /** 覆盖四种 kind 的标准候选，测试「同类词不同目标」的消歧。 */
    val STANDARD_CANDIDATES: List<VoiceTarget> = listOf(
        task(1, "Rust 所有权"),
        task(2, "英语听力"),
        quest(10, "机器学习"),
        quest(11, "雅思冲刺"),
        domain(20, "物理"),
        domain(21, "数学"),
        item(30, "奶茶"),
        item(31, "电影票"),
    )
}
