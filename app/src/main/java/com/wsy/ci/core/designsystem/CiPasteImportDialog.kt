package com.wsy.ci.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp

/** 粘贴框的最小与常规高度：平板上一屏能看下十来行 JSON。 */
private val PASTE_FIELD_MIN_HEIGHT = 200.dp
private val PASTE_FIELD_HEIGHT = 280.dp

/**
 * 通用「粘贴 JSON 导入」对话框：复制模板 → 喂给任意聊天 AI → 粘回来校验落库。
 *
 * 学习计划与商城货架是两套格式，但交互完全一样，所以外壳做成参数化的：
 * [template] 是丢给 AI 的格式说明，[result] 由调用方在导入后回填（以 ✅ 开头视为成功）。
 */
@Composable
fun CiPasteImportDialog(
    title: String,
    hint: String,
    template: String,
    pasteLabel: String,
    result: String?,
    onImport: (String) -> Unit,
    onDismissResult: () -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current

    if (result != null) {
        val succeeded = result.startsWith("✅")
        val close = { onDismissResult(); if (succeeded) onDismiss() }
        AlertDialog(
            shape = CiShapes.dialog,
            onDismissRequest = close,
            title = { Text(if (succeeded) "导入完成" else "导入失败") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) { Text(result) }
            },
            confirmButton = { TextButton(onClick = close) { Text("知道了") } },
        )
        return
    }

    AlertDialog(
        shape = CiShapes.dialog,
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(CiSpacing.xs)) {
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(CiSpacing.xs)) {
                    TextButton(onClick = { clipboard.setText(AnnotatedString(template)) }) {
                        Text("📋 复制模板")
                    }
                    TextButton(onClick = { text = clipboard.getText()?.text ?: "" }) {
                        Text("📥 从剪贴板粘贴")
                    }
                }
                CiFormField(
                    value = text,
                    onValueChange = { text = it },
                    label = pasteLabel,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = PASTE_FIELD_MIN_HEIGHT)
                        .height(PASTE_FIELD_HEIGHT)
                        .verticalScroll(rememberScrollState()),
                    singleLine = false,
                )
            }
        },
        confirmButton = {
            Button(onClick = { onImport(text) }, enabled = text.isNotBlank()) { Text("校验并导入") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
