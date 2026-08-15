package com.wsy.ci.llm

import com.wsy.ci.core.economy.Economy
import com.wsy.ci.core.economy.Rarity
import com.wsy.ci.core.voice.VoiceTarget
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

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

@Serializable
data class ParsedVoiceSpan(val date: String, val start: String, val end: String)

/** 规则解析器兜不住时，LLM 兜底解析出的语音指令结构；转成 [com.wsy.ci.core.voice.VoiceIntent] 复用同一套确认/执行逻辑。 */
@Serializable
data class ParsedVoiceCommand(
    val intent: String,
    val spans: List<ParsedVoiceSpan> = emptyList(),
    val reason: String = "",
    val from: String? = null,
    val to: String? = null,
    val targetId: Long? = null,
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
     * 规则解析器兜不住时的语音指令兜底：候选清单以 `id|名称` 塞进 prompt，明确要求
     * targetId 只能从清单里选，禁止编造——这是「开始某任务」匹配准确率的关键。
     */
    suspend fun parseVoiceCommand(text: String, candidates: List<VoiceTarget>): LlmParsed<ParsedVoiceCommand> {
        val today = LocalDate.now()
        val candidateLines = candidates.take(MAX_VOICE_CANDIDATES).joinToString("\n") { "${it.id}|${it.name}" }
        val system = """
            你把中文语音指令解析成结构化意图。今天是 ${today.format(DateTimeFormatter.ISO_LOCAL_DATE)}（${today.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, Locale.CHINA)}）。
            intent 只能是以下三选一：
            - "block"：记一段不可安排的时间（没空/有事/要去…），配合 spans 和 reason
            - "query"：查看某天或某段时间的安排，配合 from 和 to
            - "start"：开始一个具体任务/主线/支线，targetId 只能从下面候选清单里选 id，禁止编造：
            $candidateLines
            「上午」=09:00-12:00，「下午」=14:00-18:00，「晚上」=19:00-22:00，「全天」=08:00-22:00。
            只输出 JSON，不要解释，不需要的字段留空或省略：
            {"intent":"block|query|start","spans":[{"date":"yyyy-MM-dd","start":"HH:mm","end":"HH:mm"}],"reason":"","from":"yyyy-MM-dd","to":"yyyy-MM-dd","targetId":123}
        """.trimIndent()
        return when (val result = gateway.complete(LlmTaskType.NL_PARSE, system, text)) {
            is LlmResult.Failure -> LlmParsed.Err(result.message, result.error)
            is LlmResult.Success -> try {
                LlmParsed.Ok(json.decodeFromString<ParsedVoiceCommand>(extractJson(result.content)))
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

    /** 复盘分析（M4）：统计摘要 → 洞察与建议（自由文本）。 */
    suspend fun analyzeStats(summary: String): LlmParsed<String> {
        val system = """
            你是学习教练。下面是用户一段时间的学习统计摘要，请给出：
            1) 三条最重要的洞察（数据说话）；2) 可能的归因；3) 下周的三条具体可执行建议。
            用简体中文，条理清晰，总字数 300 字以内。
        """.trimIndent()
        return when (val result = gateway.complete(LlmTaskType.REVIEW_ANALYSIS, system, summary)) {
            is LlmResult.Failure -> LlmParsed.Err(result.message, result.error)
            is LlmResult.Success -> LlmParsed.Ok(result.content.trim())
        }
    }

    private companion object {
        /** 候选清单太长会挤爆 NL_PARSE 的 token 预算，超过这个数就截断。 */
        const val MAX_VOICE_CANDIDATES = 40
    }
}
