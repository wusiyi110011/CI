package com.wsy.ci.feature.quest

import androidx.compose.runtime.Composable
import com.wsy.ci.core.designsystem.CiPasteImportDialog
import com.wsy.ci.core.porting.CiImport

/** 学习计划导入：外壳走通用的 [CiPasteImportDialog]，这里只提供文案与模板。 */
@Composable
fun ImportDialog(
    result: String?,
    onImport: (String) -> Unit,
    onDismissResult: () -> Unit,
    onDismiss: () -> Unit,
) {
    CiPasteImportDialog(
        title = "📥 导入 JSON 计划",
        hint = "把「复制模板」的内容发给任何 AI（或自己写），按格式改好后粘贴到下面。" +
            "支持带 markdown 围栏的原始回复。",
        template = CiImport.TEMPLATE,
        pasteLabel = "粘贴 JSON",
        result = result,
        onImport = onImport,
        onDismissResult = onDismissResult,
        onDismiss = onDismiss,
    )
}
