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

package com.wsy.ci.core.designsystem

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * 表单/详情/确认类对话框外壳：定宽、圆角 28、surfaceContainerHigh、内边距 24，
 * 底部一行「取消 + 可选破坏性动作 + 可选主按钮」。全工程这类弹窗都走这里，不要各自搭 Surface。
 *
 * [destructiveLabel] 排在取消与主按钮之间，用 error 色标出来（删除这类不可逆动作）。
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
fun CiFormDialog(
    title: String,
    onDismiss: () -> Unit,
    confirmLabel: String?,
    onConfirm: (() -> Unit)?,
    dismissLabel: String = "取消",
    destructiveLabel: String? = null,
    onDestructive: (() -> Unit)? = null,
    @DrawableRes confirmIcon: Int? = null,
    width: Dp = CiSizes.dialogFormWidth,
    content: @Composable () -> Unit,
) {
    val dialogModifier = Modifier.ciResponsiveDialogWidth(width)
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = dialogModifier,
            shape = CiShapes.dialog,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = CiElevation.dialog,
        ) {
            Column(
                modifier = Modifier.padding(CiSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(CiSpacing.sm + 2.dp),
            ) {
                Text(text = title, style = MaterialTheme.typography.titleLarge)
                content()
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(top = CiSpacing.xxs + 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(CiSpacing.sm, Alignment.End),
                    verticalArrangement = Arrangement.spacedBy(CiSpacing.xs),
                ) {
                    TextButton(onClick = onDismiss) { Text(dismissLabel) }
                    if (destructiveLabel != null && onDestructive != null) {
                        TextButton(
                            onClick = onDestructive,
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error,
                            ),
                        ) { Text(destructiveLabel) }
                    }
                    if (confirmLabel != null && onConfirm != null) {
                        Button(onClick = onConfirm, shape = CiShapes.pill) {
                            confirmIcon?.let {
                                CiFunctionIcon(
                                    resourceId = it,
                                    contentDescription = null,
                                    modifier = Modifier.size(CiSizes.compactIcon),
                                )
                            }
                            Text(
                                confirmLabel,
                                modifier = if (confirmIcon == null) {
                                    Modifier
                                } else {
                                    Modifier.padding(start = CiSpacing.xs)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
