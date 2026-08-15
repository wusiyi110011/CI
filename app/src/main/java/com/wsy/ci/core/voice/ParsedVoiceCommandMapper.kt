package com.wsy.ci.core.voice

import com.wsy.ci.llm.ParsedVoiceCommand
import com.wsy.ci.llm.ParsedVoiceSpan
import java.time.LocalDate

/**
 * 把 LLM 兜底解析出的 [ParsedVoiceCommand] 转成规则解析器同款的 [VoiceIntent]，
 * 这样两条路径可以共用同一套确认浮层和执行逻辑。[originalText] 只在解析失败时
 * 用于兜底的 [VoiceIntent.Unknown]。
 */
fun ParsedVoiceCommand.toVoiceIntent(candidates: List<VoiceTarget>, originalText: String): VoiceIntent {
    return when (intent) {
        "block" -> {
            val parsedSpans = spans.mapNotNull { it.toTimeSpanOrNull() }
            if (parsedSpans.isEmpty()) VoiceIntent.Unknown(originalText)
            else VoiceIntent.BlockTime(parsedSpans, reason.ifBlank { "临时安排" })
        }
        "query" -> {
            val fromDay = from?.let { parseDateOrNull(it) }
            val toDay = to?.let { parseDateOrNull(it) }
            if (fromDay == null || toDay == null) VoiceIntent.Unknown(originalText)
            else VoiceIntent.QuerySchedule(fromDay, toDay)
        }
        "start" -> {
            val target = candidates.firstOrNull { it.id == targetId }
            if (target == null) VoiceIntent.Unknown(originalText) else VoiceIntent.StartTask(target)
        }
        else -> VoiceIntent.Unknown(originalText)
    }
}

private fun ParsedVoiceSpan.toTimeSpanOrNull(): TimeSpan? {
    val day = parseDateOrNull(date) ?: return null
    val startMin = parseHmOrNull(start) ?: return null
    val endMin = parseHmOrNull(end) ?: return null
    if (endMin <= startMin) return null
    return TimeSpan(day, startMin, endMin)
}

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
