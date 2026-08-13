package com.wsy.ci.feature.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wsy.ci.core.designsystem.CiDifficultyChip
import com.wsy.ci.core.designsystem.CiDropdownField
import com.wsy.ci.core.designsystem.CiFormDialog
import com.wsy.ci.core.designsystem.CiSegmentedControl
import com.wsy.ci.core.designsystem.CiShapes
import com.wsy.ci.core.designsystem.CiSpacing
import com.wsy.ci.core.designsystem.CiStatPanel
import com.wsy.ci.core.designsystem.CiTheme
import com.wsy.ci.core.designsystem.tabularNums
import com.wsy.ci.core.db.QuestType
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
    main: QuestFilter = QuestFilter.All,
    side: QuestFilter = QuestFilter.All,
    activeFilters: Set<RecordFilterKind> = emptySet(),
): List<TaskRecord> = records.filter { record ->
    val statusOk = status.matches(record.task.status)
    val domainOk = when (domain) {
        DomainFilter.All -> true
        is DomainFilter.Only -> record.task.domainId == domain.domainId
    }
    val mainActive = RecordFilterKind.MAIN in activeFilters
    val sideActive = RecordFilterKind.SIDE in activeFilters
    val questOk = when {
        !mainActive && !sideActive -> true
        mainActive && sideActive ->
            (record.questType == QuestType.MAIN && questMatches(record, main)) ||
                (record.questType == QuestType.SIDE && questMatches(record, side))
        mainActive -> record.questType == QuestType.MAIN && questMatches(record, main)
        else -> record.questType == QuestType.SIDE && questMatches(record, side)
    }
    statusOk && domainOk && questOk
}

fun domainMatches(record: TaskRecord, domain: DomainFilter): Boolean = when (domain) {
    DomainFilter.All -> true
    is DomainFilter.Only -> record.task.domainId == domain.domainId
}

fun questMatches(record: TaskRecord, quest: QuestFilter): Boolean = when (quest) {
    QuestFilter.All -> true
    is QuestFilter.Only -> record.task.questId == quest.questId
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
    mainFilter: QuestFilter = QuestFilter.All,
    sideFilter: QuestFilter = QuestFilter.All,
    activeFilters: Set<RecordFilterKind> = emptySet(),
    onStatusFilter: (RecordFilter) -> Unit,
    onDomainFilter: (DomainFilter) -> Unit,
    onMainFilter: (QuestFilter) -> Unit = {},
    onSideFilter: (QuestFilter) -> Unit = {},
    onRemoveFilter: (RecordFilterKind) -> Unit = {},
    onRecordClick: (TaskRecord) -> Unit,
    modifier: Modifier = Modifier,
) {
    val filtered = filterRecords(records, statusFilter, domainFilter, mainFilter, sideFilter, activeFilters)
    val totalMinutes = filtered.sumOf { it.actualMinutes }
    val totalCi = filtered.sumOf { it.rewardCi }
    var showFilterDialog by remember { mutableStateOf(false) }

    CiStatPanel(
        title = "任务明细 · ${filtered.size} 条 · 实际投入 ${TimeFormat.duration(totalMinutes)}" +
            if (totalCi > 0) " · 入账 $totalCi CI" else "",
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CiSpacing.xs),
        ) {
            CiSegmentedControl(
                options = RecordFilter.entries,
                selected = statusFilter,
                label = { it.label },
                onSelect = onStatusFilter,
            )
            FilterIconButton(onClick = { showFilterDialog = true })
        }
        ActiveFilterChips(
            records = records,
            activeFilters = activeFilters,
            domain = domainFilter,
            main = mainFilter,
            side = sideFilter,
            onRemove = onRemoveFilter,
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

    if (showFilterDialog) {
        AddRecordFilterDialog(
            records = records,
            onDomainFilter = onDomainFilter,
            onMainFilter = onMainFilter,
            onSideFilter = onSideFilter,
            onDismiss = { showFilterDialog = false },
        )
    }
}

