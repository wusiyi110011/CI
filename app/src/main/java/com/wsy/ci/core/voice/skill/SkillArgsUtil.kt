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

import com.wsy.ci.core.voice.TimeSpan
import com.wsy.ci.core.voice.VoiceTarget
import com.wsy.ci.core.voice.VoiceTargetKind
import com.wsy.ci.core.economy.Difficulty
import java.time.LocalDate
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * LLM 参数校验的统一工具。LLM 输出是外部数据，形状可能畸形，这里一律用
 * `as?` 安全转换而不是会抛异常的 `jsonPrimitive`/`jsonObject`/`jsonArray`——
 * 校验不过返回 null 退化为未识别，绝不能让一次 LLM 结果把 app 崩掉。
 */

private val JsonElement?.asPrimitiveOrNull: JsonPrimitive?
    get() = this as? JsonPrimitive

private val JsonElement?.asObjectOrNull: JsonObject?
    get() = this as? JsonObject

private val JsonElement?.asArrayOrNull: JsonArray?
    get() = this as? JsonArray

/** 规则层 args 里的 targetId（Long 或数字字符串都收）。 */
fun SkillArgs.targetIdOrNull(): Long? = when (val v = this["targetId"]) {
    is Long -> v
    is Int -> v.toLong()
    is String -> v.toLongOrNull()
    else -> null
}

/** LLM args 里的 targetId，取值同上。 */
fun JsonObject.targetIdOrNull(): Long? = this["targetId"]?.asPrimitiveOrNull?.longOrNull

/**
 * 按 id 在候选清单里找对象并校验 kind（拒绝编造）。
 * LLM 裁决结果只有命中候选且 kind 合法才被接受，这是「不静默执行」的关键闸门。
 */
fun List<VoiceTarget>.byIdAndKinds(id: Long, kinds: Set<VoiceTargetKind>): VoiceTarget? =
    firstOrNull { it.id == id && it.kind in kinds }

/** LLM args 里的目标对象：targetId 必须命中候选且 kind 符合要求。 */
fun JsonObject.targetOrNull(ctx: SkillRuleContext, vararg kinds: VoiceTargetKind): VoiceTarget? {
    val id = targetIdOrNull() ?: return null
    return ctx.candidates.byIdAndKinds(id, kinds.toSet())
}

/**
 * LLM args 里的日期区间：from/to 都是合法 ISO 日期、不越界且不超出 [MAX_RANGE_DAYS] 天时返回 [from, to]。
 * 「合法但离谱」的区间（如 0001 年到 9999 年）一律拒绝，防全表扫描。
 */
fun JsonObject.dateRangeOrNull(ctx: SkillRuleContext): Pair<Long, Long>? {
    val from = this["from"]?.asPrimitiveOrNull?.content?.let(::parseDateOrNull) ?: return null
    val to = this["to"]?.asPrimitiveOrNull?.content?.let(::parseDateOrNull) ?: return null
    if (from > to) return null
    val today = ctx.today.toEpochDay()
    if (from < today - MAX_RANGE_DAYS || to > today + MAX_RANGE_DAYS) return null
    return from to to
}

/**
 * LLM args 里的占位时间段数组：逐条校验日期与 HH:mm 并保证 end 晚于 start，
 * 条数限制 [MAX_SPANS] 防止确认卡片被几十条占位刷屏。
 * 任一条不合法就整体拒绝（宁可退回未识别，也不记一个错的时间段）。
 */
fun JsonObject.timeSpansOrNull(): List<TimeSpan>? {
    val spansJson = this["spans"]?.asArrayOrNull ?: return null
    if (spansJson.isEmpty() || spansJson.size > MAX_SPANS) return null
    val spans = spansJson.mapNotNull { el ->
        val obj = el.asObjectOrNull ?: return null
        val day = obj["date"]?.asPrimitiveOrNull?.content?.let(::parseDateOrNull) ?: return null
        val start = obj["start"]?.asPrimitiveOrNull?.content?.let(::parseHmOrNull) ?: return null
        val end = obj["end"]?.asPrimitiveOrNull?.content?.let(::parseHmOrNull) ?: return null
        if (end <= start) return null
        TimeSpan(day, start, end)
    }
    return spans
}

/** LLM args 里的占位原因；缺省给通用文案。 */
fun JsonObject.reasonOrNull(): String? =
    this["reason"]?.asPrimitiveOrNull?.content?.trim()?.takeIf { it.isNotBlank() }

/** 读取并限制 LLM 给出的短文本参数，避免把超长内容直接写入实体或确认卡片。 */
fun JsonObject.textOrNull(key: String, maxLength: Int = 200): String? =
    this[key]?.asPrimitiveOrNull?.takeIf { it.isString }?.content?.trim()
        ?.takeIf { it.isNotBlank() && it.length <= maxLength }

/** 读取 ISO 日期并转成 epochDay；日期错误时返回 null。 */
fun JsonObject.epochDayOrNull(vararg keys: String): Long? = keys.asSequence()
    .mapNotNull { key ->
        this[key]?.asPrimitiveOrNull?.content?.let(::parseDateOrNull)
            ?: this[key]?.asPrimitiveOrNull?.longOrNull
    }
    .firstOrNull()

/** 读取 HH:mm 或当日分钟数，并限制在合法的一天范围内。 */
fun JsonObject.minuteOrNull(vararg keys: String): Int? = keys.asSequence()
    .mapNotNull { key ->
        val element = this[key]?.asPrimitiveOrNull ?: return@mapNotNull null
        element.content.toIntOrNull()?.takeIf { it in 0..24 * 60 }
            ?: parseHmOrNull(element.content)
    }
    .firstOrNull()

/** 将 LLM 给出的难度枚举安全地转换为业务枚举。 */
fun JsonObject.difficultyOrNull(): Difficulty? {
    val raw = this["difficulty"]?.asPrimitiveOrNull?.content?.trim() ?: return null
    return Difficulty.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }
        ?: Difficulty.entries.firstOrNull { it.label == raw }
}

/** 规则层与 LLM 层共用的日期/时间边界校验。 */
fun validTaskTime(epochDay: Long, startMinute: Int, endMinute: Int, today: Long): Boolean =
    epochDay in today..today + MAX_RANGE_DAYS && startMinute in 0 until 24 * 60 &&
        endMinute in 1..24 * 60 && endMinute > startMinute

private fun parseDateOrNull(text: String): Long? =
    runCatching { LocalDate.parse(text).toEpochDay() }.getOrNull()

private fun parseHmOrNull(text: String): Int? {
    val parts = text.trim().split(":")
    if (parts.size != 2) return null
    val h = parts[0].toIntOrNull() ?: return null
    val m = parts[1].toIntOrNull() ?: return null
    if (h !in 0..23 || m !in 0..59) return null
    return h * 60 + m
}

/** 日期区间相对今天的最大前后跨度（天），±5 年足够覆盖任何真实排程。 */
private const val MAX_RANGE_DAYS = 365L * 5

/** 单次占位事件的最大条数。 */
private const val MAX_SPANS = 7
