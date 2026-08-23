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
import com.wsy.ci.core.voice.skill.AppSkill
import com.wsy.ci.core.voice.skill.SkillArgs
import com.wsy.ci.core.voice.skill.SkillExecutionContext
import com.wsy.ci.core.voice.skill.SkillOutcome
import com.wsy.ci.core.voice.skill.SkillPreview
import com.wsy.ci.core.voice.skill.SkillRuleContext
import com.wsy.ci.core.voice.skill.SkillRisk
import com.wsy.ci.core.voice.skill.dateRangeOrNull
import kotlinx.serialization.json.JsonObject

/** 日程查询：迁移原「查某天安排」能力，日期解析逻辑不变，结果走日程结果浮层。 */
object QueryScheduleSkill : AppSkill {

    override val id = "query_schedule"
    override val risk = SkillRisk.SAFE
    override val llmSpec = "查看某天或某段时间的任务安排；args: {\"from\":\"yyyy-MM-dd\",\"to\":\"yyyy-MM-dd\"}"

    override fun matchRule(text: String, ctx: SkillRuleContext): SkillArgs? {
        if (QUERY_WORDS.none { text.contains(it) }) return null
        val range = ChineseTimeParser.parseDateRange(text, ctx.today) ?: return null
        return mapOf("from" to range.first, "to" to range.second)
    }

    override fun parseLlmArgs(args: JsonObject, ctx: SkillRuleContext): SkillArgs? {
        val range = args.dateRangeOrNull(ctx) ?: return null
        return mapOf("from" to range.first, "to" to range.second)
    }

    override fun preview(args: SkillArgs, ctx: SkillRuleContext): SkillPreview {
        val from = (args["from"] as? Long) ?: ctx.today.toEpochDay()
        val to = (args["to"] as? Long) ?: from
        val rangeText = if (from == to) TimeFormat.date(from) else "${TimeFormat.date(from)} ~ ${TimeFormat.date(to)}"
        return SkillPreview("查看日程", lines = listOf(rangeText))
    }

    override suspend fun execute(args: SkillArgs, ctx: SkillExecutionContext): SkillOutcome {
        val from = (args["from"] as? Long) ?: return SkillOutcome.Failed("时间段已失效，请重新说一次")
        val to = (args["to"] as? Long) ?: from
        val tasks = ctx.db.taskDao().byRange(from, to)
        return SkillOutcome.Done(scheduleTasks = tasks)
    }

    /** 原 `VoiceCommandParser` 的查询词表，保持口径一致。 */
    private val QUERY_WORDS = listOf("查", "看看", "看", "安排", "日程", "计划", "什么", "有啥")
}
