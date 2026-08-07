package com.wsy.ci.feature.quest

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.wsy.ci.core.db.TaskEntity
import com.wsy.ci.core.designsystem.CiFormDialog
import com.wsy.ci.core.designsystem.CiSizes
import com.wsy.ci.core.designsystem.CiSpacing
import com.wsy.ci.core.util.TimeFormat

/** 从未关联任务中多选，并一次性挂到指定主线。 */
@Composable
fun BatchAssignTasksDialog(
    state: BatchAssignState,
    onConfirm: (Set<Long>) -> Unit,
    onDismiss: () -> Unit,
) {
    val taskIds = remember(state.tasks) { state.tasks.map { it.id }.toSet() }
    var selectedIds by remember(state.quest.id, taskIds) { mutableStateOf(emptySet<Long>()) }
    val allSelected = taskIds.isNotEmpty() && selectedIds == taskIds

    CiFormDialog(
        title = "批量关联到「${state.quest.title}」",
        onDismiss = onDismiss,
        confirmLabel = selectedIds.takeIf { it.isNotEmpty() }?.let { "关联 ${it.size} 个" },
        onConfirm = selectedIds.takeIf { it.isNotEmpty() }?.let { { onConfirm(it) } },
        width = CiSizes.dialogWideWidth,
    ) {
        if (state.tasks.isEmpty()) {
            Text(
                text = "没有未关联的任务",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        selectedIds = if (allSelected) emptySet() else taskIds
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = allSelected,
                    onCheckedChange = {
                        selectedIds = if (allSelected) emptySet() else taskIds
                    },
                )
                Text("全选（${state.tasks.size} 个）", style = MaterialTheme.typography.titleSmall)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = CiSizes.dialogScrollMaxHeight)
                    .verticalScroll(rememberScrollState()),
            ) {
                state.tasks.forEach { task ->
                    TaskChoiceRow(
                        task = task,
                        selected = task.id in selectedIds,
                        onToggle = {
                            selectedIds = if (task.id in selectedIds) {
                                selectedIds - task.id
                            } else {
                                selectedIds + task.id
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun TaskChoiceRow(
    task: TaskEntity,
    selected: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = CiSpacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CiSpacing.xs),
    ) {
        Checkbox(checked = selected, onCheckedChange = { onToggle() })
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = task.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${TimeFormat.date(task.epochDay)}  " +
                    "${TimeFormat.minuteOfDay(task.startMinute)}–${TimeFormat.minuteOfDay(task.endMinute)}" +
                    "  ·  ${task.status.label}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
