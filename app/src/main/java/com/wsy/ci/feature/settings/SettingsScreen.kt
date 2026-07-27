@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.wsy.ci.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wsy.ci.llm.LlmEndpoints
import com.wsy.ci.llm.LlmSettings
import com.wsy.ci.llm.LlmTaskType

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = viewModel()) {
    val keyConfigured by viewModel.keyConfigured.collectAsState()
    val routes by viewModel.routes.collectAsState()
    val message by viewModel.message.collectAsState()
    val testing by viewModel.testing.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.dismissMessage()
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("设置", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(vertical = 8.dp))

            Text("API Key（仅存本机 Keystore 加密存储）", style = MaterialTheme.typography.titleMedium)
            KeyCard(
                title = "DeepSeek（v4-pro 复杂推理 / v4-flash 轻量任务共用）",
                configured = keyConfigured[LlmEndpoints.KEY_DEEPSEEK] == true,
                onSave = { viewModel.saveKey(LlmEndpoints.KEY_DEEPSEEK, it) },
                onTest = { viewModel.testEndpoint(LlmEndpoints.DEEPSEEK_FLASH.id) },
                testing = testing,
            )
            KeyCard(
                title = "MiMo 小米（v2.5 视觉理解）",
                configured = keyConfigured[LlmEndpoints.KEY_MIMO] == true,
                onSave = { viewModel.saveKey(LlmEndpoints.KEY_MIMO, it) },
                onTest = { viewModel.testEndpoint(LlmEndpoints.MIMO.id) },
                testing = testing,
            )

            Text("模型路由表", style = MaterialTheme.typography.titleMedium)
            Text(
                "默认分工：复杂推理 → DeepSeek V4 Pro；轻量任务 → DeepSeek V4 Flash；视觉 → MiMo V2.5。可按任务覆盖或关闭（关闭后走离线兜底）。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
            Card {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LlmTaskType.entries.forEach { task ->
                        RouteRow(
                            task = task,
                            current = routes[task],
                            onSelect = { viewModel.setRoute(task, it) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun KeyCard(
    title: String,
    configured: Boolean,
    onSave: (String) -> Unit,
    onTest: () -> Unit,
    testing: Boolean,
) {
    var input by remember { mutableStateOf("") }
    Card {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text(
                    if (configured) "✅ 已配置" else "未配置",
                    color = if (configured) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text(if (configured) "粘贴新 Key 可覆盖" else "粘贴 API Key") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { onSave(input); input = "" }, enabled = input.isNotBlank()) { Text("保存") }
                TextButton(onClick = onTest, enabled = configured && !testing) {
                    Text(if (testing) "测试中…" else "测试")
                }
            }
        }
    }
}

@Composable
private fun RouteRow(
    task: LlmTaskType,
    current: String?,
    onSelect: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val currentLabel = when (current) {
        null -> "默认（${LlmEndpoints.defaultFor(task.tier).label}）"
        LlmSettings.ROUTE_OFF -> "关闭（离线兜底）"
        else -> LlmEndpoints.byId(current)?.label ?: current
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(task.label, style = MaterialTheme.typography.bodyLarge)
            Text(task.tier.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        }
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = currentLabel,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).width(280.dp),
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    text = { Text("默认（${LlmEndpoints.defaultFor(task.tier).label}）") },
                    onClick = { onSelect(null); expanded = false },
                )
                LlmEndpoints.ALL.forEach { endpoint ->
                    DropdownMenuItem(
                        text = { Text(endpoint.label) },
                        onClick = { onSelect(endpoint.id); expanded = false },
                    )
                }
                DropdownMenuItem(
                    text = { Text("关闭（离线兜底）") },
                    onClick = { onSelect(LlmSettings.ROUTE_OFF); expanded = false },
                )
            }
        }
    }
}
