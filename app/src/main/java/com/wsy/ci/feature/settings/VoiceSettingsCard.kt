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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.wsy.ci.core.designsystem.CiPanelCard
import com.wsy.ci.core.designsystem.CiSegmentedControl
import com.wsy.ci.core.designsystem.CiShapes
import com.wsy.ci.core.designsystem.CiSpacing
import com.wsy.ci.core.designsystem.CiTextField
import com.wsy.ci.core.settings.AppSettings
import com.wsy.ci.core.settings.VoiceAutoExecuteLevel

/**
 * 语音助手偏好面板。面板只修改偏好并发出监听/清除事件，不直接持有或启动语音服务。
 */
@Composable
fun VoiceSettingsCard(
    wakeWordEnabled: Boolean,
    wakePhrase: String,
    ttsEnabled: Boolean,
    autoExecuteLevel: VoiceAutoExecuteLevel,
    wakePromptShown: Boolean,
    correctionLearningEnabled: Boolean,
    wakeModelReady: Boolean,
    onWakeWordEnabledChange: (Boolean) -> Unit,
    onSaveWakePhrase: (String) -> String?,
    onTtsEnabledChange: (Boolean) -> Unit,
    onAutoExecuteLevelChange: (VoiceAutoExecuteLevel) -> Unit,
    onWakePromptShownChange: (Boolean) -> Unit,
    onCorrectionLearningEnabledChange: (Boolean) -> Unit,
    onStartWakeListening: () -> Unit,
    onStopWakeListening: () -> Unit,
    onClearCorrectionRecords: () -> Unit,
    compact: Boolean = false,
) {
    var phraseDraft by rememberSaveable(wakePhrase) { mutableStateOf(wakePhrase) }
    var phraseError by remember { mutableStateOf<String?>(null) }
    var showHelp by rememberSaveable { mutableStateOf(false) }
    var showClearCorrectionConfirm by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(wakePhrase) {
        phraseDraft = wakePhrase
        phraseError = null
    }

    CiPanelCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = if (compact) CiSpacing.md else CiSpacing.lg,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(CiSpacing.sm)) {
            Column(verticalArrangement = Arrangement.spacedBy(CiSpacing.xxs)) {
                Text("语音助手", style = MaterialTheme.typography.titleMedium)
                Text(
                    "通过长按录音或唤醒词调用查询、计时和任务指令。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            VoicePreferenceRow(
                title = "唤醒词",
                detail = when {
                    !wakeModelReady -> "请先下载约 5 MB 的离线唤醒词模型"
                    wakeWordEnabled -> "说出“$wakePhrase”后开始监听"
                    else -> "关闭后只响应录音手势"
                },
                control = {
                    Switch(
                        checked = wakeWordEnabled,
                        enabled = wakeModelReady,
                        onCheckedChange = { enabled ->
                            onWakeWordEnabledChange(enabled)
                            if (enabled) onStartWakeListening() else onStopWakeListening()
                        },
                    )
                },
            )

            if (wakeWordEnabled) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    shape = CiShapes.field,
                ) {
                    Text(
                        "开启唤醒词会常驻通知、占用麦克风，并增加耗电。",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(CiSpacing.md),
                    )
                }
            }

            val wakePhraseField: @Composable (Modifier) -> Unit = { modifier ->
                CiTextField(
                    value = phraseDraft,
                    onValueChange = {
                        phraseDraft = it
                        phraseError = null
                    },
                    placeholder = AppSettings.DEFAULT_WAKE_PHRASE,
                    modifier = modifier,
                )
            }
            val saveWakePhraseButton: @Composable () -> Unit = {
                Button(
                    onClick = { phraseError = onSaveWakePhrase(phraseDraft) },
                    enabled = phraseDraft != wakePhrase,
                    shape = CiShapes.pill,
                ) {
                    Text("保存唤醒词")
                }
            }
            if (compact) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(CiSpacing.xs),
                    horizontalAlignment = Alignment.End,
                ) {
                    wakePhraseField(Modifier.fillMaxWidth())
                    saveWakePhraseButton()
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(CiSpacing.xs),
                ) {
                    wakePhraseField(Modifier.weight(1f))
                    saveWakePhraseButton()
                }
            }
            phraseError?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            Text(
                "唤醒词保存前会自动去掉首尾空格，请输入 2～12 个中文字符。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            VoicePreferenceRow(
                title = "语音播报（TTS）",
                detail = "执行结果由设备扬声器读出",
                control = {
                    Switch(checked = ttsEnabled, onCheckedChange = onTtsEnabledChange)
                },
            )

            Column(verticalArrangement = Arrangement.spacedBy(CiSpacing.xs)) {
                Text("自动执行等级", style = MaterialTheme.typography.titleSmall)
                Text(
                    autoExecuteLevel.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                CiSegmentedControl(
                    options = VoiceAutoExecuteLevel.entries.toList(),
                    selected = autoExecuteLevel,
                    label = {
                        if (!compact) {
                            it.label
                        } else {
                            when (it) {
                                VoiceAutoExecuteLevel.OFF -> "确认"
                                VoiceAutoExecuteLevel.SAFE -> "安全"
                                VoiceAutoExecuteLevel.MODERATE -> "适度"
                            }
                        }
                    },
                    onSelect = onAutoExecuteLevelChange,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            VoicePreferenceRow(
                title = "首次使用提示",
                detail = if (wakePromptShown) "已展示" else "待首次使用时展示",
                control = {
                    TextButton(onClick = { onWakePromptShownChange(!wakePromptShown) }) {
                        Text(if (wakePromptShown) "再次显示" else "标记已展示")
                    }
                },
            )

            VoicePreferenceRow(
                title = "个性化纠错",
                detail = "学习你在确认页修正过的识别结果",
                control = {
                    Switch(
                        checked = correctionLearningEnabled,
                        onCheckedChange = onCorrectionLearningEnabledChange,
                    )
                },
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                OutlinedButton(
                    onClick = { showClearCorrectionConfirm = true },
                    shape = CiShapes.pill,
                ) {
                    Text("管理纠错记录")
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(CiSpacing.xxs)) {
                    Text("指令帮助", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "查看录音手势及可直接说出的示例。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedButton(onClick = { showHelp = true }, shape = CiShapes.pill) {
                    Text("查看示例")
                }
            }
        }
    }

    if (showHelp) {
        VoiceCommandHelpDialog(onDismiss = { showHelp = false })
    }
    if (showClearCorrectionConfirm) {
        AlertDialog(
            onDismissRequest = { showClearCorrectionConfirm = false },
            title = { Text("清除个性化纠错记录？") },
            text = { Text("清除后，语音助手会忘记你之前在确认页做过的修正。") },
            confirmButton = {
                TextButton(onClick = {
                    showClearCorrectionConfirm = false
                    onClearCorrectionRecords()
                }) { Text("确认清除") }
            },
            dismissButton = {
                TextButton(onClick = { showClearCorrectionConfirm = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun VoicePreferenceRow(
    title: String,
    detail: String,
    control: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CiSpacing.md),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(CiSpacing.xxs)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        control()
    }
}

@Composable
private fun VoiceCommandHelpDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("语音指令帮助") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(CiSpacing.sm)) {
                VoiceHelpRow("录音手势", "长按本地 AI 按钮开始录音，上滑取消，松开后识别。")
                VoiceHelpRow("查询", "“今天安排了什么？”、“我在学习什么领域？”")
                VoiceHelpRow("计时", "“开始专注数学 25 分钟”、“停止计时”。")
                VoiceHelpRow("任务", "“完成任务阅读论文”、“跳过任务背单词”。")
                VoiceHelpRow("日程", "“把复习安排到明天晚上”、“今天下午两点空出一小时”。")
                VoiceHelpRow("商城", "“商城有什么？”、“购买护眼休息”。")
                VoiceHelpRow("撤销", "“撤销刚才的调整”。")
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("知道了") } },
    )
}

@Composable
private fun VoiceHelpRow(title: String, example: String) {
    Column(verticalArrangement = Arrangement.spacedBy(CiSpacing.xxs)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(example, style = MaterialTheme.typography.bodySmall)
    }
}
