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

package com.wsy.ci.llm

import android.util.Log
import com.wsy.ci.core.economy.Economy
import com.wsy.ci.core.economy.Rarity
import com.wsy.ci.core.voice.VoiceTarget
import com.wsy.ci.core.voice.skill.AppSkill
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

@Serializable
data class RouteChapter(
    val title: String,
    val hours: Double,
    val resources: List<String> = emptyList(),
)

@Serializable
data class RoutePlan(
    val domain: String,
    val chapters: List<RouteChapter>,
    val titles: List<String> = emptyList(),
)

@Serializable
data class ParsedBlocker(
    val date: String,
    val start: String,
    val end: String,
    val title: String = "有事",
)

@Serializable
data class PricedItem(
    val priceYuan: Double,
    val emoji: String = "🎁",
    val description: String = "",
)

/** 复盘分析的结构化产出：洞察 / 风险 / 建议，三组各自独立渲染。 */
@Serializable
data class ReviewAnalysis(
    val insights: List<String> = emptyList(),
    val risks: List<String> = emptyList(),
    val actions: List<String> = emptyList(),
)

/**
 * 规则层兜不住时 LLM 裁决出的技能调用：skill 必须命中技能清单，args 交给对应技能的
 * `parseLlmArgs` 做候选校验，编造的 id 在那一关被拒掉。
 */
@Serializable
data class ParsedSkillCall(
    val skill: String,
    val args: JsonObject = JsonObject(emptyMap()),
)

sealed interface LlmParsed<out T> {
    data class Ok<T>(val value: T) : LlmParsed<T>
    data class Err(val message: String, val error: LlmError? = null) : LlmParsed<Nothing>
}

