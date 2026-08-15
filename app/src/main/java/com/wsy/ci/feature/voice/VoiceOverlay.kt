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
import androidx.compose.foundation.layout.width
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
import com.wsy.ci.core.util.TimeFormat
import com.wsy.ci.core.voice.skill.SkillPreview

/**
 * 长按图标录音期间的浮层：无实时文字（sherpa 整段解码没有 partial result），
 * 只用时长和音量条给出正在录音的反馈。上滑取消时整体转 error 色。不可点击关闭。
 */
@Composable
fun VoiceRecordingDialog(elapsedMillis: Long, amplitude: Float, cancelling: Boolean) {
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
        confirmButton = {},
    )
}

/** 识别结果确认浮层：文字可编辑（识别不准时手改），通用技能预览卡片，绝不静默执行。 */
@Composable
fun VoiceConfirmDialog(
    text: String,
    onTextChange: (String) -> Unit,
    preview: SkillPreview?,
    onExecute: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        shape = CiShapes.dialog,
        modifier = Modifier.width(CiSizes.dialogFormWidth),
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
                SkillPreviewCard(preview)
            }
        },
        confirmButton = {
            Button(onClick = onExecute, shape = CiShapes.pill, enabled = preview != null) {
                Text("执行")
            }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("取消") } },
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

/** 场景2「查一下哪天有什么安排」的结果浮层：按时间顺序列出任务，可跳去日历屏细看。 */
@Composable
fun VoiceScheduleResultDialog(tasks: List<TaskEntity>, onOpenCalendar: () -> Unit, onDismiss: () -> Unit) {
    val sorted = tasks.sortedWith(compareBy({ it.epochDay }, { it.startMinute }))
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = CiShapes.dialog,
        modifier = Modifier.width(CiSizes.dialogFormWidth),
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
fun VoiceErrorDialog(message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = CiShapes.dialog,
        icon = { CiFunctionIcon(resourceId = R.drawable.ic_ci_warning, contentDescription = null) },
        title = { Text("没能完成") },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("知道了") } },
    )
}

/** 按 [VoiceUiState] 在各浮层间切换，`Idle` 时不渲染任何内容。 */
@Composable
internal fun VoiceOverlayHost(state: VoiceUiState, viewModel: VoiceViewModel) {
    when (state) {
        VoiceUiState.Idle -> Unit
        is VoiceUiState.Recording -> VoiceRecordingDialog(state.elapsedMillis, state.amplitude, state.cancelling)
        VoiceUiState.Recognizing -> VoiceRecognizingDialog()
        is VoiceUiState.Confirm -> VoiceConfirmDialog(
            text = state.text,
            onTextChange = viewModel::onTextEdited,
            preview = state.preview,
            onExecute = { state.invocation?.let(viewModel::execute) },
            onCancel = viewModel::dismiss,
        )
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
        is VoiceUiState.Error -> VoiceErrorDialog(message = state.message, onDismiss = viewModel::dismiss)
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
        shape = CiShapes.dialog,
        modifier = Modifier.width(CiSizes.dialogFormWidth),
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
private fun SkillPreviewCard(preview: SkillPreview?) {
    val errorColor = MaterialTheme.colorScheme.error
    CiPanelCard {
        if (preview == null) {
            Text(
                text = "没听懂，可以直接改上面的文字再试",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                text = preview.title,
                style = MaterialTheme.typography.titleMedium,
                color = if (preview.dangerous) errorColor else MaterialTheme.colorScheme.onSurface,
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
