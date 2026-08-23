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

package com.wsy.ci

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.content.ContextCompat
import com.wsy.ci.core.designsystem.CiShapes
import com.wsy.ci.core.designsystem.CiFunctionIcon
import com.wsy.ci.core.designsystem.CiSizes
import com.wsy.ci.core.designsystem.CiSpacing
import com.wsy.ci.core.designsystem.CiTheme
import com.wsy.ci.core.designsystem.CiCompactWindowBreakpoint
import com.wsy.ci.core.designsystem.CiWindowSize
import com.wsy.ci.core.designsystem.LocalCiWindowSize
import com.wsy.ci.feature.calendar.CalendarScreen
import com.wsy.ci.feature.quest.QuestScreen
import com.wsy.ci.feature.settings.SettingsScreen
import com.wsy.ci.feature.settings.LocalModelInstallState
import com.wsy.ci.feature.settings.LocalModelController
import com.wsy.ci.feature.settings.LocalModelServiceState
import com.wsy.ci.feature.settings.avatarDrawable
import com.wsy.ci.feature.settings.label
import com.wsy.ci.feature.settings.DataBackupController
import com.wsy.ci.feature.shop.ShopScreen
import com.wsy.ci.feature.stats.StatsScreen
import com.wsy.ci.feature.today.TodayScreen
import com.wsy.ci.feature.voice.VoiceOverlayHost
import com.wsy.ci.feature.voice.VoiceWakeAvatar
import com.wsy.ci.feature.voice.VoiceViewModel
import com.wsy.ci.feature.voice.VoiceUiState
import com.wsy.ci.feature.voice.avatarLabel
import com.wsy.ci.localmodel.download.LocalModelDownloadManager
import com.wsy.ci.voice.VoiceWakeService
import com.wsy.ci.voice.VoiceWakeState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withTimeoutOrNull

internal enum class Destination(
    val label: String,
    @param:DrawableRes val icon: Int,
) {
    TODAY("今日", R.drawable.ic_ci_today),
    CALENDAR("日程", R.drawable.ic_ci_schedule),
    QUEST("任务", R.drawable.ic_ci_quest),
    SHOP("商城", R.drawable.ic_ci_shop),
    STATS("复盘", R.drawable.ic_ci_stats),
    SETTINGS("设置", R.drawable.ic_ci_settings),
}

class MainActivity : ComponentActivity() {
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* 是否授权由用户决定；拒绝时计时仍可在应用内继续。 */ }

    /** 首次长按 AI 图标才申请，不在 onCreate 里主动要；拒绝时下次长按会再检查一次。 */
    private val recordAudioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted && (application as CiApp).container.appSettings.wakeWordEnabled.value) {
            VoiceWakeService.start(this)
        }
    }

    private val localModelController: LocalModelController
        get() = (application as CiApp).container.localModelController
    private val dataBackupController: DataBackupController
        get() = (application as CiApp).container.dataBackupController
    private val asrDownloads: LocalModelDownloadManager
        get() = (application as CiApp).container.asrDownloads
    private val kwsDownloads: LocalModelDownloadManager
        get() = (application as CiApp).container.kwsDownloads

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        val appSettings = (application as CiApp).container.appSettings
        setContent {
            val themeMode by appSettings.themeMode.collectAsStateWithLifecycle()
            val motionMode by appSettings.motionMode.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()
            val darkTheme = themeMode.isDark(systemDark)
            val systemReducedMotion = Settings.Global.getFloat(
                contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ) == 0f
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }
            CiTheme(
                darkTheme = darkTheme,
                reducedMotion = motionMode.reduceMotion(systemReducedMotion),
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    CiRoot(
                        localModelController = localModelController,
                        dataBackupController = dataBackupController,
                        asrDownloads = asrDownloads,
                        kwsDownloads = kwsDownloads,
                        onRequestRecordAudioPermission = {
                            recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        },
                    )
                }
            }
        }
    }

    override fun onStop() {
        localModelController.stopService()
        super.onStop()
    }
}

