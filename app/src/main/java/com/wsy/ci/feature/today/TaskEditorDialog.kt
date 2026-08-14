@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.wsy.ci.feature.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.foundation.clickable
import androidx.compose.material3.MaterialTheme
import com.wsy.ci.core.designsystem.CiChip
import com.wsy.ci.core.designsystem.CiFormField
import com.wsy.ci.core.designsystem.CiFunctionIcon
import com.wsy.ci.core.designsystem.CiSizes
import com.wsy.ci.core.designsystem.CiSpacing
import com.wsy.ci.core.designsystem.CiTheme
import com.wsy.ci.core.designsystem.CiShapes
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
import com.wsy.ci.core.db.TaskEntity
import com.wsy.ci.core.economy.Difficulty
import com.wsy.ci.core.timeline.MINUTES_PER_DAY
import com.wsy.ci.core.util.TimeFormat

/**
 * 任务新建/编辑对话框。时间输入用 HH:mm 文本（平板键盘输入效率高于滚轮）。
 *
 * [focusedMinutes] >0 时在最上面标出这段时间实际学了多久——自由专注补录卡走的就是
 * 这个对话框，那张卡上必须能看见刚才那次专注的分钟数。
 */
@Composable
fun TaskEditorDialog(
    initial: TaskEntity,
    domains: List<DomainEntity>,
    quests: List<QuestEntity>,
    onSave: (TaskEntity) -> Unit,
    onDelete: ((TaskEntity) -> Unit)?,
    onDismiss: () -> Unit,
    onCreateDomain: (String, (Long) -> Unit) -> Unit,
    deleteLabel: String = "删除",
    focusedMinutes: Int = 0,
) {
    var title by remember { mutableStateOf(initial.title) }
    var date by remember { mutableStateOf(formatDate(initial.epochDay)) }
    var start by remember { mutableStateOf(formatMinute(initial.startMinute)) }
    var end by remember { mutableStateOf(formatMinute(initial.endMinute)) }
    var difficulty by remember { mutableStateOf(initial.difficulty) }
    var domainId by remember { mutableStateOf(initial.domainId) }
    var questId by remember { mutableStateOf(initial.questId) }
    var locked by remember { mutableStateOf(initial.locked) }
    var note by remember { mutableStateOf(initial.note) }
    var newDomainName by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        shape = CiShapes.dialog,
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id == 0L) "新建任务" else "编辑任务") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                CiFocusedMinutesLine(focusedMinutes)
                CiFormField(
                    value = title, onValueChange = { title = it },
                    label = "任务名", singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CiFormField(
                        value = date, onValueChange = { date = it },
                        label = "日期 yyyy-MM-dd", singleLine = true,
                        modifier = Modifier.width(180.dp),
                    )
                    CiFormField(
                        value = start, onValueChange = { start = it },
                        label = "开始 HH:mm", singleLine = true,
                        modifier = Modifier.width(140.dp),
                    )
                    CiFormField(
                        value = end, onValueChange = { end = it },
                        label = "结束 HH:mm（早于开始即次日）", singleLine = true,
                        modifier = Modifier.width(140.dp),
                    )
                }
                Text(
                    text = "难度",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(CiSpacing.xs)) {
                    Difficulty.entries.forEach { option ->
                        val colors = CiTheme.colors.difficulty(option)
                        CiChip(
                            text = "${option.label} ×${option.factor}",
                            container = colors.container,
                            content = colors.content,
                            borderColor = if (difficulty == option) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                null
                            },
                            verticalPadding = 5.dp,
                            modifier = Modifier.clickable { difficulty = option },
                        )
                    }
                }
                DomainPicker(domains, domainId, onSelect = { domainId = it })
                CiFormField(
                    value = newDomainName, onValueChange = { newDomainName = it },
                    label = "或新建领域（回车前填名字，点保存时创建）", singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                QuestPicker(quests, questId, onSelect = { questId = it })
                FilterChip(
                    selected = locked,
                    onClick = { locked = !locked },
                    label = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(CiSpacing.xxs),
                        ) {
                            if (locked) {
                                CiFunctionIcon(
                                    resourceId = R.drawable.ic_ci_lock,
                                    contentDescription = null,
                                    modifier = Modifier.size(CiSizes.compactIcon),
                                )
                            }
                            Text(if (locked) "已锁定（重排不移动）" else "未锁定")
                        }
                    },
                )
                CiFormField(
                    value = note, onValueChange = { note = it },
                    label = "备注 / 复盘", singleLine = false, minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let { Text(it, color = androidx.compose.material3.MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val epochDay = parseDate(date)
                val startMin = parseMinute(start)
                val endMin = parseMinute(end)
                if (title.isBlank()) { error = "任务名不能为空"; return@TextButton }
                if (epochDay == null) { error = "日期格式应为 yyyy-MM-dd"; return@TextButton }
                if (startMin == null || endMin == null) { error = "时间格式应为 HH:mm"; return@TextButton }
                // 结束早于开始就是跨到次日：23:00–02:00 即一个三小时的深夜块
                val endAcross = if (endMin < startMin) endMin + MINUTES_PER_DAY else endMin
                if (endAcross == startMin) { error = "结束时间不能与开始时间相同"; return@TextButton }
                val build: (Long?) -> TaskEntity = { did ->
                    initial.copy(
                        title = title.trim(), epochDay = epochDay,
                        startMinute = startMin, endMinute = endAcross,
                        difficulty = difficulty, domainId = did, questId = questId, locked = locked,
                        note = note.trim(),
                    )
                }
                if (newDomainName.isNotBlank()) {
                    onCreateDomain(newDomainName.trim()) { id -> onSave(build(id)) }
                } else {
                    onSave(build(domainId))
                }
                onDismiss()
            }) { Text("保存") }
        },
        dismissButton = {
            Row {
                // 删不删由调用方给不给 onDelete 决定：自由专注补录卡删的是那段记录，任务还没建出来
                if (onDelete != null) {
                    TextButton(onClick = { onDelete(initial); onDismiss() }) { Text(deleteLabel) }
                }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
    )
}

