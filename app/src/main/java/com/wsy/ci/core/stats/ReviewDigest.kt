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

package com.wsy.ci.core.stats

import java.time.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray

/** 一期（周或月）的复盘聚合快照，由 ViewModel 从 DAO 查询结果映射而来。 */
data class ReviewPeriodSnapshot(
    val fromDay: Long,
    val toDay: Long,
    val totalMinutes: Int,
    val plannedCount: Int,
    val doneCount: Int,
    val skippedCount: Int,
    /** 领域名 → 专注分钟，调用方按分钟降序排好。 */
    val minutesByDomain: List<Pair<String, Int>>,
    /** 专注分钟最多的前几个小时（24 小时制），已按热度降序。 */
    val topHours: List<Int>,
    val earnedCi: Long,
    val spentCi: Long,
)

/** 复盘里一条主线的事实：截止倒计时与本期投入，不含任何评价。 */
data class ReviewMainQuest(
    val title: String,
    val deadlineEpochDay: Long?,
    /** 章节总数；chaptersJson 缺失或解析失败为 null。 */
    val chapterCount: Int?,
    val periodMinutes: Int,
)

/** 复盘里一条支线的事实：连击状态与本期打卡次数。 */
data class ReviewSideQuest(
    val title: String,
    val streakDays: Int,
    val bestStreak: Int,
    val lastDoneEpochDay: Long?,
    val periodFocusCount: Int,
)

/**
 * 把两期对比数据拼成喂给 LLM 的中文摘要。
 *
 * 纯函数、不碰 db：聚合口径在 ViewModel 侧与统计屏共用同一套查询，
 * 这里只负责把事实组织成模型好读的文本。复盘的价值在「变化」不在「快照」，
 * 所以每一项都尽量带上期对照。
 */
object ReviewDigest {

    /** 领域太多时截断，防摘要挤爆本地模型的 token 预算。 */
    private const val MAX_DOMAIN_LINES = 6

    fun build(
        granularityLabel: String,
        todayEpochDay: Long,
        current: ReviewPeriodSnapshot,
        previous: ReviewPeriodSnapshot?,
        mainQuests: List<ReviewMainQuest>,
        sideQuests: List<ReviewSideQuest>,
    ): String = buildString {
        appendLine("【对比周期】$granularityLabel")
        appendLine("【总专注】${periodLine(current.totalMinutes, previous?.totalMinutes)}")
        appendLine(
            "【任务完成】本期 计划${current.plannedCount} 完成${current.doneCount} " +
                "跳过${current.skippedCount}（完成率${rate(current.plannedCount, current.doneCount)}）" +
                previous?.let {
                    " vs 上期 计划${it.plannedCount} 完成${it.doneCount} " +
                        "跳过${it.skippedCount}（完成率${rate(it.plannedCount, it.doneCount)}）"
                }.orEmpty()
        )
        appendLine("【按领域】${domainLines(current, previous)}")
        appendLine(
            "【黄金时段】本期 ${current.topHours.joinToString("、") { "${it}点" }}" +
                previous?.let { " vs 上期 ${it.topHours.joinToString("、") { h -> "${h}点" }}" }.orEmpty()
        )
        appendLine(
            "【CI经济】本期 入${current.earnedCi} 出${current.spentCi} " +
                "净存${current.earnedCi - current.spentCi}" +
                previous?.let {
                    " vs 上期 入${it.earnedCi} 出${it.spentCi} 净存${it.earnedCi - it.spentCi}"
                }.orEmpty()
        )
        if (mainQuests.isNotEmpty()) {
            appendLine("【主线进度】")
            mainQuests.forEach { q ->
                val deadline = q.deadlineEpochDay?.let {
                    val days = it - todayEpochDay
                    val tense = when {
                        days < 0 -> "已过期${-days}天"
                        else -> "还剩${days}天"
                    }
                    "，截止${LocalDate.ofEpochDay(it)}（$tense）"
                }.orEmpty()
                val chapters = q.chapterCount?.let { "，共$it 个章节" }.orEmpty()
                appendLine("- ${q.title}$deadline$chapters，本期投入${q.periodMinutes}分钟")
            }
        }
        if (sideQuests.isNotEmpty()) {
            appendLine("【支线连击】")
            sideQuests.forEach { q ->
                val lastDone = q.lastDoneEpochDay?.let {
                    val since = todayEpochDay - it
                    when {
                        since <= 0 -> "今天打过卡"
                        else -> "最近一次打卡距今${since}天"
                    }
                } ?: "从未打卡"
                appendLine(
                    "- ${q.title}：当前连击${q.streakDays}天（最佳${q.bestStreak}），" +
                        "$lastDone，本期专注${q.periodFocusCount}次"
                )
            }
        }
    }

    /** 本期数值 + 环比；上期为 null 或零基线时只报本期。 */
    private fun periodLine(current: Int, previous: Int?): String {
        val base = "本期 ${current}分钟"
        if (previous == null) return base
        return "$base vs 上期 ${previous}分钟（${percentChange(current, previous)}）"
    }

    private fun percentChange(current: Int, previous: Int): String {
        if (previous <= 0) return if (current > 0) "上期无记录" else "持平"
        val pct = (current - previous) * 100.0 / previous
        val sign = if (pct >= 0) "+" else ""
        return "环比$sign${"%.1f".format(pct)}%"
    }

    private fun rate(planned: Int, done: Int): String =
        if (planned <= 0) "无计划" else "${done * 100 / planned}%"

    private fun domainLines(current: ReviewPeriodSnapshot, previous: ReviewPeriodSnapshot?): String {
        if (current.minutesByDomain.isEmpty()) return "本期没有专注记录"
        // 上期整体缺失（首次使用）时整个对照列都不出现；上期存在但没学过该领域才标「无记录」
        val prevByName = previous?.minutesByDomain?.toMap()
        return current.minutesByDomain.take(MAX_DOMAIN_LINES).joinToString(" / ") { (name, minutes) ->
            if (prevByName == null) {
                "$name ${minutes}分钟"
            } else {
                val delta = prevByName[name]?.let { "上期$it" } ?: "上期无记录"
                "$name ${minutes}分钟($delta)"
            }
        }
    }

    /** 主线 chaptersJson 的最小解析：只要章节数量，结构不对就当没有。 */
    fun chapterCount(chaptersJson: String?): Int? {
        if (chaptersJson.isNullOrBlank()) return null
        return runCatching { Json.parseToJsonElement(chaptersJson).jsonArray.size }.getOrNull()
    }
}