@Composable
private fun CiRoot(
    localModelController: LocalModelController,
    dataBackupController: DataBackupController,
    asrDownloads: LocalModelDownloadManager,
    kwsDownloads: LocalModelDownloadManager,
    onRequestRecordAudioPermission: () -> Unit,
) {
    var destination by rememberSaveable { mutableStateOf(Destination.TODAY) }
    val stateHolder = rememberSaveableStateHolder()
    val localModelState by localModelController.state.collectAsStateWithLifecycle()
    var confirmCancelInference by rememberSaveable { mutableStateOf(false) }
    val voiceViewModel: VoiceViewModel = viewModel()
    val voiceState by voiceViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val app = context.applicationContext as CiApp
    val wakeEnabled by app.container.appSettings.wakeWordEnabled.collectAsStateWithLifecycle()
    val wakePhrase by app.container.appSettings.wakePhrase.collectAsStateWithLifecycle()
    val wakeCanListen = voiceState == VoiceUiState.Idle ||
        voiceState is VoiceUiState.Confirm || voiceState is VoiceUiState.Disambiguate
    LaunchedEffect(wakeEnabled, wakePhrase, wakeCanListen) {
        val permissionGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (wakeEnabled && permissionGranted && wakeCanListen) {
            // 服务内会串行停止旧 stream 后再用新短语启动，这里不做 stop/start 抢跑。
            VoiceWakeService.start(context)
        } else {
            VoiceWakeService.pause(context)
        }
    }
    LaunchedEffect(voiceViewModel) {
        voiceViewModel.navigationEvents.collect { destination = it }
    }
    val onAiClick = {
        when (localModelState.serviceState) {
            LocalModelServiceState.OFF -> {
                if (localModelState.installState == LocalModelInstallState.INSTALLED) {
                    localModelController.startService()
                } else {
                    destination = Destination.SETTINGS
                }
            }
            LocalModelServiceState.STARTING -> localModelController.stopService()
            LocalModelServiceState.ON -> localModelController.stopService()
            LocalModelServiceState.INFERENCING -> confirmCancelInference = true
        }
    }
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            // 根统一消费安全区域；子页面的 Scaffold 只会收到已经消费后的 Insets。
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        val windowSize = if (
            maxWidth < CiCompactWindowBreakpoint || maxHeight < CiCompactWindowBreakpoint
        ) {
            CiWindowSize.COMPACT
        } else {
            CiWindowSize.EXPANDED
        }
        CompositionLocalProvider(LocalCiWindowSize provides windowSize) {
            if (windowSize == CiWindowSize.COMPACT) {
                Box(modifier = Modifier.fillMaxSize()) {
                    CiScreenHost(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = CiSizes.bottomNavigationHeight),
                        destination = destination,
                        stateHolder = stateHolder,
                        localModelController = localModelController,
                        dataBackupController = dataBackupController,
                        asrDownloads = asrDownloads,
                        kwsDownloads = kwsDownloads,
                        app = app,
                        onRequestRecordAudioPermission = onRequestRecordAudioPermission,
                        onNavigateToToday = { destination = Destination.TODAY },
                    )
                    CiNavigationBar(
                        selected = destination,
                        localModelState = localModelState,
                        wakeStateFlow = app.container.voiceWakeRuntime.state,
                        onSelect = { destination = it },
                        onAiClick = onAiClick,
                        onVoiceStart = voiceViewModel::onVoiceStart,
                        onVoiceDragCancelChanged = voiceViewModel::onVoiceDragCancelChanged,
                        onVoiceEnd = voiceViewModel::onVoiceEnd,
                        onVoiceCancel = voiceViewModel::onVoiceCancel,
                        onRequestRecordAudioPermission = onRequestRecordAudioPermission,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            } else {
                Row(modifier = Modifier.fillMaxSize()) {
                    CiNavigationRail(
                        selected = destination,
                        localModelState = localModelState,
                        wakeStateFlow = app.container.voiceWakeRuntime.state,
                        onSelect = { destination = it },
                        onAiClick = onAiClick,
                        onVoiceStart = voiceViewModel::onVoiceStart,
                        onVoiceDragCancelChanged = voiceViewModel::onVoiceDragCancelChanged,
                        onVoiceEnd = voiceViewModel::onVoiceEnd,
                        onVoiceCancel = voiceViewModel::onVoiceCancel,
                        onRequestRecordAudioPermission = onRequestRecordAudioPermission,
                    )
                    CiScreenHost(
                        modifier = Modifier.weight(1f),
                        destination = destination,
                        stateHolder = stateHolder,
                        localModelController = localModelController,
                        dataBackupController = dataBackupController,
                        asrDownloads = asrDownloads,
                        kwsDownloads = kwsDownloads,
                        app = app,
                        onRequestRecordAudioPermission = onRequestRecordAudioPermission,
                        onNavigateToToday = { destination = Destination.TODAY },
                    )
                }
            }
        }
    }
    if (confirmCancelInference) {
        AlertDialog(
            onDismissRequest = { confirmCancelInference = false },
            title = { Text("取消本地推理？") },
            text = { Text("确认后会停止本次推理并关闭本地服务。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmCancelInference = false
                        localModelController.cancelInferenceAndStop()
                    },
                ) { Text("确认取消并关闭") }
            },
            dismissButton = {
                TextButton(onClick = { confirmCancelInference = false }) { Text("继续推理") }
            },
        )
    }
    VoiceOverlayHost(state = voiceState, viewModel = voiceViewModel)
}

