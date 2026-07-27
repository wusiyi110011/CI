package com.wsy.ci.feature.quest

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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.wsy.ci.core.designsystem.CiFormField
import com.wsy.ci.core.designsystem.CiShapes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.wsy.ci.core.porting.CiImport

/**
 * JSON 导入对话框：粘贴外部（人或 AI）按模板设计的计划 → 校验 → 落库。
 * 「复制模板」把格式说明放进剪贴板，拿去喂任何聊天 AI 即可。
 */
@Composable
fun ImportDialog(
    result: String?,
    onImport: (String) -> Unit,
    onDismissResult: () -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current

    if (result != null) {
        AlertDialog(
        shape = CiShapes.dialog,
            onDismissRequest = { onDismissResult(); if (result.startsWith("✅")) onDismiss() },
            title = { Text(if (result.startsWith("✅")) "导入完成" else "导入失败") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) { Text(result) }
            },
            confirmButton = {
                TextButton(onClick = { onDismissResult(); if (result.startsWith("✅")) onDismiss() }) {
                    Text("知道了")
                }
            },
        )
        return
    }

    AlertDialog(
        shape = CiShapes.dialog,
        onDismissRequest = onDismiss,
        title = { Text("📥 导入 JSON 计划") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "把「复制模板」的内容发给任何 AI（或自己写），按格式改好后粘贴到下面。支持带 markdown 围栏的原始回复。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { clipboard.setText(AnnotatedString(CiImport.TEMPLATE)) }) {
                        Text("📋 复制模板")
                    }
                    TextButton(onClick = { text = clipboard.getText()?.text ?: "" }) {
                        Text("📥 从剪贴板粘贴")
                    }
                }
                CiFormField(
                    value = text,
                    onValueChange = { text = it },
                    label = "粘贴 JSON",
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 200.dp)
                        .height(280.dp)
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
