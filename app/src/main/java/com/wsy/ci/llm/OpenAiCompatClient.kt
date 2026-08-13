package com.wsy.ci.llm

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

@Serializable
private data class ChatMessage(val role: String, val content: String)

@Serializable
private data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double = 0.3,
    @SerialName("max_tokens") val maxTokens: Int = 4096,
)

@Serializable
private data class ChatChoice(val message: ChatMessage)

@Serializable
private data class ChatResponse(val choices: List<ChatChoice> = emptyList())

/** OpenAI 兼容 /chat/completions 客户端，按供应商切换鉴权头。 */
class OpenAiCompatClient(private val settings: LlmSettings) : LlmGateway {

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun isAvailable(task: LlmTaskType): Boolean =
        settings.resolveEndpoint(task) != null

    override suspend fun complete(
        task: LlmTaskType,
        systemPrompt: String,
        userPrompt: String,
    ): LlmResult = withContext(Dispatchers.IO) {
        val provider = settings.resolveEndpoint(task)
            ?: return@withContext LlmResult.Failure(
                "「${task.label}」未配置可用的模型，请到设置页填入 API Key",
                LlmError(
                    code = if (settings.routeSelection(task) == LlmRoute.Off) {
                        LlmErrorCode.ROUTE_OFF
                    } else {
                        LlmErrorCode.API_UNAVAILABLE
                    },
                    message = "「${task.label}」未配置可用的模型，请到设置页填入 API Key",
                    source = LlmErrorSource.API,
                ),
            )
        val key = settings.apiKey(provider.keyId)
            ?: return@withContext LlmResult.Failure(
                "${provider.label} 未配置 API Key",
                LlmError(LlmErrorCode.API_UNAVAILABLE, "${provider.label} 未配置 API Key", LlmErrorSource.API),
            )

        val body = json.encodeToString(
            ChatRequest(
                model = provider.model,
                messages = listOf(
                    ChatMessage("system", systemPrompt),
                    ChatMessage("user", userPrompt),
                ),
            )
        ).toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(provider.baseUrl.trimEnd('/') + "/chat/completions")
            .post(body)
            .apply {
                when (provider.authStyle) {
                    AuthStyle.BEARER -> header("Authorization", "Bearer $key")
                    AuthStyle.API_KEY_HEADER -> header("api-key", key)
                }
            }
            .build()

        try {
            http.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext LlmResult.Failure(
                        "${provider.label} 返回 ${response.code}：${text.take(200)}",
                        LlmError(LlmErrorCode.API_UNAVAILABLE, "${provider.label} 返回 ${response.code}：${text.take(200)}", LlmErrorSource.API),
                    )
                }
                val content = json.decodeFromString<ChatResponse>(text)
                    .choices.firstOrNull()?.message?.content
                if (content.isNullOrBlank()) {
                    LlmResult.Failure(
                        "${provider.label} 返回内容为空",
                        LlmError(LlmErrorCode.API_UNAVAILABLE, "${provider.label} 返回内容为空", LlmErrorSource.API),
                    )
                } else {
                    LlmResult.Success(content)
                }
            }
        } catch (e: IOException) {
            LlmResult.Failure(
                "网络错误：${e.message ?: "连接失败"}",
                LlmError(LlmErrorCode.API_UNAVAILABLE, "网络错误：${e.message ?: "连接失败"}", LlmErrorSource.API),
            )
        } catch (e: Exception) {
            LlmResult.Failure(
                "解析响应失败：${e.message ?: e.javaClass.simpleName}",
                LlmError(LlmErrorCode.API_UNAVAILABLE, "解析响应失败：${e.message ?: e.javaClass.simpleName}", LlmErrorSource.API),
            )
        }
    }
}

/** 从模型返回文本中抠出第一段 JSON（容忍 markdown 代码围栏与前后闲话）。 */
fun extractJson(raw: String): String {
    val fenced = Regex("```(?:json)?\\s*([\\s\\S]*?)```").find(raw)?.groupValues?.get(1)
    val candidate = (fenced ?: raw).trim()
    val start = candidate.indexOfFirst { it == '{' || it == '[' }
    if (start < 0) return candidate
    val open = candidate[start]
    val close = if (open == '{') '}' else ']'
    var depth = 0
    for (i in start until candidate.length) {
        when (candidate[i]) {
            open -> depth++
            close -> {
                depth--
                if (depth == 0) return candidate.substring(start, i + 1)
            }
        }
    }
    return candidate.substring(start)
}
