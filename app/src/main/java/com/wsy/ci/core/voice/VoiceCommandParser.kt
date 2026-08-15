package com.wsy.ci.core.voice

import java.time.LocalDate

/** 把语音识别文本判定成三类可执行意图之一，先命中先返回，兜底为 [VoiceIntent.Unknown]。 */
object VoiceCommandParser {

    fun parse(
        text: String,
        today: LocalDate,
        nowMinute: Int,
        candidates: List<VoiceTarget>,
        pinyinOf: PinyinOf,
    ): VoiceIntent {
        if (START_VERBS.any { text.contains(it) }) {
            VoiceTargetMatcher.match(text, candidates, pinyinOf)?.let { return VoiceIntent.StartTask(it) }
        }
        if (QUERY_WORDS.any { text.contains(it) }) {
            ChineseTimeParser.parseDateRange(text, today)?.let { (from, to) ->
                return VoiceIntent.QuerySchedule(from, to)
            }
        }
        if (BLOCK_WORDS.any { text.contains(it) }) {
            val spans = ChineseTimeParser.parseSpans(text, today, nowMinute)
            if (spans.isNotEmpty()) {
                return VoiceIntent.BlockTime(spans, extractReason(text))
            }
        }
        return VoiceIntent.Unknown(text)
    }

    private fun extractReason(text: String): String =
        REASON_PHRASE_REGEX.find(text)?.value?.trim()?.takeIf { it.isNotBlank() } ?: "临时安排"

    private val START_VERBS = listOf("开始", "启动", "我要做", "我要学", "来一发", "干", "搞")
    private val QUERY_WORDS = listOf("查", "看看", "看", "安排", "日程", "计划", "什么", "有啥")
    private val BLOCK_WORDS = listOf("没空", "没时间", "有事", "占用", "不行", "忙", "要去", "约了", "开会")
    private val REASON_PHRASE_REGEX = Regex("""(要去[^，。,！?\s]{0,6}|约了[^，。,！?\s]{0,6}|开会)""")
}
