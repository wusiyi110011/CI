package com.wsy.ci.feature.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wsy.ci.core.designsystem.CiChip
import com.wsy.ci.core.designsystem.CiDifficultyChip
import com.wsy.ci.core.designsystem.CiSegmentedControl
import com.wsy.ci.core.designsystem.CiShapes
import com.wsy.ci.core.designsystem.CiSpacing
import com.wsy.ci.core.designsystem.CiStatPanel
import com.wsy.ci.core.designsystem.CiTheme
import com.wsy.ci.core.designsystem.tabularNums
import com.wsy.ci.core.util.TimeFormat

/** 明细行内各列宽度，固定住才能跨行对齐。 */
private val TIME_COLUMN_WIDTH = 150.dp
private val DOMAIN_COLUMN_WIDTH = 96.dp
private val NUMBER_COLUMN_WIDTH = 84.dp
private val STATUS_DOT_SIZE = 9.dp

/**
 * 按当前筛选条件过滤明细。纯函数，不碰状态，便于单测与直接在 composable 里调用。
 */
fun filterRecords(
    records: List<TaskRecord>,
    status: RecordFilter,
    domain: DomainFilter,
): List<TaskRecord> = records.filter { record ->
    val statusOk = status.matches(record.task.status)
    val domainOk = when (domain) {
        DomainFilter.All -> true
        is DomainFilter.Only -> record.task.domainId == domain.domainId
    }
    statusOk && domainOk
}

/**
 * 面板 6：任务明细列表，带状态与领域两个筛选维度。
 *
 * 注意这里用普通 Column 而非 LazyColumn——整个复盘屏外层已经是 verticalScroll，
 * 同方向嵌套懒加载列表会直接崩。一个周期内任务量有限（月视图百来条），全量渲染可接受。
 */
@Composable
fun TaskRecordsPanel(
    records: List<TaskRecord>,
    statusFilter: RecordFilter,
    domainFilter: DomainFilter,
    onStatusFilter: (RecordFilter) -> Unit,
    onDomainFilter: (DomainFilter) -> Unit,
    onRecordClick: (TaskRecord) -> Unit,
    modifier: Modifier = Modifier,
) {
    val filtered = filterRecords(records, statusFilter, domainFilter)
    val totalMinutes = filtered.sumOf { it.actualMinutes }
    val totalCi = filtered.sumOf { it.rewardCi }

    CiStatPanel(
        title = "任务明细 · ${filtered.size} 条 · 实际投入 ${TimeFormat.duration(totalMinutes)}" +
            if (totalCi > 0) " · 入账 $totalCi CI" else "",
        modifier = modifier,
    ) {
        CiSegmentedControl(
            options = RecordFilter.entries,
            selected = statusFilter,
            label = { it.label },
            onSelect = onStatusFilter,
        )
        DomainFilterRow(
            records = records,
            selected = domainFilter,
            onSelect = onDomainFilter,
        )

        if (filtered.isEmpty()) {
            Text(
                text = "当前筛选下没有任务。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = CiSpacing.sm),
            )
        } else {
            RecordHeaderRow()
            Column(verticalArrangement = Arrangement.spacedBy(CiSpacing.xs)) {
                filtered.forEach { record ->
                    RecordRow(record, onClick = { onRecordClick(record) })
                }
            }
        }
    }
}

/**
 * 领域筛选 chip 行。选项只从当前周期真实出现过的领域里取——
 * 列出一堆点了必然空列表的领域没有意义。横向滚动避免领域多时挤爆。
 */
@Composable
private fun DomainFilterRow(
    records: List<TaskRecord>,
    selected: DomainFilter,
    onSelect: (DomainFilter) -> Unit,
) {
    val options = records
        .map { it.task.domainId to it.domainName }
        .distinct()
        .sortedBy { it.second }
    if (options.size <= 1) return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(top = CiSpacing.xxs),
        horizontalArrangement = Arrangement.spacedBy(CiSpacing.xs),
    ) {
        FilterChip(
            text = "全部领域",
            selected = selected == DomainFilter.All,
            onClick = { onSelect(DomainFilter.All) },
        )
        options.forEach { (id, name) ->
            FilterChip(
                text = name,
                selected = selected == DomainFilter.Only(id),
                onClick = { onSelect(DomainFilter.Only(id)) },
            )
        }
    }
}

/** 可点选的筛选 chip，选中态靠描边区分（配色仍全部来自 ColorScheme）。 */
@Composable
private fun FilterChip(text: String, selected: Boolean, onClick: () -> Unit) {
    CiChip(
        text = text,
        container = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        content = if (selected) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        borderColor = if (selected) MaterialTheme.colorScheme.onSurface else null,
        verticalPadding = 5.dp,
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun RecordHeaderRow() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = CiSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CiSpacing.sm),
    ) {
        Box(modifier = Modifier.size(STATUS_DOT_SIZE))
        HeaderCell("日期 / 时段", Modifier.width(TIME_COLUMN_WIDTH))
        HeaderCell("任务", Modifier.weight(1f))
        HeaderCell("领域", Modifier.width(DOMAIN_COLUMN_WIDTH))
        HeaderCell("实际", Modifier.width(NUMBER_COLUMN_WIDTH), TextAlign.End)
        HeaderCell("CI", Modifier.width(NUMBER_COLUMN_WIDTH), TextAlign.End)
    }
}

@Composable
private fun HeaderCell(text: String, modifier: Modifier, align: TextAlign = TextAlign.Start) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = align,
        modifier = modifier,
    )
}

/**
 * 明细单行；备注非空时在标题下方另起一行完整展示（复盘内容不截断）。
 * 整行可点，点开这条任务的任务卡（含实际学了多久）。
 */
@Composable
private fun RecordRow(record: TaskRecord, onClick: () -> Unit) {
    val task = record.task
    val accent = CiTheme.colors.taskBlock(task.status).accent
    Column(
        modifier = Modifier.clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(CiSpacing.xxs),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CiSpacing.sm),
        ) {
            Box(
                modifier = Modifier
                    .size(STATUS_DOT_SIZE)
                    .clip(CiShapes.pill)
                    .background(accent)
            )
            Text(
                text = "${TimeFormat.shortDate(task.epochDay)}  " +
                    "${TimeFormat.minuteOfDay(task.startMinute)}–" +
                    TimeFormat.minuteOfDay(task.endMinute),
                style = MaterialTheme.typography.bodySmall.tabularNums(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(TIME_COLUMN_WIDTH),
            )
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CiSpacing.xs),
            ) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                CiDifficultyChip(task.difficulty, showFactor = false)
            }
            Text(
                text = record.domainName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(DOMAIN_COLUMN_WIDTH),
            )
            Text(
                text = if (record.actualMinutes > 0) {
                    TimeFormat.duration(record.actualMinutes)
                } else {
                    "—"
                },
                style = MaterialTheme.typography.bodySmall.tabularNums(),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.End,
                modifier = Modifier.width(NUMBER_COLUMN_WIDTH),
            )
            Text(
                text = if (record.rewardCi > 0) "+${record.rewardCi}" else "—",
                style = MaterialTheme.typography.bodySmall.tabularNums(),
                color = if (record.rewardCi > 0) {
                    CiTheme.colors.income
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                textAlign = TextAlign.End,
                modifier = Modifier.width(NUMBER_COLUMN_WIDTH),
            )
        }
        if (task.note.isNotBlank()) {
            Text(
                text = task.note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = STATUS_DOT_SIZE + CiSpacing.sm),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
    }
}