@Composable
private fun FilterIconButton(onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.semantics { contentDescription = "添加筛选条件" },
    ) {
        val color = MaterialTheme.colorScheme.onSurfaceVariant
        Canvas(modifier = Modifier.size(CiSpacing.lg)) {
            val funnel = Path().apply {
                moveTo(size.width * 0.18f, size.height * 0.22f)
                lineTo(size.width * 0.82f, size.height * 0.22f)
                lineTo(size.width * 0.58f, size.height * 0.52f)
                lineTo(size.width * 0.58f, size.height * 0.80f)
                lineTo(size.width * 0.42f, size.height * 0.72f)
                lineTo(size.width * 0.42f, size.height * 0.52f)
                close()
            }
            drawPath(funnel, color)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActiveFilterChips(
    records: List<TaskRecord>,
    activeFilters: Set<RecordFilterKind>,
    domain: DomainFilter,
    main: QuestFilter,
    side: QuestFilter,
    onRemove: (RecordFilterKind) -> Unit,
) {
    if (activeFilters.isEmpty()) return
    val domainNames = records.associate { it.task.domainId to it.domainName }
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(CiSpacing.xs),
        verticalArrangement = Arrangement.spacedBy(CiSpacing.xs),
    ) {
        RecordFilterKind.entries.filter { it in activeFilters }.forEach { kind ->
            val value = when (kind) {
                RecordFilterKind.DOMAIN -> when (domain) {
                    DomainFilter.All -> null
                    is DomainFilter.Only -> domainNames[domain.domainId] ?: "未分类"
                }
                RecordFilterKind.MAIN -> questFilterLabel(records, main, QuestType.MAIN)
                RecordFilterKind.SIDE -> questFilterLabel(records, side, QuestType.SIDE)
            }
            RemovableFilterChip(
                text = kind.label + value?.let { "：$it" }.orEmpty(),
                onRemove = { onRemove(kind) },
            )
        }
    }
}

@Composable
private fun RemovableFilterChip(text: String, onRemove: () -> Unit) {
    Surface(
        shape = CiShapes.pill,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(start = CiSpacing.sm, end = CiSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CiSpacing.xs),
        ) {
            Text(text, style = MaterialTheme.typography.labelMedium)
            Text(
                text = "×",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .clickable(onClick = onRemove)
                    .semantics { contentDescription = "移除$text" }
                    .padding(vertical = CiSpacing.xxs),
            )
        }
    }
}

private fun questFilterLabel(
    records: List<TaskRecord>,
    filter: QuestFilter,
    type: QuestType,
): String? = when (filter) {
    QuestFilter.All -> null
    is QuestFilter.Only -> records.firstOrNull {
        it.questType == type && it.task.questId == filter.questId
    }?.questTitle ?: "具体任务线"
}

@Composable
private fun AddRecordFilterDialog(
    records: List<TaskRecord>,
    onDomainFilter: (DomainFilter) -> Unit,
    onMainFilter: (QuestFilter) -> Unit,
    onSideFilter: (QuestFilter) -> Unit,
    onDismiss: () -> Unit,
) {
    var kind by remember { mutableStateOf(RecordFilterKind.MAIN) }
    var valueKey by remember(kind) { mutableStateOf<String?>(null) }
    val options = remember(records, kind) { filterValueOptions(records, kind) }

    CiFormDialog(
        title = "添加筛选条件",
        onDismiss = onDismiss,
        confirmLabel = "确定",
        onConfirm = {
            when (kind) {
                RecordFilterKind.DOMAIN -> onDomainFilter(
                    valueKey?.let { key ->
                        DomainFilter.Only(if (key == NULL_DOMAIN_KEY) null else key.toLong())
                    } ?: DomainFilter.All
                )
                RecordFilterKind.MAIN -> onMainFilter(valueKey?.let { QuestFilter.Only(it.toLong()) } ?: QuestFilter.All)
                RecordFilterKind.SIDE -> onSideFilter(valueKey?.let { QuestFilter.Only(it.toLong()) } ?: QuestFilter.All)
            }
            onDismiss()
        },
        dismissLabel = "取消",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(CiSpacing.sm)) {
            FilterDropdown(
                value = kind.label,
                options = RecordFilterKind.entries.map { it.name to it.label },
                onSelect = { kind = RecordFilterKind.valueOf(it) },
            )
            FilterDropdown(
                value = valueKey?.let { selected -> options.firstOrNull { it.first == selected }?.second }
                    ?: "全部",
                options = listOf(ALL_VALUE_KEY to "全部") + options,
                onSelect = { valueKey = it.takeUnless { key -> key == ALL_VALUE_KEY } },
            )
        }
    }
}

@Composable
private fun FilterDropdown(
    value: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        CiDropdownField(value = value, onClick = { expanded = true })
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (key, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        onSelect(key)
                        expanded = false
                    },
                )
            }
        }
    }
}

private const val ALL_VALUE_KEY = "__all__"
private const val NULL_DOMAIN_KEY = "__none__"

/** 第二级下拉框选项只取当前周期真正出现过的值。 */
private fun filterValueOptions(
    records: List<TaskRecord>,
    kind: RecordFilterKind,
): List<Pair<String, String>> = when (kind) {
    RecordFilterKind.DOMAIN -> records
        .map { (it.task.domainId?.toString() ?: NULL_DOMAIN_KEY) to it.domainName }
        .distinctBy { it.first }
        .sortedBy { it.second }
    RecordFilterKind.MAIN -> records.filter { it.questType == QuestType.MAIN }
        .mapNotNull { record -> record.task.questId?.let { id -> id.toString() to (record.questTitle ?: "主线任务") } }
        .distinctBy { it.first }.sortedBy { it.second }
    RecordFilterKind.SIDE -> records.filter { it.questType == QuestType.SIDE }
        .mapNotNull { record -> record.task.questId?.let { id -> id.toString() to (record.questTitle ?: "支线任务") } }
        .distinctBy { it.first }.sortedBy { it.second }
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
