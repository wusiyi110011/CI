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

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * LLM 配置存储：
 * - API Key（按厂商，DeepSeek 两模型共用）→ EncryptedSharedPreferences（Keystore 加密，仅存本机）
 * - 任务路由（每类任务用哪个端点/关闭）→ 普通 SharedPreferences
 */
interface LlmRouteProvider {
    fun routeSelection(task: LlmTaskType): LlmRoute
}

class LlmSettings(context: Context) : LlmRouteProvider {

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

    /** 读取并归一化路由，兼容旧版端点 id、null 默认值和新路由关键字。 */
    override fun routeSelection(task: LlmTaskType): LlmRoute = LlmRouteParser.parse(route(task))

    fun setRoute(task: LlmTaskType, endpointIdOrOff: String?) {
        routes.edit().apply {
            if (endpointIdOrOff == null) remove("route_${task.name}")
            else putString("route_${task.name}", endpointIdOrOff)
        }.apply()
    }

    /** 解析某任务实际使用的端点；关闭或无 Key 时返回 null（调用方走离线兜底）。 */
    fun resolveEndpoint(task: LlmTaskType): LlmEndpoint? =
        resolveApiEndpoint(task, routeSelection(task), ::hasKey)

    companion object {
        const val ROUTE_OFF = "off"
        const val ROUTE_DEFAULT = "default"
        const val ROUTE_API = "api"
        const val ROUTE_LOCAL = "local"
    }
}

/**
 * 解析 API 端点的纯逻辑。用户显式选中的端点必须严格遵守：缺 Key 就不可用，不能把
 * 学习内容静默发给另一家供应商。只有默认路由和未指定端点的「API 自动」允许兜底。
 */
internal fun resolveApiEndpoint(
    task: LlmTaskType,
    selection: LlmRoute,
    hasKey: (String) -> Boolean,
): LlmEndpoint? {
    val endpoint = when (selection) {
        LlmRoute.Default -> LlmEndpoints.defaultFor(task.tier)
        is LlmRoute.Api -> selection.endpoint ?: LlmEndpoints.defaultFor(task.tier)
        LlmRoute.Local,
        LlmRoute.Off,
        is LlmRoute.Invalid -> return null
    }
    if (hasKey(endpoint.keyId)) return endpoint
    if (selection is LlmRoute.Api && selection.endpoint != null) return null
    if (task.tier == LlmTier.VISION) return null
    return LlmEndpoints.ALL.firstOrNull {
        it.id != LlmEndpoints.MIMO.id && hasKey(it.keyId)
    }
}

/** 路由字符串解析器。纯 Kotlin，便于在 JVM 测试中覆盖设置迁移和错误分支。 */
object LlmRouteParser {
    fun parse(raw: String?): LlmRoute {
        val value = raw?.trim()?.lowercase().orEmpty()
        if (value.isBlank() || value == LlmSettings.ROUTE_DEFAULT) return LlmRoute.Default
        if (value == LlmSettings.ROUTE_LOCAL) return LlmRoute.Local
        if (value == LlmSettings.ROUTE_OFF) return LlmRoute.Off
        if (value == LlmSettings.ROUTE_API) return LlmRoute.Api()
        if (value.startsWith("api:")) {
            val endpointId = value.removePrefix("api:").trim()
            return if (endpointId.isBlank()) LlmRoute.Api()
            else LlmEndpoints.byId(endpointId)?.let { LlmRoute.Api(it) }
                ?: LlmRoute.Invalid(raw.orEmpty())
        }
        // 旧版本直接保存 deepseek-pro/deepseek-flash/mimo。
        return LlmEndpoints.byId(value)?.let { LlmRoute.Api(it) }
            ?: LlmRoute.Invalid(raw.orEmpty())
    }
}