/** 当前业务页面宿主：桌面与手机共用，确保切换窗口尺寸时保存各页面状态。 */
@Composable
private fun CiScreenHost(
    modifier: Modifier,
    destination: Destination,
    stateHolder: SaveableStateHolder,
    localModelController: LocalModelController,
    dataBackupController: DataBackupController,
    asrDownloads: LocalModelDownloadManager,
    kwsDownloads: LocalModelDownloadManager,
    app: CiApp,
    onRequestRecordAudioPermission: () -> Unit,
    onNavigateToToday: () -> Unit,
) {
    val context = LocalContext.current
    Box(modifier = modifier) {
        stateHolder.SaveableStateProvider(destination) {
            when (destination) {
                Destination.TODAY -> TodayScreen()
                Destination.CALENDAR -> CalendarScreen()
                // 从任务线里开始专注后直接切到今日屏，那里才有计时卡
                Destination.QUEST -> QuestScreen(
                    onNavigateToToday = onNavigateToToday,
                )
                Destination.SHOP -> ShopScreen()
                Destination.STATS -> StatsScreen()
                Destination.SETTINGS -> SettingsScreen(
                    localModelController = localModelController,
                    backupController = dataBackupController,
                    asrDownloads = asrDownloads,
                    kwsDownloads = kwsDownloads,
                    onStartWakeListening = {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
                            PackageManager.PERMISSION_GRANTED
                        ) {
                            onRequestRecordAudioPermission()
                        }
                    },
                    onStopWakeListening = {},
                    onClearCorrectionRecords = app.container.voiceCorrectionStore::clear,
                )
            }
        }
    }
}

/**
 * 左侧导航：104dp 宽、扁平、右缘账页分割线，选中项使用干燥花金浅容器。
 * 6 项整组垂直居中，底部的本地 AI 伙伴与一级业务导航分离。
 * 顶部对齐会在下方留一大片空白。
 */
@Composable
internal fun CiNavigationRail(
    selected: Destination,
    localModelState: com.wsy.ci.feature.settings.LocalModelUiState,
    wakeStateFlow: StateFlow<VoiceWakeState>,
    onSelect: (Destination) -> Unit,
    onAiClick: () -> Unit,
    onVoiceStart: () -> Unit,
    onVoiceDragCancelChanged: (Boolean) -> Unit,
    onVoiceEnd: () -> Unit,
    onVoiceCancel: () -> Unit,
    onRequestRecordAudioPermission: () -> Unit,
) {
    val edgeColor = MaterialTheme.colorScheme.outlineVariant
    Column(
        modifier = Modifier
            .width(CiSizes.navRailWidth)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.background)
            .rightEdge(edgeColor)
            .padding(vertical = CiSpacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        Box(
            modifier = Modifier.width(CiSizes.fab).height(CiSizes.headerRowHeight),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "CI",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
        HorizontalDivider(
            modifier = Modifier.width(CiSizes.fab),
            color = MaterialTheme.colorScheme.outlineVariant,
        )
        Box(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(CiSpacing.sm),
            ) {
                Destination.entries.forEach { destination ->
                    NavRailItem(
                        destination = destination,
                        isSelected = destination == selected,
                        onClick = { onSelect(destination) },
                    )
                }
            }
        }
        AiQuickEntry(
            state = localModelState,
            wakeStateFlow = wakeStateFlow,
            onClick = onAiClick,
            onVoiceStart = onVoiceStart,
            onVoiceDragCancelChanged = onVoiceDragCancelChanged,
            onVoiceEnd = onVoiceEnd,
            onVoiceCancel = onVoiceCancel,
            onRequestRecordAudioPermission = onRequestRecordAudioPermission,
        )
    }
}

