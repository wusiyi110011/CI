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

package com.wsy.ci.feature.voice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.DialogProperties
import com.wsy.ci.R
import com.wsy.ci.Destination
import com.wsy.ci.core.db.TaskEntity
import com.wsy.ci.core.designsystem.CiFormField
import com.wsy.ci.core.designsystem.CiFunctionIcon
import com.wsy.ci.core.designsystem.CiPanelCard
import com.wsy.ci.core.designsystem.CiProgressBar
import com.wsy.ci.core.designsystem.CiShapes
import com.wsy.ci.core.designsystem.CiSizes
import com.wsy.ci.core.designsystem.CiSpacing
import com.wsy.ci.core.designsystem.ciResponsiveDialogWidth
import com.wsy.ci.core.util.TimeFormat
import com.wsy.ci.core.voice.VoiceTarget
import com.wsy.ci.core.voice.label
import com.wsy.ci.core.voice.skill.SkillPreview
import com.wsy.ci.core.voice.skill.SkillRisk

/**
 * 长按图标录音期间的浮层：无实时文字（sherpa 整段解码没有 partial result），
 * 只用时长和音量条给出正在录音的反馈。上滑取消时整体转 error 色，
 * 同时保留结束与取消按钮，供 TalkBack、键盘及不便持续按住的用户完成操作。
 */
@Composable
fun VoiceRecordingDialog(
    elapsedMillis: Long,
    amplitude: Float,
    cancelling: Boolean,
    onFinish: () -> Unit,
    onCancel: () -> Unit,
) {
    val tint = if (cancelling) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        shape = CiShapes.dialog,
        icon = {
            CiFunctionIcon(
                resourceId = R.drawable.ic_ci_ai_schedule,
                contentDescription = null,
            )
        },
        title = { Text(if (cancelling) "松开手指，取消" else TimeFormat.elapsed(elapsedMillis)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(CiSpacing.sm)) {
                CiProgressBar(progress = amplitude.coerceIn(0f, 1f), color = tint)
                Text(
                    text = if (cancelling) "松开手指即可取消本次录音" else "松手结束，上滑取消",
                    style = MaterialTheme.typography.bodySmall,
                    color = tint,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onFinish) { Text("结束并识别") }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text("取消录音") }
        },
    )
}

/** 识别结果确认浮层：文字可编辑（识别不准时手改），通用技能预览卡片，绝不静默执行。 */
@Composable
fun VoiceConfirmDialog(
    text: String,
    onTextChange: (String) -> Unit,
    preview: SkillPreview?,
    risk: SkillRisk?,
    canReinterpret: Boolean,
    onReinterpret: () -> Unit,
    onExecute: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        shape = CiShapes.dialog,
        modifier = Modifier
            .ciResponsiveDialogWidth(CiSizes.dialogFormWidth),
        icon = {
            CiFunctionIcon(
                resourceId = R.drawable.ic_ci_ai_schedule,
                contentDescription = null,
            )
        },
        title = { Text("识别到语音指令") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(CiSpacing.sm)) {
                CiFormField(
                    value = text,
                    onValueChange = onTextChange,
                    label = "识别文本",
                    modifier = Modifier.fillMaxWidth(),
                )
                SkillPreviewCard(preview, risk)
            }
        },
        confirmButton = {
            Button(onClick = onExecute, shape = CiShapes.pill, enabled = preview != null) {
                Text("执行")
            }
        },
        dismissButton = {
            Row {
                if (canReinterpret) {
                    TextButton(onClick = onReinterpret) { Text("重新理解") }
                }
                TextButton(onClick = onCancel) { Text("取消") }
            }
        },
    )
}

/** 整段识别中：sherpa 没有 partial result，只能给一个不确定时长的进度指示。 */
@Composable
fun VoiceRecognizingDialog() {
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        shape = CiShapes.dialog,
        title = { Text("识别中…") },
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator()
                Text("正在转成文字", modifier = Modifier.padding(start = CiSpacing.md))
            }
        },
        confirmButton = {},
    )
}

