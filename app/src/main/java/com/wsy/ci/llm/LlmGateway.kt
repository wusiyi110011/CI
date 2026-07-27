package com.wsy.ci.llm

/** 任务分层：特别复杂 → deepseek-v4-pro；稍简单 → deepseek-v4-flash；需要视觉 → mimo-v2.5。 */
enum class LlmTier(val label: String) {
    HEAVY("复杂推理"),
    LIGHT("轻量任务"),
    VISION("视觉理解"),
}

/** LLM 任务分类，按 tier 路由到不同模型，未配置 Key 或关闭时走离线兜底。 */
enum class LlmTaskType(val label: String, val tier: LlmTier) {
    ROUTE_GEN("学习路线生成", LlmTier.HEAVY),
    REVIEW_ANALYSIS("深度复盘分析", LlmTier.HEAVY),
    NL_PARSE("自然语言解析", LlmTier.LIGHT),
    ITEM_PRICING("商品估价", LlmTier.LIGHT),
    TITLE_GEN("头衔生成", LlmTier.LIGHT),
    IMAGE_UNDERSTAND("图片理解", LlmTier.VISION),
}

/** 鉴权头风格：DeepSeek 用 Bearer，MiMo 用 api-key 头。 */
enum class AuthStyle { BEARER, API_KEY_HEADER }

/**
 * 一个可路由的模型端点。keyId 是密钥归属（同厂商多模型共用一个 Key），
 * API Key 本身单独走加密存储。
 */
data class LlmEndpoint(
    val id: String,
    val label: String,
    val baseUrl: String,
    val model: String,
    val authStyle: AuthStyle,
    val keyId: String,
)

object LlmEndpoints {
    const val KEY_DEEPSEEK = "deepseek"
    const val KEY_MIMO = "mimo"

    val DEEPSEEK_PRO = LlmEndpoint(
        id = "deepseek-pro",
        label = "DeepSeek V4 Pro",
        baseUrl = "https://api.deepseek.com",
        model = "deepseek-v4-pro",
        authStyle = AuthStyle.BEARER,
        keyId = KEY_DEEPSEEK,
    )
    val DEEPSEEK_FLASH = LlmEndpoint(
        id = "deepseek-flash",
        label = "DeepSeek V4 Flash",
        baseUrl = "https://api.deepseek.com",
        model = "deepseek-v4-flash",
        authStyle = AuthStyle.BEARER,
        keyId = KEY_DEEPSEEK,
    )
    val MIMO = LlmEndpoint(
        id = "mimo",
        label = "MiMo V2.5（视觉）",
        baseUrl = "https://api.xiaomimimo.com/v1",
        model = "mimo-v2.5",
        authStyle = AuthStyle.API_KEY_HEADER,
        keyId = KEY_MIMO,
    )
    val ALL = listOf(DEEPSEEK_PRO, DEEPSEEK_FLASH, MIMO)

    fun byId(id: String?): LlmEndpoint? = ALL.firstOrNull { it.id == id }

    /** 默认分工：复杂 → Pro，轻量 → Flash，视觉 → MiMo。 */
    fun defaultFor(tier: LlmTier): LlmEndpoint = when (tier) {
        LlmTier.HEAVY -> DEEPSEEK_PRO
        LlmTier.LIGHT -> DEEPSEEK_FLASH
        LlmTier.VISION -> MIMO
    }
}

sealed interface LlmResult {
    data class Success(val content: String) : LlmResult
    data class Failure(val message: String) : LlmResult
}

interface LlmGateway {
    /** 某任务类型当前是否可用（有路由端点且配了 Key）。 */
    suspend fun isAvailable(task: LlmTaskType): Boolean

    suspend fun complete(task: LlmTaskType, systemPrompt: String, userPrompt: String): LlmResult
}
