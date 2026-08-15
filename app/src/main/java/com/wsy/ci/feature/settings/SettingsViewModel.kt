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

package com.wsy.ci.feature.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wsy.ci.CiApp
import com.wsy.ci.core.settings.ThemeMode
import com.wsy.ci.core.settings.MotionMode
import com.wsy.ci.llm.LlmEndpoints
import com.wsy.ci.llm.LlmResult
import com.wsy.ci.llm.LlmSettings
import com.wsy.ci.llm.LlmTaskType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val container = (app as CiApp).container
    private val settings = container.llmSettings
    private val appSettings = container.appSettings

    /** 外观：跟随系统 / 明亮 / 黑暗。写入后整棵 Compose 树立刻换色。 */
    val themeMode: StateFlow<ThemeMode> = appSettings.themeMode
    val motionMode: StateFlow<MotionMode> = appSettings.motionMode

    fun setThemeMode(mode: ThemeMode) = appSettings.setThemeMode(mode)
    fun setMotionMode(mode: MotionMode) = appSettings.setMotionMode(mode)

    val keyConfigured = MutableStateFlow(loadKeyStates())
    val routes = MutableStateFlow(loadRoutes())
    val message = MutableStateFlow<String?>(null)
    val testing = MutableStateFlow(false)

    private fun loadKeyStates(): Map<String, Boolean> = mapOf(
        LlmEndpoints.KEY_DEEPSEEK to settings.hasKey(LlmEndpoints.KEY_DEEPSEEK),
        LlmEndpoints.KEY_MIMO to settings.hasKey(LlmEndpoints.KEY_MIMO),
    )

    private fun loadRoutes(): Map<LlmTaskType, String?> =
        LlmTaskType.entries.associateWith { settings.route(it) }

    fun saveKey(keyId: String, key: String) {
        if (key.isBlank()) return
        settings.setApiKey(keyId, key)
        keyConfigured.value = loadKeyStates()
        message.value = "已保存 ${if (keyId == LlmEndpoints.KEY_DEEPSEEK) "DeepSeek" else "MiMo"} Key（仅存本机加密存储）"
    }

    fun setRoute(task: LlmTaskType, endpointIdOrOff: String?) {
        settings.setRoute(task, endpointIdOrOff)
        routes.value = loadRoutes()
    }

    /** 用一条最小请求测试指定端点连通性。 */
    fun testEndpoint(endpointId: String) {
        val endpoint = LlmEndpoints.byId(endpointId) ?: return
        viewModelScope.launch {
            testing.value = true
            // 临时把 TITLE_GEN 路由到目标端点做一次冒烟，测完还原
            val old = settings.route(LlmTaskType.TITLE_GEN)
            settings.setRoute(LlmTaskType.TITLE_GEN, endpoint.id)
            val result = container.llmService.generateTitles("测试")
            settings.setRoute(LlmTaskType.TITLE_GEN, old)
            routes.value = loadRoutes()
            message.value = when (result) {
                is com.wsy.ci.llm.LlmParsed.Ok -> "${endpoint.label} 连通正常"
                is com.wsy.ci.llm.LlmParsed.Err -> "${endpoint.label}：${result.message}"
            }
            testing.value = false
        }
    }

    fun dismissMessage() {
        message.value = null
    }

    /** 数据备份导入后，从磁盘重读路由和 Key 配置，让设置页无需重启即可同步。 */
    fun reloadFromStorage() {
        keyConfigured.value = loadKeyStates()
        routes.value = loadRoutes()
    }
}
