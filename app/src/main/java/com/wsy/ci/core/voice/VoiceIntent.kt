package com.wsy.ci.core.voice

import com.wsy.ci.llm.ParsedBlocker
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** 一段占位时间：哪一天、当天哪段分钟区间。 */
data class TimeSpan(val epochDay: Long, val startMinute: Int, val endMinute: Int)

enum class VoiceTargetKind { TASK, QUEST, DOMAIN }

/** 语音「开始某任务」可命中的候选对象：任务、主线/支线或领域。 */
data class VoiceTarget(val id: Long, val name: String, val kind: VoiceTargetKind)

/** 语音指令解析出的结构化意图。 */
sealed interface VoiceIntent {
    data class BlockTime(val spans: List<TimeSpan>, val reason: String) : VoiceIntent {
        /** 转成可直接喂给 `TodayViewModel.confirmBlockers()` 的结构，复用现有重排 diff 链路。 */
        fun toParsedBlockers(): List<ParsedBlocker> = spans.map { span ->
            ParsedBlocker(
                date = LocalDate.ofEpochDay(span.epochDay).format(DateTimeFormatter.ISO_LOCAL_DATE),
                start = minuteToHm(span.startMinute),
                end = minuteToHm(span.endMinute),
                title = reason,
            )
        }
    }

    data class QuerySchedule(val fromEpochDay: Long, val toEpochDay: Long) : VoiceIntent

    data class StartTask(val target: VoiceTarget) : VoiceIntent

    data class Unknown(val rawText: String) : VoiceIntent
}

private fun minuteToHm(minute: Int): String = "%02d:%02d".format(minute / 60, minute % 60)
