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

/** 本地推理的提示词参数。保持在任务类型旁边，避免调用方各自猜测模型预算。 */
data class LocalGenerationOptions(
    val thinking: Boolean,
    val maxTokens: Int,
) {
    init {
        require(maxTokens > 0) { "maxTokens 必须大于 0" }
    }
}

val LlmTaskType.localGenerationOptions: LocalGenerationOptions
    get() = when (this) {
        LlmTaskType.ROUTE_GEN -> LocalGenerationOptions(thinking = true, maxTokens = 2048)
        // 复盘关掉思考链：本地小模型的思考链先吃掉大半预算，还容易把正文带成英文
        LlmTaskType.REVIEW_ANALYSIS -> LocalGenerationOptions(thinking = false, maxTokens = 1536)
        // 语音指令的候选任务清单会塞进 prompt，256 token 装不下，抬到 512。
        LlmTaskType.NL_PARSE -> LocalGenerationOptions(thinking = false, maxTokens = 512)
        LlmTaskType.ITEM_PRICING -> LocalGenerationOptions(thinking = false, maxTokens = 384)
        LlmTaskType.TITLE_GEN -> LocalGenerationOptions(thinking = false, maxTokens = 256)
        LlmTaskType.IMAGE_UNDERSTAND -> LocalGenerationOptions(thinking = false, maxTokens = 512)
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

/** 任务路由选择。旧版端点 id 归一化为 [Api]，null 归一化为 [Default]。 */
sealed interface LlmRoute {
    data object Default : LlmRoute
    data object Local : LlmRoute
    data object Off : LlmRoute
    data class Api(val endpoint: LlmEndpoint? = null) : LlmRoute
    data class Invalid(val raw: String) : LlmRoute
}

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
    /**
     * 结构化失败原因。message 字段保留在 Failure 上，兼容已有业务层调用。
     */
    data class Failure(
        val message: String,
        val error: LlmError = LlmError(LlmErrorCode.UNKNOWN, message),
    ) : LlmResult
}

enum class LlmErrorCode {
    ROUTE_OFF,
    ROUTE_INVALID,
    API_UNAVAILABLE,
    LOCAL_UNAVAILABLE,
    LOCAL_NOT_READY,
    LOCAL_LOAD_FAILED,
    LOCAL_INFERENCE_FAILED,
    LOCAL_CANCELLED,
    UNSUPPORTED,
    UNKNOWN,
}

enum class LlmErrorSource { ROUTER, API, LOCAL }

data class LlmError(
    val code: LlmErrorCode,
    val message: String,
    val source: LlmErrorSource = when (code) {
        LlmErrorCode.API_UNAVAILABLE -> LlmErrorSource.API
        LlmErrorCode.LOCAL_UNAVAILABLE,
        LlmErrorCode.LOCAL_NOT_READY,
        LlmErrorCode.LOCAL_LOAD_FAILED,
        LlmErrorCode.LOCAL_INFERENCE_FAILED,
        LlmErrorCode.LOCAL_CANCELLED -> LlmErrorSource.LOCAL
        else -> LlmErrorSource.ROUTER
    },
    val retryable: Boolean = code in setOf(
        LlmErrorCode.API_UNAVAILABLE,
        LlmErrorCode.LOCAL_UNAVAILABLE,
        LlmErrorCode.LOCAL_NOT_READY,
        LlmErrorCode.LOCAL_LOAD_FAILED,
        LlmErrorCode.LOCAL_INFERENCE_FAILED,
    ),
)

/** 带 RGBA 像素的视觉请求。像素按行优先、每像素 4 字节排列。 */
data class LlmImageRequest(
    val rgba: ByteArray,
    val width: Int,
    val height: Int,
    val systemPrompt: String,
    val userPrompt: String,
) {
    init {
        require(width > 0 && height > 0) { "图片尺寸必须大于 0" }
        require(rgba.size == width * height * 4) { "RGBA 像素长度与图片尺寸不匹配" }
    }
}

interface LlmGateway {
    /** 某任务类型当前是否可用（有路由端点且配了 Key）。 */
    suspend fun isAvailable(task: LlmTaskType): Boolean

    suspend fun complete(task: LlmTaskType, systemPrompt: String, userPrompt: String): LlmResult

    /** 视觉请求的可选扩展；文本网关默认返回结构化的不支持错误。 */
    suspend fun completeImage(task: LlmTaskType, request: LlmImageRequest): LlmResult =
        LlmResult.Failure(
            "当前模型不支持图片理解",
            LlmError(LlmErrorCode.UNSUPPORTED, "当前模型不支持图片理解"),
        )
}
