package com.wsy.ci.feature.quest

import androidx.compose.runtime.Composable
import com.wsy.ci.core.designsystem.CiPasteImportDialog
import com.wsy.ci.core.porting.CiImport
import com.wsy.ci.core.porting.ImportPreview

/** 学习计划导入：外壳走通用的 [CiPasteImportDialog]，这里只提供文案与模板。 */
@Composable
fun ImportDialog(
    preview: ImportPreview?,
    result: String?,
    onPreview: (String) -> Unit,
    onConfirm: () -> Unit,
    onCancelPreview: () -> Unit,
    onDismissResult: () -> Unit,
    onDismiss: () -> Unit,
) {
    CiPasteImportDialog(
        title = "导入 JSON 计划",
        hint = "把「复制模板」的内容发给任何 AI（或自己写），按格式改好后粘贴到下面。" +
            "校验通过后会先列出要导入的内容，确认了才写进库里。",
        template = CiImport.TEMPLATE,
        pasteLabel = "粘贴 JSON",
        preview = preview,
        result = result,
        onPreview = onPreview,
        onConfirm = onConfirm,
        onCancelPreview = onCancelPreview,
        onDismissResult = onDismissResult,
        onDismiss = onDismiss,
    )
}
