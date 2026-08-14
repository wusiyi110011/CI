package com.wsy.ci.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.wsy.ci.R
import com.wsy.ci.core.porting.ImportPreview

/** 粘贴框的最小与常规高度：平板上一屏能看下十来行 JSON。 */
private val PASTE_FIELD_MIN_HEIGHT = 200.dp
private val PASTE_FIELD_HEIGHT = 280.dp

/**
 * 通用「粘贴 JSON 导入」对话框：复制模板 → 喂给任意聊天 AI → 粘回来校验落库。
 *
 * 学习计划与商城货架是两套格式，但交互完全一样，所以外壳做成参数化的：
 * [template] 是丢给 AI 的格式说明。
 *
 * 三步走，[preview] 与 [result] 由调用方回填决定当前停在哪一步：
 * 粘贴 → 校验出清单让用户过目（[preview] 非空）→ 确认后落库并报结果（[result] 非空，以 ✅ 开头视为成功）。
 */
@Composable
fun CiPasteImportDialog(
    title: String,
    hint: String,
    template: String,
    pasteLabel: String,
    preview: ImportPreview?,
    result: String?,
    onPreview: (String) -> Unit,
    onConfirm: () -> Unit,
    onCancelPreview: () -> Unit,
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
            title = {
                ImportDialogTitle(
                    icon = if (succeeded) R.drawable.ic_ci_complete else R.drawable.ic_ci_warning,
                    text = if (succeeded) "导入完成" else "导入失败",
                )
            },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        result
                            .removePrefix("✅ ")
                            .removePrefix("❌ ")
                            .replace("\n⚠️ ", "\n注意："),
                    )
                }
            },
            confirmButton = { TextButton(onClick = close) { Text("知道了") } },
        )
        return
    }

    if (preview != null) {
        // 关掉预览退回粘贴框，而不是整个关闭：内容还在，用户可以改完再来一次
        AlertDialog(
            shape = CiShapes.dialog,
            onDismissRequest = onCancelPreview,
            title = { Text("确认导入内容") },
            text = { ImportPreviewBody(preview) },
            confirmButton = {
                Button(onClick = onConfirm, enabled = preview.sections.isNotEmpty()) {
                    Text("确认导入")
                }
            },
            dismissButton = { TextButton(onClick = onCancelPreview) { Text("返回修改") } },
        )
        return
    }

    AlertDialog(
        shape = CiShapes.dialog,
        onDismissRequest = onDismiss,
        title = { ImportDialogTitle(R.drawable.ic_ci_import, title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(CiSpacing.xs)) {
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(CiSpacing.xs)) {
                    TextButton(onClick = { clipboard.setText(AnnotatedString(template)) }) {
                        CiFunctionIcon(
                            resourceId = R.drawable.ic_ci_copy,
                            contentDescription = null,
                            modifier = Modifier.size(CiSizes.compactIcon),
                        )
                        Text("复制模板", modifier = Modifier.padding(start = CiSpacing.xs))
                    }
                    TextButton(onClick = { text = clipboard.getText()?.text ?: "" }) {
                        CiFunctionIcon(
                            resourceId = R.drawable.ic_ci_paste,
                            contentDescription = null,
                            modifier = Modifier.size(CiSizes.compactIcon),
                        )
                        Text("从剪贴板粘贴", modifier = Modifier.padding(start = CiSpacing.xs))
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
            Button(onClick = { onPreview(text) }, enabled = text.isNotBlank()) { Text("校验并预览") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/** 预览正文：一句总结 + 分段明细 + 知会项，长了自己滚。 */
@Composable
private fun ImportPreviewBody(preview: ImportPreview) {
    Column(
        modifier = Modifier
            .heightIn(max = CiSizes.dialogScrollMaxHeight)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(CiSpacing.sm),
    ) {
        Text(text = preview.summary, style = MaterialTheme.typography.titleSmall)
        preview.sections.forEach { section ->
            Column(verticalArrangement = Arrangement.spacedBy(CiSpacing.xxs)) {
                Text(
                    text = section.heading,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                section.lines.forEach { line ->
                    Text(
                        text = "· $line",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        preview.warnings.forEach { warning ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                CiFunctionIcon(
                    resourceId = R.drawable.ic_ci_warning,
                    contentDescription = "警告",
                    modifier = Modifier.size(CiSizes.compactIcon),
                )
                Text(
                    text = warning,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(start = CiSpacing.xs),
                )
            }
        }
    }
}

@Composable
private fun ImportDialogTitle(icon: Int, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CiSpacing.xs),
    ) {
        CiFunctionIcon(
            resourceId = icon,
            contentDescription = null,
            modifier = Modifier.size(CiSizes.actionIcon),
        )
        Text(text)
    }
}