@Composable
private fun DomainPicker(
    domains: List<DomainEntity>,
    selected: Long?,
    onSelect: (Long?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = domains.firstOrNull { it.id == selected }?.name ?: "（无领域）"
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        CiFormField(
            value = selectedName, onValueChange = {}, readOnly = true,
            label = "领域",
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
            DropdownMenuItem(
                text = { Text("（无领域）") },
                onClick = { onSelect(null); expanded = false },
            )
            domains.forEach { d ->
                DropdownMenuItem(
                    text = { Text(d.name) },
                    onClick = { onSelect(d.id); expanded = false },
                )
            }
        }
    }
}

@Composable
private fun QuestPicker(
    quests: List<QuestEntity>,
    selected: Long?,
    onSelect: (Long?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = quests.firstOrNull { it.id == selected }?.title ?: "（不关联任务线）"
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        CiFormField(
            value = selectedName, onValueChange = {}, readOnly = true,
            label = "主线/支线",
            trailingIcon = {
                CiFunctionIcon(
                    resourceId = R.drawable.ic_ci_dropdown,
                    contentDescription = if (expanded) "收起任务线" else "展开任务线",
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
                text = { Text("（不关联任务线）") },
                onClick = { onSelect(null); expanded = false },
            )
            quests.forEach { q ->
                DropdownMenuItem(
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(CiSpacing.xs),
                        ) {
                            CiFunctionIcon(
                                resourceId = if (q.type == com.wsy.ci.core.db.QuestType.MAIN) {
                                    R.drawable.ic_ci_main_quest
                                } else {
                                    R.drawable.ic_ci_side_quest
                                },
                                contentDescription = null,
                                modifier = Modifier.size(CiSizes.compactIcon),
                            )
                            Text(q.title)
                        }
                    },
                    onClick = { onSelect(q.id); expanded = false },
                )
            }
        }
    }
}

/** 跨天的结束时间写成「次日 02:00」，[parseMinute] 认得这个前缀。 */
private fun formatMinute(minute: Int): String = TimeFormat.minuteOfDay(minute)

private fun formatDate(epochDay: Long): String = java.time.LocalDate.ofEpochDay(epochDay).toString()

/** 开始计时会把任务搬到今天，所以日期必须可改，否则挪走了就回不去。 */
private fun parseDate(text: String): Long? = try {
    java.time.LocalDate.parse(text.trim()).toEpochDay()
} catch (_: Exception) {
    null
}

/**
 * HH:mm → 当日分钟数。带「次日」前缀（[formatMinute] 的输出）时加上一天，
 * 跨零点的时间块因此可以照常编辑、保存。
 */
private fun parseMinute(text: String): Int? {
    val trimmed = text.trim()
    val nextDay = trimmed.startsWith(NEXT_DAY_PREFIX)
    val clock = if (nextDay) trimmed.removePrefix(NEXT_DAY_PREFIX).trim() else trimmed
    val parts = clock.split(":", "：", ".")
    if (parts.size != 2) return null
    val h = parts[0].toIntOrNull() ?: return null
    val m = parts[1].toIntOrNull() ?: return null
    if (h !in 0..23 || m !in 0..59) return null
    return h * 60 + m + if (nextDay) MINUTES_PER_DAY else 0
}

private const val NEXT_DAY_PREFIX = "次日"