/** 手机底部一级导航：六个业务入口与 AI 伙伴等宽分布，避免覆盖业务内容。 */
@Composable
private fun CiNavigationBar(
    selected: Destination,
    localModelState: com.wsy.ci.feature.settings.LocalModelUiState,
    wakeStateFlow: StateFlow<VoiceWakeState>,
    onSelect: (Destination) -> Unit,
    onAiClick: () -> Unit,
    onVoiceStart: () -> Unit,
    onVoiceDragCancelChanged: (Boolean) -> Unit,
    onVoiceEnd: () -> Unit,
    onVoiceCancel: () -> Unit,
    onRequestRecordAudioPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val edgeColor = MaterialTheme.colorScheme.outlineVariant
    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(CiSizes.bottomNavigationHeight)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .drawBehind {
                drawLine(
                    color = edgeColor,
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = CiSizes.border.toPx(),
                )
            },
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            Destination.entries.take(3).forEach { destination ->
                NavBarItem(
                    destination = destination,
                    isSelected = destination == selected,
                    onClick = { onSelect(destination) },
                    modifier = Modifier.weight(1f),
                )
            }
            AiQuickEntry(
                modifier = Modifier.weight(1f),
                state = localModelState,
                wakeStateFlow = wakeStateFlow,
                onClick = onAiClick,
                onVoiceStart = onVoiceStart,
                onVoiceDragCancelChanged = onVoiceDragCancelChanged,
                onVoiceEnd = onVoiceEnd,
                onVoiceCancel = onVoiceCancel,
                onRequestRecordAudioPermission = onRequestRecordAudioPermission,
                compactNavigation = true,
            )
            Destination.entries.drop(3).forEach { destination ->
                NavBarItem(
                    destination = destination,
                    isSelected = destination == selected,
                    onClick = { onSelect(destination) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun NavBarItem(
    destination: Destination,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }
    val content = if (isSelected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(
        modifier = modifier
            .fillMaxHeight()
            .selectable(selected = isSelected, role = Role.Tab, onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = destination.label
            }
            .padding(horizontal = CiSpacing.xxs, vertical = CiSpacing.xxs)
            .clip(CiShapes.field)
            .background(container),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CiFunctionIcon(
            resourceId = destination.icon,
            contentDescription = null,
            modifier = Modifier.size(CiSizes.navRailIcon),
        )
        Text(
            text = destination.label,
            style = MaterialTheme.typography.labelSmall,
            color = content,
            modifier = Modifier.padding(top = CiSpacing.xxs),
        )
    }
}

/** 长按上滑超过这个距离视为取消录音。 */
private val VOICE_CANCEL_THRESHOLD = 64.dp

@Composable
private fun AiQuickEntry(
    modifier: Modifier = Modifier,
    state: com.wsy.ci.feature.settings.LocalModelUiState,
    wakeStateFlow: StateFlow<VoiceWakeState>,
    onClick: () -> Unit,
    onVoiceStart: () -> Unit,
    onVoiceDragCancelChanged: (Boolean) -> Unit,
    onVoiceEnd: () -> Unit,
    onVoiceCancel: () -> Unit,
    onRequestRecordAudioPermission: () -> Unit,
    compactNavigation: Boolean = false,
) {
    val wakeState by wakeStateFlow.collectAsStateWithLifecycle()
    val active = state.serviceState != LocalModelServiceState.OFF
    val wakeEngaged = wakeState is VoiceWakeState.WakeDetected ||
        wakeState is VoiceWakeState.Capturing || wakeState is VoiceWakeState.Recognizing
    val wakeFailed = wakeState is VoiceWakeState.Failed
    val content = when {
        wakeFailed -> MaterialTheme.colorScheme.onErrorContainer
        active || wakeEngaged -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val companionState = state.companionState
    val statusLabel = wakeState.avatarLabel(companionState.label())
    val context = LocalContext.current
    Column(
        modifier = modifier
            .then(
                if (compactNavigation) {
                    Modifier.fillMaxHeight()
                } else {
                    Modifier.size(CiSizes.navRailItem)
                },
            )
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = "本地 AI，$statusLabel；长按开始语音，上滑取消"
                onClick("打开本地 AI") {
                    onClick()
                    true
                }
                customActions = listOf(
                    CustomAccessibilityAction("开始语音录入") {
                        val hasPermission = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO,
                        ) == PackageManager.PERMISSION_GRANTED
                        if (hasPermission) onVoiceStart() else onRequestRecordAudioPermission()
                        true
                    },
                )
            }
            .pointerInput(Unit) {
                val cancelThresholdPx = VOICE_CANCEL_THRESHOLD.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val longPressUp = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                        waitForUpOrCancellation()
                    }
                    if (longPressUp != null) {
                        onClick()
                        return@awaitEachGesture
                    }
                    val hasPermission = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.RECORD_AUDIO,
                    ) == PackageManager.PERMISSION_GRANTED
                    if (!hasPermission) {
                        onRequestRecordAudioPermission()
                        // 权限弹窗弹出期间手指多半已经离开，吞掉剩余事件，这次长按不触发录音。
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.changes.all { !it.pressed }) break
                        }
                        return@awaitEachGesture
                    }
                    onVoiceStart()
                    var cancelling = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        val dy = change.position.y - down.position.y
                        val nowCancelling = -dy > cancelThresholdPx
                        if (nowCancelling != cancelling) {
                            cancelling = nowCancelling
                            onVoiceDragCancelChanged(cancelling)
                        }
                        if (!change.pressed) {
                            change.consume()
                            break
                        }
                        change.consume()
                    }
                    if (cancelling) onVoiceCancel() else onVoiceEnd()
                }
            }
            .then(
                if (compactNavigation) {
                    Modifier.padding(horizontal = CiSpacing.xxs, vertical = CiSpacing.xxs)
                } else {
                    Modifier
                },
            )
            .clip(CiShapes.field)
            .background(
                when {
                    wakeFailed -> MaterialTheme.colorScheme.errorContainer
                    active || wakeEngaged -> MaterialTheme.colorScheme.primaryContainer
                    else -> MaterialTheme.colorScheme.background.copy(alpha = 0f)
                },
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        VoiceWakeAvatar(
            state = wakeState,
            avatarRes = companionState.avatarDrawable(),
            modifier = Modifier.size(
                if (compactNavigation) CiSizes.navRailIcon else CiSizes.fab,
            ),
        )
        Text(
            text = statusLabel,
            style = MaterialTheme.typography.labelSmall,
            color = content,
        )
    }
}

@Composable
private fun NavRailItem(destination: Destination, isSelected: Boolean, onClick: () -> Unit) {
    val container = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.background.copy(alpha = 0f)
    }
    val content = if (isSelected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(
        modifier = Modifier
            .size(CiSizes.navRailItem)
            .clip(CiShapes.field)
            .background(container)
            .selectable(selected = isSelected, role = Role.Tab, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CiFunctionIcon(
            resourceId = destination.icon,
            contentDescription = null,
            modifier = Modifier.size(CiSizes.navRailIcon),
        )
        Text(
            text = destination.label,
            style = MaterialTheme.typography.labelSmall,
            color = content,
            modifier = Modifier.padding(top = CiSpacing.xxs),
        )
    }
}

/** 导航栏右缘的账页分割线。 */
private fun Modifier.rightEdge(color: Color) = drawBehind {
    drawLine(
        color = color,
        start = Offset(size.width, 0f),
        end = Offset(size.width, size.height),
        strokeWidth = CiSizes.border.toPx(),
    )
}
