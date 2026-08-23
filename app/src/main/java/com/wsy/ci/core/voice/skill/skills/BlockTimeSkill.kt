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

import com.wsy.ci.core.util.TimeFormat
import com.wsy.ci.core.voice.ChineseTimeParser
import com.wsy.ci.core.voice.TimeSpan
import com.wsy.ci.core.voice.skill.AppSkill
import com.wsy.ci.core.voice.skill.SkillArgs
import com.wsy.ci.core.voice.skill.SkillDestination
import com.wsy.ci.core.voice.skill.SkillExecutionContext
import com.wsy.ci.core.voice.skill.SkillOutcome
import com.wsy.ci.core.voice.skill.SkillPreview
import com.wsy.ci.core.voice.skill.SkillRuleContext
import com.wsy.ci.core.voice.skill.SkillRisk
import com.wsy.ci.core.voice.skill.reasonOrNull
import com.wsy.ci.core.voice.skill.timeSpansOrNull
import com.wsy.ci.llm.ParsedBlocker
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.serialization.json.JsonObject

/**
 * 记占位事件：迁移原「没空/有事」分支，时间解析逻辑不变，落地仍走
 * `RescheduleFlow.confirmBlockers` 的 diff 预览链路（今日页弹窗确认）。
 */
object BlockTimeSkill : AppSkill {

    override val id = "block_time"
    override val risk = SkillRisk.MODERATE
    override val llmSpec =
        "记一段不可安排时间的占位事件（没空/有事/要去…）；" +
            "args: {\"spans\":[{\"date\":\"yyyy-MM-dd\",\"start\":\"HH:mm\",\"end\":\"HH:mm\"}],\"reason\":\"原因\"}"

    override fun matchRule(text: String, ctx: SkillRuleContext): SkillArgs? {
        if (BLOCK_WORDS.none { text.contains(it) }) return null
        val spans = ChineseTimeParser.parseSpans(text, ctx.today, ctx.nowMinute)
        if (spans.isEmpty()) return null
        return mapOf("spans" to spans, "reason" to extractReason(text))
    }

    override fun parseLlmArgs(args: JsonObject, ctx: SkillRuleContext): SkillArgs? {
        val spans = args.timeSpansOrNull() ?: return null
        return mapOf("spans" to spans, "reason" to (args.reasonOrNull() ?: "临时安排"))
    }

    override fun preview(args: SkillArgs, ctx: SkillRuleContext): SkillPreview {
        val spans = (args["spans"] as? List<*>)?.filterIsInstance<TimeSpan>().orEmpty()
        val lines = buildList {
            spans.forEach { span ->
                add("· ${TimeFormat.date(span.epochDay)} " +
                    "${TimeFormat.minuteOfDay(span.startMinute)}–${TimeFormat.minuteOfDay(span.endMinute)}")
            }
            add(args["reason"] as? String ?: "临时安排")
        }
        return SkillPreview("记占位事件", lines = lines)
    }

    override suspend fun execute(args: SkillArgs, ctx: SkillExecutionContext): SkillOutcome {
        val spans = (args["spans"] as? List<*>)?.filterIsInstance<TimeSpan>().orEmpty()
        if (spans.isEmpty()) return SkillOutcome.Failed("时间段已失效，请重新说一次")
        ctx.rescheduleFlow.confirmBlockers(spans.toParsedBlockers(args["reason"] as? String ?: "临时安排"))
        return SkillOutcome.Done(navigateTo = SkillDestination.TODAY)
    }

    private fun extractReason(text: String): String =
        REASON_PHRASE_REGEX.find(text)?.value?.trim()?.takeIf { it.isNotBlank() } ?: "临时安排"

    /** 原 `VoiceCommandParser` 的占用词表，保持口径一致。 */
    private val BLOCK_WORDS = listOf("没空", "没时间", "有事", "占用", "不行", "忙", "要去", "约了", "开会")
    private val REASON_PHRASE_REGEX = Regex("""(要去[^，。,！?\s]{0,6}|约了[^，。,！?\s]{0,6}|开会)""")
}

/** 占位时间段 → `RescheduleFlow.confirmBlockers` 可直接用的结构。纯转换逻辑，top-level 便于单测。 */
internal fun List<TimeSpan>.toParsedBlockers(reason: String): List<ParsedBlocker> = map { span ->
    ParsedBlocker(
        date = LocalDate.ofEpochDay(span.epochDay).format(DateTimeFormatter.ISO_LOCAL_DATE),
        start = minuteToHm(span.startMinute),
        end = minuteToHm(span.endMinute),
        title = reason,
    )
}

private fun minuteToHm(minute: Int): String = "%02d:%02d".format(minute / 60, minute % 60)
