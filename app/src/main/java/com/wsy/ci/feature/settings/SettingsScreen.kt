package com.wsy.ci.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wsy.ci.core.designsystem.CiChip
import com.wsy.ci.core.designsystem.CiDropdownField
import com.wsy.ci.core.designsystem.CiPanelCard
import com.wsy.ci.core.designsystem.CiScreenHeader
import com.wsy.ci.core.designsystem.CiShapes
import com.wsy.ci.core.designsystem.CiSizes
import com.wsy.ci.core.designsystem.CiSpacing
import com.wsy.ci.core.designsystem.CiTextField
import com.wsy.ci.llm.LlmEndpoints
import com.wsy.ci.llm.LlmSettings
import com.wsy.ci.llm.LlmTaskType

/** API Key 卡高度，见逐屏布局规格第 6 节。 */
private val KEY_CARD_HEIGHT: Dp = 180.dp

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

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(CiSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(CiSpacing.md),
        ) {
            CiScreenHeader(title = "设置", subtitle = "API Key 仅存本机 Keystore 加密存储")

            Row(horizontalArrangement = Arrangement.spacedBy(CiSpacing.md)) {
                KeyCard(
                    name = "DeepSeek",
                    detail = "V4 Pro 复杂推理 / V4 Flash 轻量任务共用",
                    configured = keyConfigured[LlmEndpoints.KEY_DEEPSEEK] == true,
                    testing = testing,
                    onSave = { viewModel.saveKey(LlmEndpoints.KEY_DEEPSEEK, it) },
                    onTest = { viewModel.testEndpoint(LlmEndpoints.DEEPSEEK_FLASH.id) },
                    modifier = Modifier.weight(1f),
                )
                KeyCard(
                    name = "MiMo 小米",
                    detail = "V2.5 视觉理解",
                    configured = keyConfigured[LlmEndpoints.KEY_MIMO] == true,
                    testing = testing,
                    onSave = { viewModel.saveKey(LlmEndpoints.KEY_MIMO, it) },
                    onTest = { viewModel.testEndpoint(LlmEndpoints.MIMO.id) },
                    modifier = Modifier.weight(1f),
                )
            }

            RouteTableCard(
                routes = routes,
                onSelect = viewModel::setRoute,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** API Key 卡：平台名 + 状态 chip + 密码框（含显隐）+ 保存/测试。 */
@Composable
private fun KeyCard(
    name: String,
    detail: String,
    configured: Boolean,
    testing: Boolean,
    onSave: (String) -> Unit,
    onTest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var input by remember { mutableStateOf("") }
    var revealed by remember { mutableStateOf(false) }

    CiPanelCard(
        modifier = modifier.height(KEY_CARD_HEIGHT),
        contentPadding = 20.dp,
        verticalSpacing = 10.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(text = name, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            CiChip(
                text = if (configured) "已配置" else "未配置",
                container = if (configured) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                },
                content = if (configured) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }

        CiTextField(
            value = input,
            onValueChange = { input = it },
            placeholder = if (configured) "粘贴新 Key 可覆盖" else "粘贴 API Key",
            height = 44.dp,
            visualTransformation = if (revealed) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            trailing = {
                Text(
                    text = if (revealed) "🙈" else "👁",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .clip(CiShapes.pill)
                        .clickable { revealed = !revealed }
                        .padding(CiSpacing.xxs),
                )
            },
            modifier = Modifier.fillMaxWidth(),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = { onSave(input); input = ""; revealed = false },
                enabled = input.isNotBlank(),
                shape = CiShapes.pill,
            ) {
                Text("保存", style = MaterialTheme.typography.labelMedium)
            }
            OutlinedButton(
                onClick = onTest,
                enabled = configured && !testing,
                shape = CiShapes.pill,
            ) {
                Text(
                    text = if (testing) "测试中…" else "测试",
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

/** 模型路由表：整行卡，每任务一行 56dp + 240dp 下拉选择器。 */
@Composable
private fun RouteTableCard(
    routes: Map<LlmTaskType, String?>,
    onSelect: (LlmTaskType, String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    CiPanelCard(modifier = modifier, contentPadding = 0.dp, verticalSpacing = 0.dp) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(CiSpacing.xxs),
        ) {
            Text("模型路由表", style = MaterialTheme.typography.titleSmall)
            Text(
                text = "默认分工：复杂推理 → DeepSeek V4 Pro；轻量任务 → DeepSeek V4 Flash；" +
                    "视觉 → MiMo V2.5。可按任务覆盖或关闭（关闭后走离线兜底）。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            LlmTaskType.entries.forEach { task ->
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                RouteRow(
                    task = task,
                    current = routes[task],
                    onSelect = { onSelect(task, it) },
                )
            }
        }
    }
}

@Composable
private fun RouteRow(task: LlmTaskType, current: String?, onSelect: (String?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val defaultLabel = "默认（${LlmEndpoints.defaultFor(task.tier).label}）"
    val currentLabel = when (current) {
        null -> defaultLabel
        LlmSettings.ROUTE_OFF -> "关闭（离线兜底）"
        else -> LlmEndpoints.byId(current)?.label ?: current
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(CiSizes.ledgerRowHeight)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(task.label, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = task.tier.label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box {
            CiDropdownField(value = currentLabel, onClick = { expanded = true })
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    text = { Text(defaultLabel) },
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
