@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.wsy.ci.feature.quest

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wsy.ci.core.db.DomainEntity
import com.wsy.ci.core.db.QuestEntity
import com.wsy.ci.core.db.QuestType
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

@Composable
fun QuestEditorDialog(
    initial: QuestEntity,
    domains: List<DomainEntity>,
    onSave: (QuestEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    var title by remember { mutableStateOf(initial.title) }
    var description by remember { mutableStateOf(initial.description) }
    var type by remember { mutableStateOf(initial.type) }
    var domainId by remember { mutableStateOf(initial.domainId) }
    var deadline by remember {
        mutableStateOf(initial.deadlineEpochDay?.let { LocalDate.ofEpochDay(it).toString() } ?: "")
    }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
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
                        label = { Text("🗡 主线（大目标，有截止）") },
                    )
                    FilterChip(
                        selected = type == QuestType.SIDE,
                        onClick = { type = QuestType.SIDE },
                        label = { Text("🔁 支线（习惯，吃连击）") },
                    )
                }
                OutlinedTextField(
                    value = title, onValueChange = { title = it },
                    label = { Text("名称") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = description, onValueChange = { description = it },
                    label = { Text("描述（可选）") },
                    modifier = Modifier.fillMaxWidth(),
                )
                DomainDropdown(domains, domainId) { domainId = it }
                if (type == QuestType.MAIN) {
                    OutlinedTextField(
                        value = deadline, onValueChange = { deadline = it },
                        label = { Text("截止日期 yyyy-MM-dd（可选）") }, singleLine = true,
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
                        deadlineEpochDay = deadlineDay,
                    )
                )
                onDismiss()
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
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
        OutlinedTextField(
            value = selectedName, onValueChange = {}, readOnly = true,
            label = { Text("关联领域（挣经验升头衔）") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("（无领域）") }, onClick = { onSelect(null); expanded = false })
            domains.forEach { d ->
                DropdownMenuItem(text = { Text(d.name) }, onClick = { onSelect(d.id); expanded = false })
            }
        }
    }
}