/** 模型准备与指令执行都不可中途重复触发，用同一种阻塞式进度反馈。 */
@Composable
private fun VoiceProgressDialog(
    title: String,
    detail: String,
    onCancel: (() -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        shape = CiShapes.dialog,
        title = { Text(title) },
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator()
                Text(detail, modifier = Modifier.padding(start = CiSpacing.md))
            }
        },
        confirmButton = {},
        dismissButton = {
            if (onCancel != null) TextButton(onClick = onCancel) { Text("取消") }
        },
    )
}

/** 只在第一次手动长按时展示，把操作方式和数据安全边界说清楚。 */
@Composable
private fun VoiceFirstUseDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = CiShapes.dialog,
        icon = { CiFunctionIcon(resourceId = R.drawable.ic_ci_ai_schedule, contentDescription = null) },
        title = { Text("语音指令已就绪") },
        text = {
            Text("长按本地 AI 图标说话，松手识别，向上滑动后松手可取消。会改动数据的指令默认先显示预览，危险操作不会自动执行。")
        },
        confirmButton = { Button(onClick = onDismiss, shape = CiShapes.pill) { Text("知道了") } },
    )
}

/** 同名任务或近音目标的显式消歧。 */
@Composable
private fun VoiceDisambiguationDialog(
    text: String,
    candidates: List<VoiceTarget>,
    onChoose: (VoiceTarget) -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        shape = CiShapes.dialog,
        modifier = Modifier
            .ciResponsiveDialogWidth(CiSizes.dialogFormWidth),
        title = { Text("你指的是哪一个？") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(CiSpacing.sm)) {
                Text("识别文字：$text", style = MaterialTheme.typography.bodySmall)
                candidates.forEach { target ->
                    Button(
                        onClick = { onChoose(target) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = CiShapes.pill,
                    ) {
                        Text("【${target.kind.label}】${target.name}")
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onCancel) { Text("取消") } },
    )
}

/** 场景2「查一下哪天有什么安排」的结果浮层：按时间顺序列出任务，可跳去日历屏细看。 */
@Composable
fun VoiceScheduleResultDialog(tasks: List<TaskEntity>, onOpenCalendar: () -> Unit, onDismiss: () -> Unit) {
    val sorted = tasks.sortedWith(compareBy({ it.epochDay }, { it.startMinute }))
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        shape = CiShapes.dialog,
        modifier = Modifier
            .ciResponsiveDialogWidth(CiSizes.dialogFormWidth),
        icon = { CiFunctionIcon(resourceId = R.drawable.ic_ci_schedule, contentDescription = null) },
        title = { Text("查到 ${sorted.size} 条安排") },
        text = {
            if (sorted.isEmpty()) {
                Text(
                    text = "这段时间没有安排任务",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(
                    modifier = Modifier.heightIn(max = CiSizes.dialogScrollMaxHeight).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(CiSpacing.xxs),
                ) {
                    sorted.forEach { task ->
                        Text(
                            text = "${TimeFormat.date(task.epochDay)} " +
                                "${TimeFormat.minuteOfDay(task.startMinute)}–${TimeFormat.minuteOfDay(task.endMinute)} " +
                                "${task.title} ${task.status.label}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onOpenCalendar, shape = CiShapes.pill) { Text("去日历查看") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

/** 识别失败 / 权限缺失等错误提示。 */
@Composable
fun VoiceErrorDialog(message: String, onDismiss: () -> Unit, onOpenSettings: (() -> Unit)? = null) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = CiShapes.dialog,
        icon = { CiFunctionIcon(resourceId = R.drawable.ic_ci_warning, contentDescription = null) },
        title = { Text("没能完成") },
        text = { Text(message) },
        confirmButton = { Button(onClick = onDismiss, shape = CiShapes.pill) { Text("关闭") } },
        dismissButton = {
            if (onOpenSettings != null) {
                TextButton(onClick = onOpenSettings) { Text("去语音设置") }
            }
        },
    )
}

/** 按 [VoiceUiState] 在各浮层间切换，`Idle` 时不渲染任何内容。 */
@Composable
internal fun VoiceOverlayHost(state: VoiceUiState, viewModel: VoiceViewModel) {
    when (state) {
        VoiceUiState.Idle -> Unit
        VoiceUiState.FirstUse -> VoiceFirstUseDialog(onDismiss = viewModel::dismiss)
        VoiceUiState.Preparing -> VoiceProgressDialog(
            title = "正在准备语音",
            detail = "检查本地模型…",
            onCancel = viewModel::onVoiceCancel,
        )
        is VoiceUiState.Recording -> VoiceRecordingDialog(
            elapsedMillis = state.elapsedMillis,
            amplitude = state.amplitude,
            cancelling = state.cancelling,
            onFinish = viewModel::onVoiceEnd,
            onCancel = viewModel::onVoiceCancel,
        )
        VoiceUiState.Recognizing -> VoiceRecognizingDialog()
        is VoiceUiState.Disambiguate -> VoiceDisambiguationDialog(
            text = state.text,
            candidates = state.candidates,
            onChoose = viewModel::chooseCandidate,
            onCancel = viewModel::dismiss,
        )
        is VoiceUiState.Confirm -> VoiceConfirmDialog(
            text = state.text,
            onTextChange = viewModel::onTextEdited,
            preview = state.preview,
            risk = state.risk,
            canReinterpret = state.canReinterpret,
            onReinterpret = viewModel::reinterpret,
            onExecute = { state.invocation?.let(viewModel::execute) },
            onCancel = viewModel::dismiss,
        )
        VoiceUiState.Executing -> VoiceProgressDialog("正在执行", "请稍候…")
        is VoiceUiState.ScheduleResult -> VoiceScheduleResultDialog(
            tasks = state.tasks,
            onOpenCalendar = viewModel::openCalendar,
            onDismiss = viewModel::dismiss,
        )
        is VoiceUiState.SkillResult -> VoiceSkillResultDialog(
            message = state.message,
            navigateTo = state.navigateTo,
            title = state.title,
            onGo = { state.navigateTo?.let(viewModel::navigateTo) },
            onDismiss = viewModel::dismiss,
        )
        is VoiceUiState.Error -> VoiceErrorDialog(
            message = state.message,
            onDismiss = viewModel::dismiss,
            onOpenSettings = viewModel::openVoiceSettings.takeIf { state.canOpenSettings },
        )
    }
}

/** 技能执行成功的结果浮层：[navigateTo] 非空时给「去 XX」按钮。 */
@Composable
private fun VoiceSkillResultDialog(
    message: String,
    navigateTo: Destination?,
    title: String,
    onGo: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        shape = CiShapes.dialog,
        modifier = Modifier
            .ciResponsiveDialogWidth(CiSizes.dialogFormWidth),
        icon = { CiFunctionIcon(resourceId = R.drawable.ic_ci_ai_schedule, contentDescription = null) },
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            if (navigateTo != null) {
                Button(onClick = onGo, shape = CiShapes.pill) { Text("去${navigateTo.label}") }
            } else {
                Button(onClick = onDismiss, shape = CiShapes.pill) { Text("知道了") }
            }
        },
        dismissButton = {
            if (navigateTo != null) TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}

/**
 * 通用技能预览卡片：标题 + 明细行，`dangerous` 时标题转警示色。
 * 加新 skill 不再需要改这个文件，预览内容由 skill 自己描述。
 */
@Composable
private fun SkillPreviewCard(preview: SkillPreview?, risk: SkillRisk?) {
    val errorColor = MaterialTheme.colorScheme.error
    CiPanelCard {
        if (preview == null) {
            Text(
                text = "没听懂，可以直接改上面的文字再试",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            val dangerous = preview.dangerous || risk == SkillRisk.DANGEROUS
            Text(
                text = preview.title,
                style = MaterialTheme.typography.titleMedium,
                color = if (dangerous) errorColor else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = when (risk) {
                    SkillRisk.SAFE -> "只读操作"
                    SkillRisk.MODERATE -> "会更改数据，可在应用内调整"
                    SkillRisk.DANGEROUS -> "高风险操作，必须手动确认"
                    null -> ""
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (dangerous) errorColor else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            preview.lines.forEach { line ->
                Text(
                    text = "· $line",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
