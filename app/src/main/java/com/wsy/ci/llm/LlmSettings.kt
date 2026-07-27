package com.wsy.ci.llm

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * LLM 配置存储：
 * - API Key（按厂商，DeepSeek 两模型共用）→ EncryptedSharedPreferences（Keystore 加密，仅存本机）
 * - 任务路由（每类任务用哪个端点/关闭）→ 普通 SharedPreferences
 */
class LlmSettings(context: Context) {

    private val keys: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "llm_keys",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    private val routes: SharedPreferences =
        context.getSharedPreferences("llm_routes", Context.MODE_PRIVATE)

    fun apiKey(keyId: String): String? =
        keys.getString("key_$keyId", null)?.takeIf { it.isNotBlank() }

    fun setApiKey(keyId: String, key: String) {
        keys.edit().putString("key_$keyId", key.trim()).apply()
    }

    fun hasKey(keyId: String): Boolean = apiKey(keyId) != null

    /** 任务路由：返回端点 id；"off" 表示明确关闭；null 表示按 tier 默认分工。 */
    fun route(task: LlmTaskType): String? = routes.getString("route_${task.name}", null)

    fun setRoute(task: LlmTaskType, endpointIdOrOff: String?) {
        routes.edit().apply {
            if (endpointIdOrOff == null) remove("route_${task.name}")
            else putString("route_${task.name}", endpointIdOrOff)
        }.apply()
    }

    /** 解析某任务实际使用的端点；关闭或无 Key 时返回 null（调用方走离线兜底）。 */
    fun resolveEndpoint(task: LlmTaskType): LlmEndpoint? {
        val route = route(task)
        if (route == ROUTE_OFF) return null
        val endpoint = LlmEndpoints.byId(route) ?: LlmEndpoints.defaultFor(task.tier)
        if (hasKey(endpoint.keyId)) return endpoint
        // 首选端点没 Key：退到任何一个有 Key 的端点（视觉任务除外，避免发给纯文本模型）
        if (task.tier == LlmTier.VISION) return null
        return LlmEndpoints.ALL.firstOrNull { it.id != LlmEndpoints.MIMO.id && hasKey(it.keyId) }
    }

    companion object {
        const val ROUTE_OFF = "off"
    }
}