/** 面向业务的 LLM 服务：拼 prompt、调网关、解析结构化 JSON。 */
class LlmService(private val gateway: LlmGateway) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun isAvailable(task: LlmTaskType) = gateway.isAvailable(task)

    /** 学习路线生成：领域 + 每周可投入小时 + 目标 → 章节化主线 + 6 级头衔。 */
    suspend fun generateRoute(
        domainName: String,
        weeklyHours: Int,
        goal: String,
    ): LlmParsed<RoutePlan> {
        val system = """
            你是学习规划专家。用户给出想学的领域、每周可投入时间和目标，你输出一条结构化学习主线。
            只输出 JSON，不要任何解释，格式：
            {"domain":"领域名","chapters":[{"title":"章节名","hours":8.5,"resources":["资源1"]}],"titles":["6个由低到高的领域专属趣味头衔"]}
            要求：章节 4~10 个，按学习顺序排列，hours 为预估学习小时数；titles 必须恰好 6 个、
            贴合领域文化、由新手到宗师递进（如深度学习：炼丹学徒→调参术士→…→领域宗师）。
        """.trimIndent()
        val user = "领域：$domainName\n每周可投入：$weeklyHours 小时\n目标：${goal.ifBlank { "系统入门到能实战" }}"
        return when (val result = gateway.complete(LlmTaskType.ROUTE_GEN, system, user)) {
            is LlmResult.Failure -> LlmParsed.Err(result.message, result.error)
            is LlmResult.Success -> try {
                val plan = json.decodeFromString<RoutePlan>(extractJson(result.content))
                if (plan.chapters.isEmpty()) LlmParsed.Err("模型未返回章节，请重试")
                else LlmParsed.Ok(plan)
            } catch (e: Exception) {
                LlmParsed.Err("路线 JSON 解析失败：${e.message?.take(120)}")
            }
        }
    }

    /** 自然语言 → 占位事件列表（「明天下午2-5点有事」→ blocker）。 */
    suspend fun parseBlockers(text: String): LlmParsed<List<ParsedBlocker>> {
        val today = LocalDate.now()
        val system = """
            你把中文口语时间描述解析成日程占位事件。今天是 ${today.format(DateTimeFormatter.ISO_LOCAL_DATE)}（${today.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, Locale.CHINA)}）。
            只输出 JSON 数组，不要解释：
            [{"date":"yyyy-MM-dd","start":"HH:mm","end":"HH:mm","title":"事件简述"}]
            「上午」=09:00-12:00，「下午」=14:00-18:00，「晚上」=19:00-22:00，「全天」=08:00-22:00。
        """.trimIndent()
        return when (val result = gateway.complete(LlmTaskType.NL_PARSE, system, text)) {
            is LlmResult.Failure -> LlmParsed.Err(result.message, result.error)
            is LlmResult.Success -> try {
                val list = json.decodeFromString<List<ParsedBlocker>>(extractJson(result.content))
                if (list.isEmpty()) LlmParsed.Err("没有解析出时间段，请说得再具体些")
                else LlmParsed.Ok(list)
            } catch (e: Exception) {
                LlmParsed.Err("解析失败：${e.message?.take(120)}")
            }
        }
    }

    /**
     * 规则解析器兜不住时的语音指令兜底：把技能注册表里全部技能说明拼进 prompt，
     * 由模型在清单里选一个并给出参数；候选清单以 `id|名称` 塞进 prompt，明确要求
     * 引用对象的 id 只能从清单里选——编造的 id 还会在技能侧的参数校验里被再拒一次。
     */
    suspend fun parseSkillCall(
        text: String,
        skills: List<AppSkill>,
        candidates: List<VoiceTarget>,
    ): LlmParsed<ParsedSkillCall> {
        val today = LocalDate.now()
        val skillLines = skills.joinToString("\n") { "- ${it.id}：${it.llmSpec}" }
        val candidateLines = candidates.take(MAX_VOICE_CANDIDATES).joinToString("\n") { "${it.id}|${it.name}" }
        val system = """
            你把中文语音指令解析成对 app 功能的一次调用。今天是 ${today.format(DateTimeFormatter.ISO_LOCAL_DATE)}（${today.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, Locale.CHINA)}）。
            可选功能如下，skill 只能从下面列表里选，禁止编造：
            $skillLines
            需要引用对象（任务/任务线/领域/商品）时，targetId 只能从下面候选清单里选 id，禁止编造：
            $candidateLines
            「上午」=09:00-12:00，「下午」=14:00-18:00，「晚上」=19:00-22:00，「全天」=08:00-22:00。
            只输出 JSON，不要解释，不需要的 args 字段留空或省略：
            {"skill":"功能id","args":{...}}
        """.trimIndent()
        return when (val result = gateway.complete(LlmTaskType.NL_PARSE, system, text)) {
            is LlmResult.Failure -> LlmParsed.Err(result.message, result.error)
            is LlmResult.Success -> try {
                LlmParsed.Ok(json.decodeFromString<ParsedSkillCall>(extractJson(result.content)))
            } catch (e: Exception) {
                LlmParsed.Err("语音指令解析失败：${e.message?.take(120)}")
            }
        }
    }

    /** 为已有领域补生成 6 级头衔。 */
    suspend fun generateTitles(domainName: String): LlmParsed<List<String>> {
        val system = """
            为一个学习领域生成 6 个由低到高的趣味头衔，贴合该领域的行话与文化。
            只输出 JSON 数组：["头衔1","头衔2","头衔3","头衔4","头衔5","头衔6"]
        """.trimIndent()
        return when (val result = gateway.complete(LlmTaskType.TITLE_GEN, system, "领域：$domainName")) {
            is LlmResult.Failure -> LlmParsed.Err(result.message, result.error)
            is LlmResult.Success -> try {
                val titles = json.decodeFromString<List<String>>(extractJson(result.content))
                if (titles.size >= Economy.MAX_LEVEL) LlmParsed.Ok(titles.take(Economy.MAX_LEVEL))
                else LlmParsed.Err("头衔数量不足 6 个")
            } catch (e: Exception) {
                LlmParsed.Err("头衔解析失败：${e.message?.take(120)}")
            }
        }
    }

    /** 商品估价：名称 → 现实价格（元）+ 图标 + 描述；CI 价与品质由固定公式换算。 */
    suspend fun priceItem(name: String): LlmParsed<Triple<Long, Rarity, PricedItem>> {
        val system = """
            你是商品估价助手。用户给出一件奖励（实物、服务或虚拟消费），估算其现实人民币价格。
            只输出 JSON：{"priceYuan":123.0,"emoji":"🎬","description":"一句话描述"}
        """.trimIndent()
        return when (val result = gateway.complete(LlmTaskType.ITEM_PRICING, system, name)) {
            is LlmResult.Failure -> LlmParsed.Err(result.message, result.error)
            is LlmResult.Success -> try {
                val item = json.decodeFromString<PricedItem>(extractJson(result.content))
                val priceCi = (item.priceYuan * Economy.CI_PER_YUAN).toLong().coerceAtLeast(1)
                LlmParsed.Ok(Triple(priceCi, Economy.suggestRarity(priceCi), item))
            } catch (e: Exception) {
                LlmParsed.Err("估价解析失败：${e.message?.take(120)}")
            }
        }
    }

    /**
     * 复盘分析（M4）：两期对比摘要 → 结构化洞察/风险/建议。
     *
     * 语言约束靠两层：指令按「三明治」钉三遍（system 首句、尾句、user 末尾），
     * 再放一个中文输出样板——真机实测小模型对指令的遵循时好时坏（同 prompt
     * 一轮中文一轮英文），但对样板的模仿稳定得多。样板内容刻意与真实复盘
     * 无关，防照抄。整体结构塌掉时由 [ReviewAnalysisParser] 兜底。
     */
    suspend fun analyzeStats(digest: String): LlmParsed<ReviewAnalysis> {
        val system = """
            你必须全程使用简体中文回答，包括所有 JSON 字段里的值。
            你是学习教练。根据用户给出的学习统计对比摘要做深度复盘，输出：
            insights：最重要的洞察，基于本期与上期的变化而非罗列数字，2~4 条；
            risks：风险与可能的归因，0~3 条；
            actions：下周的具体可执行建议，具体到可以直接照做，2~3 条。
            只输出 JSON，不要任何解释，输出必须严格模仿下面这个样板的格式与语言：
            {"insights":["本期总投入比上期下降三成，缺口集中在工作日晚上。","某领域的投入翻倍，是本期最大的进步。"],"risks":["连续两天空白会让打卡断签。"],"actions":["下周一到周五每晚八点半各安排四十五分钟专注。"]}
            再次强调：所有字段的值都必须使用简体中文。
        """.trimIndent()
        val user = "$digest\n请全程使用简体中文回答。"
        return when (val result = gateway.complete(LlmTaskType.REVIEW_ANALYSIS, system, user)) {
            is LlmResult.Failure -> LlmParsed.Err(result.message, result.error)
            is LlmResult.Success -> {
                // 小模型的坏输出只有看到原文才能修：围栏、think 泄漏、token 截断各有各的相
                Log.i(TAG, "复盘原始输出（${result.content.length} 字符）：\n${result.content}")
                val parsed = try {
                    json.decodeFromString<ReviewAnalysis>(extractJson(result.content))
                } catch (e: Exception) {
                    // 整体结构塌掉（实测：数组没闭合就写下一个键）走容错提取
                    Log.w(TAG, "标准 JSON 解析失败，改用容错提取：${e.message?.take(200)}")
                    ReviewAnalysisParser.parse(result.content)
                }
                if (parsed.insights.isEmpty() && parsed.risks.isEmpty() && parsed.actions.isEmpty()) {
                    LlmParsed.Err("模型未返回有效内容，请重试")
                } else {
                    LlmParsed.Ok(parsed)
                }
            }
        }
    }

    private companion object {
        /** 候选清单太长会挤爆 NL_PARSE 的 token 预算，超过这个数就截断。 */
        const val MAX_VOICE_CANDIDATES = 40

        const val TAG = "LlmService"
    }
}
