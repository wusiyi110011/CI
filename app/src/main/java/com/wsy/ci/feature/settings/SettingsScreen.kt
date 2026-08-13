package com.wsy.ci.feature.settings

import android.net.ConnectivityManager
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wsy.ci.core.designsystem.CiChip
import com.wsy.ci.core.designsystem.CiDropdownField
import com.wsy.ci.core.designsystem.CiPanelCard
import com.wsy.ci.core.designsystem.CiProgressBar
import com.wsy.ci.core.designsystem.CiScreenHeader
import com.wsy.ci.core.designsystem.CiShapes
import com.wsy.ci.core.designsystem.CiSizes
import com.wsy.ci.core.designsystem.CiSpacing
import com.wsy.ci.core.designsystem.CiTextField
import com.wsy.ci.core.settings.ThemeMode
import com.wsy.ci.llm.LlmEndpoints
import com.wsy.ci.llm.LlmSettings
import com.wsy.ci.llm.LlmTaskType

/** API Key 卡高度，见逐屏布局规格第 6 节。 */
private val KEY_CARD_HEIGHT: Dp = 180.dp

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = viewModel(),
    localModelController: LocalModelController? = null,
    backupController: DataBackupController? = null,
) {
    val localController = localModelController ?: remember { InMemoryLocalModelController() }
    val dataBackupController = backupController ?: remember { InMemoryBackupController() }
    val localModel by localController.state.collectAsState()
    val backupState by dataBackupController.state.collectAsState()
    val backupBusy = backupState.backingUp ||
        backupState.importingId != null || backupState.deletingId != null
    val keyConfigured by viewModel.keyConfigured.collectAsState()
    val routes by viewModel.routes.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val message by viewModel.message.collectAsState()
    val testing by viewModel.testing.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    var confirmMetered by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showBackups by remember { mutableStateOf(false) }
    var confirmRestoreId by remember { mutableStateOf<String?>(null) }
    var confirmDeleteBackupId by remember { mutableStateOf<String?>(null) }
    val requestDownload = {
        if (context.getSystemService(ConnectivityManager::class.java).isActiveNetworkMetered) {
            confirmMetered = true
        } else {
            localController.download(false)
        }
    }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.dismissMessage()
        }
    }
    LaunchedEffect(backupState.message) {
        if (backupState.message?.startsWith("已导入") == true) viewModel.reloadFromStorage()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(CiSpacing.lg)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(CiSpacing.md),
        ) {
            CiScreenHeader(title = "设置", subtitle = "API Key 仅存本机 Keystore 加密存储")

            AppearanceCard(current = themeMode, onSelect = viewModel::setThemeMode)

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

            LocalModelAndBackupCard(
                state = localModel,
                backupState = backupState,
                onDownload = requestDownload,
                onPause = localController::pauseDownload,
                onResume = requestDownload,
                onCancelDownload = localController::cancelDownload,
                onDelete = { confirmDelete = true },
                onStart = localController::startService,
                onStop = localController::stopService,
                onTest = localController::testInference,
                onTestVision = localController::testVision,
                onCancelInference = localController::cancelInferenceAndStop,
                onBackup = dataBackupController::createBackup,
                onOpenImport = {
                    if (!backupBusy) {
                        dataBackupController.refresh()
                        showBackups = true
                    }
                },
            )

            RouteTableCard(
                routes = routes,
                localInstalled = localModel.installState == LocalModelInstallState.INSTALLED,
                onSelect = viewModel::setRoute,
                modifier = Modifier.fillMaxWidth(),
            )

        }
    }
    if (confirmMetered) {
        AlertDialog(
            onDismissRequest = { confirmMetered = false },
            title = { Text("使用计费网络下载？") },
            text = { Text("模型约 1.39 GB。本次确认只对当前下载任务生效。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmMetered = false
                    localController.download(true)
                }) { Text("继续下载") }
            },
            dismissButton = { TextButton(onClick = { confirmMetered = false }) { Text("取消") } },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除本地模型？") },
            text = { Text("会先停止下载和推理，再删除约 1.39 GB 的模型文件。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    localController.deleteModel()
                }) { Text("确认删除") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("保留") } },
        )
    }
    if (showBackups) {
        BackupListDialog(
            state = backupState,
            onDismiss = { showBackups = false },
            onRestore = { if (!backupBusy) confirmRestoreId = it },
            onDelete = { if (!backupBusy) confirmDeleteBackupId = it },
        )
    }
    confirmRestoreId?.let { id ->
        val item = backupState.entries.firstOrNull { it.id == id }
        AlertDialog(
            onDismissRequest = { confirmRestoreId = null },
            title = { Text("确认导入备份？") },
            text = {
                Text(
                    item?.let { "将导入 ${formatBackupTime(it.createdAtMillis)} 的备份，当前本机数据会被覆盖。" }
                        ?: "备份不存在或已被删除。",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmRestoreId = null
                        showBackups = false
                        dataBackupController.restoreBackup(id)
                    },
                    enabled = item != null,
                ) { Text("确认导入") }
            },
            dismissButton = { TextButton(onClick = { confirmRestoreId = null }) { Text("取消") } },
        )
    }
    confirmDeleteBackupId?.let { id ->
        val item = backupState.entries.firstOrNull { it.id == id }
        AlertDialog(
            onDismissRequest = { confirmDeleteBackupId = null },
            title = { Text("删除这份备份？") },
            text = { Text(item?.let { "${formatBackupTime(it.createdAtMillis)} · ${formatBackupSize(it.sizeBytes)}" } ?: "备份不存在或已被删除。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDeleteBackupId = null
                        dataBackupController.deleteBackup(id)
                    },
                    enabled = item != null,
                ) { Text("确认删除") }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteBackupId = null }) { Text("保留") } },
        )
    }
}

