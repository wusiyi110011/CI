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

import com.wsy.ci.core.voice.skill.AppSkill
import com.wsy.ci.core.voice.skill.SkillArgs
import com.wsy.ci.core.voice.skill.SkillDestination
import com.wsy.ci.core.voice.skill.SkillExecutionContext
import com.wsy.ci.core.voice.skill.SkillOutcome
import com.wsy.ci.core.voice.skill.SkillPreview
import com.wsy.ci.core.voice.skill.SkillRuleContext
import com.wsy.ci.core.voice.skill.SkillRisk
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** 页面跳转：泛化原来的 openCalendar 特例，「打开 XX」直接切屏。 */
object NavigateSkill : AppSkill {

    override val id = "navigate"
    override val risk = SkillRisk.SAFE
    override val llmSpec =
        "打开/切换到某个页面；args: {\"destination\": \"TODAY|CALENDAR|QUEST|SHOP|STATS|SETTINGS\"}"

    override fun matchRule(text: String, ctx: SkillRuleContext): SkillArgs? {
        if (OPEN_WORDS.none { text.contains(it) }) return null
        val destination = DESTINATION_WORDS.firstOrNull { (word, _) -> text.contains(word) }?.second
            ?: return null
        return mapOf("destination" to destination)
    }

    override fun parseLlmArgs(args: JsonObject, ctx: SkillRuleContext): SkillArgs? {
        // as? 安全转换：LLM 输出畸形形状时返回 null 退化为未识别，不抛异常
        val raw = (args["destination"] as? JsonPrimitive)?.content ?: return null
        val destination = SkillDestination.entries.firstOrNull { it.name == raw } ?: return null
        return mapOf("destination" to destination)
    }

    override fun preview(args: SkillArgs, ctx: SkillRuleContext): SkillPreview {
        val destination = args["destination"] as? SkillDestination
            ?: return SkillPreview("打开页面")
        return SkillPreview("打开页面", lines = listOf("· ${destination.label}"))
    }

    override suspend fun execute(args: SkillArgs, ctx: SkillExecutionContext): SkillOutcome {
        val destination = args["destination"] as? SkillDestination
            ?: return SkillOutcome.Failed("目的地已失效，请重新说一次")
        return SkillOutcome.Done(navigateTo = destination)
    }

    private val OPEN_WORDS = listOf("打开", "切到", "切换到", "进入", "去")

    /** 目的地词 → 页面映射；词与词的顺序即同现时的优先顺序。 */
    private val DESTINATION_WORDS = listOf(
        "商城" to SkillDestination.SHOP,
        "商店" to SkillDestination.SHOP,
        "货架" to SkillDestination.SHOP,
        "日程" to SkillDestination.CALENDAR,
        "日历" to SkillDestination.CALENDAR,
        "任务线" to SkillDestination.QUEST,
        "任务" to SkillDestination.QUEST,
        "复盘" to SkillDestination.STATS,
        "统计" to SkillDestination.STATS,
        "数据" to SkillDestination.STATS,
        "设置" to SkillDestination.SETTINGS,
        "今日" to SkillDestination.TODAY,
        "今天" to SkillDestination.TODAY,
    )
}
