@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.wsy.ci.feature.quest

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.wsy.ci.core.designsystem.CiFormField
import com.wsy.ci.core.designsystem.CiFunctionIcon
import com.wsy.ci.core.designsystem.CiShapes
import com.wsy.ci.core.designsystem.CiSizes
import com.wsy.ci.core.designsystem.CiSpacing
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.wsy.ci.R
import com.wsy.ci.core.db.DomainEntity
import com.wsy.ci.core.db.QuestEntity
import com.wsy.ci.core.db.QuestType
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/** [mains] 是可供支线挂靠的主线列表（进行中的 + 当前已选中的那条）。 */
@Composable
fun QuestEditorDialog(
    initial: QuestEntity,
    domains: List<DomainEntity>,
    mains: List<QuestEntity>,
    onSave: (QuestEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    var title by remember { mutableStateOf(initial.title) }
    var description by remember { mutableStateOf(initial.description) }
    var type by remember { mutableStateOf(initial.type) }
    var domainId by remember { mutableStateOf(initial.domainId) }
    var parentQuestId by remember { mutableStateOf(initial.parentQuestId) }
    var deadline by remember {
        mutableStateOf(initial.deadlineEpochDay?.let { LocalDate.ofEpochDay(it).toString() } ?: "")
    }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        shape = CiShapes.dialog,
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id == 0L) "新建任务线" else "编辑任务线") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = type == QuestType.MAIN,
                        onClick = { type = QuestType.MAIN },
                        label = {
                            QuestTypeLabel(R.drawable.ic_ci_main_quest, "主线（大目标，有截止）")
                        },
                    )
                    FilterChip(
                        selected = type == QuestType.SIDE,
                        onClick = { type = QuestType.SIDE },
                        label = {
                            QuestTypeLabel(R.drawable.ic_ci_side_quest, "支线（习惯，吃连击）")
                        },
                    )
                }
                CiFormField(
                    value = title, onValueChange = { title = it },
                    label = "名称", singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                CiFormField(
                    value = description, onValueChange = { description = it },
                    label = "描述（可选）",
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                )
                DomainDropdown(domains, domainId) { domainId = it }
                if (type == QuestType.SIDE) {
                    ParentMainDropdown(
                        mains = mains.filter { it.id != initial.id },
                        selected = parentQuestId,
                        onSelect = { parentQuestId = it },
                    )
                }
                if (type == QuestType.MAIN) {
                    CiFormField(
                        value = deadline, onValueChange = { deadline = it },
                        label = "截止日期 yyyy-MM-dd（可选）", singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (title.isBlank()) { error = "名称不能为空"; return@TextButton }
                val deadlineDay = if (type == QuestType.MAIN && deadline.isNotBlank()) {
                    try {
                        LocalDate.parse(deadline.trim(), DateTimeFormatter.ISO_LOCAL_DATE).toEpochDay()
                    } catch (_: DateTimeParseException) {
                        error = "日期格式应为 yyyy-MM-dd"
                        return@TextButton
                    }
                } else null
                onSave(
                    initial.copy(
                        title = title.trim(),
                        description = description.trim(),
                        type = type,
                        domainId = domainId,
                        // 归属主线只对支线有意义，改回主线时顺手清掉，避免留下无效引用
                        parentQuestId = parentQuestId.takeIf { type == QuestType.SIDE },
                        deadlineEpochDay = deadlineDay,
                    )
                )
                onDismiss()
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/** 支线归属的主线：可以不选，「每天一小时英语」不必非得挂在哪条大目标下面。 */
@Composable
private fun ParentMainDropdown(
    mains: List<QuestEntity>,
    selected: Long?,
    onSelect: (Long?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = mains.firstOrNull { it.id == selected }?.title ?: "（不挂主线，独立支线）"
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        CiFormField(
            value = selectedName, onValueChange = {}, readOnly = true,
            label = "归属主线（可选）",
            trailingIcon = {
                CiFunctionIcon(
                    resourceId = R.drawable.ic_ci_dropdown,
                    contentDescription = if (expanded) "收起主线" else "展开主线",
                    modifier = Modifier
                        .size(CiSizes.compactIcon)
                        .graphicsLayer { rotationZ = if (expanded) 180f else 0f },
                )
            },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
            singleLine = false,
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("（不挂主线，独立支线）") },
                onClick = { onSelect(null); expanded = false },
            )
            mains.forEach { main ->
                DropdownMenuItem(
                    text = { QuestTypeLabel(R.drawable.ic_ci_main_quest, main.title) },
                    onClick = { onSelect(main.id); expanded = false },
                )
            }
        }
    }
}

@Composable
private fun DomainDropdown(
    domains: List<DomainEntity>,
    selected: Long?,
    onSelect: (Long?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = domains.firstOrNull { it.id == selected }?.name ?: "（无领域）"
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        CiFormField(
            value = selectedName, onValueChange = {}, readOnly = true,
            label = "关联领域（挣经验升头衔）",
            trailingIcon = {
                CiFunctionIcon(
                    resourceId = R.drawable.ic_ci_dropdown,
                    contentDescription = if (expanded) "收起领域" else "展开领域",
                    modifier = Modifier
                        .size(CiSizes.compactIcon)
                        .graphicsLayer { rotationZ = if (expanded) 180f else 0f },
                )
            },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
            singleLine = false,
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("（无领域）") }, onClick = { onSelect(null); expanded = false })
            domains.forEach { d ->
                DropdownMenuItem(text = { Text(d.name) }, onClick = { onSelect(d.id); expanded = false })
            }
        }
    }
}

@Composable
private fun QuestTypeLabel(icon: Int, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CiSpacing.xxs),
    ) {
        CiFunctionIcon(
            resourceId = icon,
            contentDescription = null,
            modifier = Modifier.size(CiSizes.compactIcon),
        )
        Text(text)
    }
}