/**
 * 本地模型卡。该卡只依赖 [LocalModelController]，下载器和推理服务可在后续模块中替换。
 * 安装与服务两套状态分开呈现，避免「已下载但未启动」与「正在推理」混在一起。
 */
@Composable
private fun LocalModelAndBackupCard(
    state: LocalModelUiState,
    backupState: DataBackupUiState,
    onDownload: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancelDownload: () -> Unit,
    onDelete: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onTest: () -> Unit,
    onTestVision: () -> Unit,
    onCancelInference: () -> Unit,
    onBackup: () -> Unit,
    onOpenImport: () -> Unit,
) {
    CiPanelCard(modifier = Modifier.fillMaxWidth(), contentPadding = CiSpacing.lg) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(CiSpacing.xxs)) {
                Text(state.modelName, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${state.version} · ${state.source} · ${state.sizeLabel}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            StatusChip(
                label = installStateLabel(state.installState),
                active = state.installState == LocalModelInstallState.INSTALLED,
                error = state.installState == LocalModelInstallState.FAILED,
            )
        }

        state.errorMessage?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(CiSpacing.xs)) {
            when (state.installState) {
                LocalModelInstallState.NOT_INSTALLED -> Button(onClick = onDownload, shape = CiShapes.pill) {
                    Text("下载")
                }
                LocalModelInstallState.WAITING_NETWORK,
                LocalModelInstallState.QUEUED,
                LocalModelInstallState.DOWNLOADING -> {
                    Button(onClick = onPause, shape = CiShapes.pill) { Text("暂停") }
                    OutlinedButton(onClick = onCancelDownload, shape = CiShapes.pill) { Text("取消") }
                }
                LocalModelInstallState.PAUSED -> {
                    Button(onClick = onResume, shape = CiShapes.pill) { Text("继续") }
                    OutlinedButton(onClick = onCancelDownload, shape = CiShapes.pill) { Text("取消") }
                }
                LocalModelInstallState.FAILED -> {
                    Button(onClick = onDownload, shape = CiShapes.pill) { Text("重试") }
                    OutlinedButton(onClick = onDelete, shape = CiShapes.pill) { Text("删除") }
                }
                LocalModelInstallState.INSTALLED -> {
                    OutlinedButton(onClick = onDelete, shape = CiShapes.pill) { Text("删除模型") }
                }
                LocalModelInstallState.VERIFYING -> {
                    OutlinedButton(onClick = onCancelDownload, shape = CiShapes.pill) { Text("取消") }
                }
            }
            if (state.installState == LocalModelInstallState.WAITING_NETWORK ||
                state.installState == LocalModelInstallState.QUEUED ||
                state.installState == LocalModelInstallState.DOWNLOADING ||
                state.installState == LocalModelInstallState.VERIFYING ||
                state.installState == LocalModelInstallState.PAUSED
            ) {
                Text(
                    "${(state.downloadProgress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
            }
        }
        if (state.installState == LocalModelInstallState.WAITING_NETWORK ||
            state.installState == LocalModelInstallState.QUEUED ||
            state.installState == LocalModelInstallState.DOWNLOADING ||
            state.installState == LocalModelInstallState.VERIFYING ||
            state.installState == LocalModelInstallState.PAUSED
        ) {
            CiProgressBar(
                progress = state.downloadProgress,
                color = MaterialTheme.colorScheme.secondary,
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(CiSpacing.xxs)) {
                Text("本地推理服务", style = MaterialTheme.typography.titleSmall)
                Text(
                    serviceStateLabel(state.serviceState),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.serviceState == LocalModelServiceState.INFERENCING) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            StatusChip(
                label = serviceStateLabel(state.serviceState),
                active = state.serviceState == LocalModelServiceState.ON,
                error = false,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(CiSpacing.xs)) {
            when (state.serviceState) {
                LocalModelServiceState.OFF -> Button(
                    onClick = onStart,
                    enabled = state.installState == LocalModelInstallState.INSTALLED,
                    shape = CiShapes.pill,
                ) { Text("启动") }
                LocalModelServiceState.STARTING -> OutlinedButton(onClick = onStop, shape = CiShapes.pill) {
                    Text("取消启动")
                }
                LocalModelServiceState.ON -> {
                    Button(onClick = onTest, shape = CiShapes.pill) { Text("文字测试") }
                    Button(onClick = onTestVision, shape = CiShapes.pill) { Text("图片测试") }
                    OutlinedButton(onClick = onStop, shape = CiShapes.pill) { Text("关闭") }
                }
                LocalModelServiceState.INFERENCING -> {
                    Button(onClick = onCancelInference, shape = CiShapes.pill) { Text("确认取消并关闭") }
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        DataBackupSection(
            state = backupState,
            onBackup = onBackup,
            onOpenImport = onOpenImport,
        )
    }
}

@Composable
private fun StatusChip(label: String, active: Boolean, error: Boolean) {
    val container = when {
        error -> MaterialTheme.colorScheme.errorContainer
        active -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val content = when {
        error -> MaterialTheme.colorScheme.onErrorContainer
        active -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    CiChip(text = label, container = container, content = content)
}

private fun installStateLabel(state: LocalModelInstallState): String = when (state) {
    LocalModelInstallState.NOT_INSTALLED -> "未安装"
    LocalModelInstallState.WAITING_NETWORK -> "等待网络"
    LocalModelInstallState.QUEUED -> "排队"
    LocalModelInstallState.DOWNLOADING -> "下载中"
    LocalModelInstallState.PAUSED -> "已暂停"
    LocalModelInstallState.VERIFYING -> "校验中"
    LocalModelInstallState.FAILED -> "下载失败"
    LocalModelInstallState.INSTALLED -> "已安装"
}

private fun serviceStateLabel(state: LocalModelServiceState): String = when (state) {
    LocalModelServiceState.OFF -> "已关闭"
    LocalModelServiceState.STARTING -> "启动中"
    LocalModelServiceState.ON -> "已开启"
    LocalModelServiceState.INFERENCING -> "推理中"
}

@Composable
private fun DataBackupSection(
    state: DataBackupUiState,
    onBackup: () -> Unit,
    onOpenImport: () -> Unit,
) {
    val busy = state.backingUp || state.importingId != null || state.deletingId != null
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CiSpacing.md),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(CiSpacing.xxs)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CiSpacing.xs),
            ) {
                Text("数据备份", style = MaterialTheme.typography.titleSmall)
                if (state.entries.isNotEmpty()) {
                    CiChip(
                        text = "${state.entries.size} 份",
                        container = MaterialTheme.colorScheme.surfaceContainerHighest,
                        content = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = state.message ?: state.errorMessage
                    ?: "仅备份学习数据和普通设置，不包含模型与 API Key。",
                style = MaterialTheme.typography.bodySmall,
                color = if (state.errorMessage == null) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(CiSpacing.xs)) {
            OutlinedButton(onClick = onOpenImport, enabled = !busy, shape = CiShapes.pill) {
                Text("管理备份")
            }
            Button(onClick = onBackup, enabled = !busy, shape = CiShapes.pill) {
                Text(if (state.backingUp) "备份中…" else "一键备份")
            }
        }
    }
}

@Composable
private fun BackupListDialog(
    state: DataBackupUiState,
    onDismiss: () -> Unit,
    onRestore: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择备份导入") },
        text = {
            if (state.entries.isEmpty()) {
                Text("暂无备份。点击设置页中的「一键备份」创建。")
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(CiSizes.dialogScrollMaxHeight)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(CiSpacing.xs),
                ) {
                    state.entries.forEach { item ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(CiSpacing.xs),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(formatBackupTime(item.createdAtMillis), style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "${item.label} · ${formatBackupSize(item.sizeBytes)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(onClick = { onRestore(item.id) }) { Text("导入") }
                            TextButton(onClick = { onDelete(item.id) }) { Text("删除") }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

/** 外观卡：明暗三选一。跟随系统之外还能手动钉死，夜里看平板不必去改系统设置。 */
@Composable
private fun AppearanceCard(current: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    CiPanelCard(modifier = Modifier.fillMaxWidth(), contentPadding = 20.dp, verticalSpacing = 10.dp) {
        Column {
            Text("外观", style = MaterialTheme.typography.titleSmall)
            Text(
                text = "选「跟随系统」时随系统深色开关切换；桌面小组件始终跟随系统。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(CiSpacing.xs)) {
            ThemeMode.entries.forEach { mode ->
                val selected = mode == current
                CiChip(
                    text = mode.label,
                    container = if (selected) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    },
                    content = if (selected) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    borderColor = if (selected) MaterialTheme.colorScheme.onSurface else null,
                    style = MaterialTheme.typography.labelMedium,
                    horizontalPadding = CiSpacing.md,
                    verticalPadding = CiSpacing.xs,
                    modifier = Modifier.clickable { onSelect(mode) },
                )
            }
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
    localInstalled: Boolean,
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
        Column {
            LlmTaskType.entries.forEach { task ->
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                RouteRow(
                    task = task,
                    current = routes[task],
                    localInstalled = localInstalled,
                    onSelect = { onSelect(task, it) },
                )
            }
        }
    }
}

@Composable
private fun RouteRow(
    task: LlmTaskType,
    current: String?,
    localInstalled: Boolean,
    onSelect: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val defaultLabel = "默认（${LlmEndpoints.defaultFor(task.tier).label}）"
    val currentLabel = when (current) {
        null -> defaultLabel
        LlmSettings.ROUTE_OFF -> "关闭（离线兜底）"
        LOCAL_MODEL_ROUTE_ID -> if (localInstalled) "本地模型（离线）" else "本地模型（需先下载）"
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
                    text = { Text(if (localInstalled) "本地模型（离线）" else "本地模型（需先下载）") },
                    onClick = { onSelect(LOCAL_MODEL_ROUTE_ID); expanded = false },
                )
                DropdownMenuItem(
                    text = { Text("关闭（离线兜底）") },
                    onClick = { onSelect(LlmSettings.ROUTE_OFF); expanded = false },
                )
            }
        }
    }
}
