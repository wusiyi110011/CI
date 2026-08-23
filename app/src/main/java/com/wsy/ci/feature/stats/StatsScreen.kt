/*
 * Copyright 2026 吴思毅
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.wsy.ci.feature.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wsy.ci.R
import com.wsy.ci.core.designsystem.CiFunctionIcon
import com.wsy.ci.core.designsystem.CiPanelCard
import com.wsy.ci.core.designsystem.CiProgressBar
import com.wsy.ci.core.designsystem.CiScreenHeader
import com.wsy.ci.core.designsystem.CiSegmentedControl
import com.wsy.ci.core.designsystem.CiShapes
import com.wsy.ci.core.designsystem.CiSizes
import com.wsy.ci.core.designsystem.CiSpacing
import com.wsy.ci.core.designsystem.CiStatPanel
import com.wsy.ci.core.designsystem.CiTheme
import com.wsy.ci.core.designsystem.HeatScale
import com.wsy.ci.core.designsystem.CiWindowSize
import com.wsy.ci.core.designsystem.LocalCiWindowSize
import com.wsy.ci.core.designsystem.formatSignedAmount
import com.wsy.ci.core.designsystem.tabularNums
import com.wsy.ci.core.util.TimeFormat
import com.wsy.ci.feature.today.TaskDetailDialog

/** 面板高度，取自逐屏布局规格第 5 节。 */
private val BAR_PANEL_HEIGHT = 260.dp
private val HEAT_PANEL_HEIGHT = 300.dp
private val ECONOMY_PANEL_HEIGHT = 150.dp

/** 热力图横轴范围：06:00–23:00，共 18 列。 */
private const val HEAT_START_HOUR = 6
private const val HEAT_END_HOUR = 24

/** 打卡格每行列数。 */
private const val CHECKIN_COLUMNS = 10

@Composable
fun StatsScreen(viewModel: StatsViewModel = viewModel()) {
    val isCompact = LocalCiWindowSize.current == CiWindowSize.COMPACT
    val period by viewModel.period.collectAsStateWithLifecycle()
    val recordFilter by viewModel.recordFilter.collectAsStateWithLifecycle()
    val domainFilter by viewModel.domainFilter.collectAsStateWithLifecycle()
    val mainFilter by viewModel.mainFilter.collectAsStateWithLifecycle()
    val sideFilter by viewModel.sideFilter.collectAsStateWithLifecycle()
    val activeRecordFilters by viewModel.activeRecordFilters.collectAsStateWithLifecycle()
    val data by viewModel.data.collectAsStateWithLifecycle()
    val analysis by viewModel.analysis.collectAsStateWithLifecycle()
    val analyzing by viewModel.analyzing.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    /** 明细里点开的那条任务，展示只读任务卡。 */
    var detailRecord by remember { mutableStateOf<TaskRecord?>(null) }

    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); viewModel.dismissMessage() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(if (isCompact) CiSpacing.md else CiSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(CiSpacing.sm + 2.dp),
        ) {
            if (isCompact) {
                CiScreenHeader(title = "复盘")
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(CiSpacing.xs),
                ) {
                    CiSegmentedControl(
                        options = StatsPeriod.entries,
                        selected = period,
                        label = { it.label },
                        onSelect = viewModel::setPeriod,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Button(
                            onClick = viewModel::analyze,
                            enabled = !analyzing,
                            shape = CiShapes.pill,
                        ) {
                            CiFunctionIcon(
                                resourceId = R.drawable.ic_ci_ai_schedule,
                                contentDescription = null,
                                modifier = Modifier.size(CiSizes.compactIcon),
                            )
                            Text(
                                if (analyzing) "分析中…" else "分析",
                                modifier = Modifier.padding(start = CiSpacing.xs),
                            )
                        }
                        TextButton(onClick = viewModel::exportCsv) { Text("导出") }
                    }
                }
            } else {
                CiScreenHeader(
                    title = "复盘",
                    subtitle = "记录事实，再决定下一轮怎么走",
                    trailing = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(CiSpacing.xs),
                        ) {
                            CiSegmentedControl(
                                options = StatsPeriod.entries,
                                selected = period,
                                label = { it.label },
                                onSelect = viewModel::setPeriod,
                            )
                            Button(
                                onClick = viewModel::analyze,
                                enabled = !analyzing,
                                shape = CiShapes.pill,
                            ) {
                                if (!analyzing) {
                                    CiFunctionIcon(
                                        resourceId = R.drawable.ic_ci_ai_schedule,
                                        contentDescription = null,
                                        modifier = Modifier.size(CiSizes.compactIcon),
                                    )
                                }
                                Text(
                                    if (analyzing) "分析中…" else "AI 深度分析",
                                    modifier = if (analyzing) {
                                        Modifier
                                    } else {
                                        Modifier.padding(start = CiSpacing.xs)
                                    },
                                )
                            }
                            TextButton(onClick = viewModel::exportCsv) { Text("导出 CSV") }
                        }
                    },
                )
            }

            val d = data
            if (d == null || (d.totalMinutes == 0 && d.plannedCount == 0)) {
                CiPanelCard(modifier = Modifier.fillMaxWidth(), contentPadding = CiSpacing.lg) {
                    Text(
                        text = "这个周期还没有数据。完成几次专注后再来看看。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Column(
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(CiSpacing.md),
                ) {
                    if (isCompact) {
                        DomainBarsPanel(d, Modifier.fillMaxWidth().height(BAR_PANEL_HEIGHT))
                        PlanVsActualPanel(d, Modifier.fillMaxWidth().height(BAR_PANEL_HEIGHT))
                        HeatmapPanel(d, Modifier.fillMaxWidth().height(HEAT_PANEL_HEIGHT))
                        CheckinPanel(d, Modifier.fillMaxWidth().height(HEAT_PANEL_HEIGHT))
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(CiSpacing.md)) {
                            DomainBarsPanel(d, Modifier.weight(1f).height(BAR_PANEL_HEIGHT))
                            PlanVsActualPanel(d, Modifier.weight(1f).height(BAR_PANEL_HEIGHT))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(CiSpacing.md)) {
                            HeatmapPanel(d, Modifier.weight(1f).height(HEAT_PANEL_HEIGHT))
                            CheckinPanel(d, Modifier.weight(1f).height(HEAT_PANEL_HEIGHT))
                        }
                    }
                    EconomyPanel(d, Modifier.fillMaxWidth().height(ECONOMY_PANEL_HEIGHT))
                    TaskRecordsPanel(
                        records = d.records,
                        statusFilter = recordFilter,
                        domainFilter = domainFilter,
                        mainFilter = mainFilter,
                        sideFilter = sideFilter,
                        activeFilters = activeRecordFilters,
                        onStatusFilter = viewModel::setRecordFilter,
                        onDomainFilter = viewModel::applyDomainFilter,
                        onMainFilter = viewModel::applyMainFilter,
                        onSideFilter = viewModel::applySideFilter,
                        onRemoveFilter = viewModel::removeRecordFilter,
                        onRecordClick = { detailRecord = it },
                        compact = isCompact,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }

    // 复盘是回看，不提供开始/编辑/跳过——那些动作留在今日与日程屏。
    // 分钟数直接取明细行已经算好的 actualMinutes（本周期内的合计），
    // 好处是卡片上的数字与它下面那一行「实际」列永远对得上。
    detailRecord?.let { record ->
        TaskDetailDialog(
            task = record.task,
            focusedMinutes = record.actualMinutes,
            onDismiss = { detailRecord = null },
        )
    }

    analysis?.let { ui ->
        AlertDialog(
            onDismissRequest = {},
            shape = CiShapes.dialog,
            title = { Text("AI 深度分析 · ${ui.granularityLabel}") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(CiSpacing.sm),
                ) {
                    ReviewSection(title = "洞察", entries = ui.result.insights)
                    ReviewSection(title = "风险与归因", entries = ui.result.risks)
                    ReviewSection(title = "下周建议", entries = ui.result.actions)
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.analysis.value = null }) { Text("关闭") }
            },
        )
    }
}

/** 复盘弹窗里的一个分区：小标题 + 条目列表；没有内容的分区整段跳过。 */
@Composable
private fun ReviewSection(title: String, entries: List<String>) {
    if (entries.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(CiSpacing.xs)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        entries.forEach { entry ->
            Row(horizontalArrangement = Arrangement.spacedBy(CiSpacing.xs)) {
                Text(text = "·", style = MaterialTheme.typography.bodyMedium)
                Text(text = entry, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** 面板 1：按领域时间分布。 */
@Composable
private fun DomainBarsPanel(d: StatsData, modifier: Modifier = Modifier) {
    val maxMinutes = d.byDomain.maxOfOrNull { it.minutes }?.coerceAtLeast(1) ?: 1
    CiStatPanel(
        title = "按领域时间分布 · 共 ${TimeFormat.duration(d.totalMinutes)}",
        modifier = modifier,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            d.byDomain.forEach { stat ->
                LabeledBar(
                    label = stat.name,
                    valueLabel = TimeFormat.duration(stat.minutes),
                    progress = stat.minutes.toFloat() / maxMinutes,
                    // 领域颜色来自 DomainEntity.colorArgb，未分类由统计层提供兜底色；
                    // 不按排行轮换颜色，避免同一领域在周期切换后变色。
                    color = Color(stat.colorArgb and 0xFFFF_FFFFL),
                )
            }
        }
    }
}

/** 面板 2：计划 vs 实际完成率（按任务数 / 按时长两条）。 */
@Composable
private fun PlanVsActualPanel(d: StatsData, modifier: Modifier = Modifier) {
    val durationRate = if (d.plannedMinutes > 0) {
        (d.actualMinutes.toFloat() / d.plannedMinutes).coerceIn(0f, 1f)
    } else {
        0f
    }
    CiStatPanel(title = "计划 vs 实际完成率", modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            LabeledBar(
                label = "按任务数完成率",
                valueLabel = "${(d.completionRate * 100).toInt()}%",
                progress = d.completionRate,
                color = MaterialTheme.colorScheme.primary,
                height = 14.dp,
            )
            LabeledBar(
                label = "按时长完成率",
                valueLabel = "${(durationRate * 100).toInt()}%",
                progress = durationRate,
                color = MaterialTheme.colorScheme.tertiary,
                height = 14.dp,
            )
            Text(
                text = "计划 ${d.plannedCount} · 完成 ${d.doneCount} · 跳过 ${d.skippedCount}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "计划投入 ${TimeFormat.duration(d.plannedMinutes)} · " +
                    "实际投入 ${TimeFormat.duration(d.actualMinutes)}" +
                    (d.estimateRatio?.let { "（实际/计划 ${"%.2f".format(it)}）" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 面板 3：黄金时段热力图，7 行 × 18 列。 */
@Composable
private fun HeatmapPanel(d: StatsData, modifier: Modifier = Modifier) {
    val scale = CiTheme.colors.focusHeat
    val maxCell = d.heat.maxOf { row -> row.max() }.coerceAtLeast(1)
    val weekdayLabels = listOf("一", "二", "三", "四", "五", "六", "日")
    CiStatPanel(
        title = "黄金时段热力图（${HEAT_START_HOUR}–${HEAT_END_HOUR} 点 × 周一至周日）",
        modifier = modifier,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            weekdayLabels.forEachIndexed { dayOfWeek, label ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(CiSpacing.md),
                    )
                    for (hour in HEAT_START_HOUR until HEAT_END_HOUR) {
                        HeatCell(
                            level = scale.levelOf(d.heat[dayOfWeek][hour], maxCell),
                            scale = scale,
                            shape = CiShapes.heatCell,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

/** 面板 4：每日打卡格，10 列 GitHub 风格。 */
@Composable
private fun CheckinPanel(d: StatsData, modifier: Modifier = Modifier) {
    val scale = CiTheme.colors.checkinHeat
    val days = (d.fromDay..d.toDay).toList()
    val maxMinutes = d.minutesByDay.values.maxOrNull()?.coerceAtLeast(1) ?: 1
    CiStatPanel(title = "每日打卡（${days.size} 天）", modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(CiSpacing.xxs)) {
            days.chunked(CHECKIN_COLUMNS).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(CiSpacing.xxs)) {
                    row.forEach { day ->
                        val minutes = d.minutesByDay[day] ?: 0
                        HeatCell(
                            level = scale.levelOf(minutes, maxMinutes),
                            scale = scale,
                            shape = CiShapes.checkinCell,
                            modifier = Modifier.weight(1f),
                            label = TimeFormat.shortDate(day).substringAfter("/"),
                        )
                    }
                    // 末行不足 10 格时补空位，保持格子等宽
                    repeat(CHECKIN_COLUMNS - row.size) {
                        Box(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/** 面板 5：经济收支三列。 */
@Composable
private fun EconomyPanel(d: StatsData, modifier: Modifier = Modifier) {
    val colors = CiTheme.colors
    CiPanelCard(modifier = modifier, contentPadding = CiSpacing.md) {
        Row(modifier = Modifier.fillMaxSize()) {
            EconomyColumn(
                label = "收入",
                value = formatSignedAmount(d.earnedCi),
                color = colors.income,
                modifier = Modifier.weight(1f),
            )
            EconomyColumn(
                label = "支出",
                value = formatSignedAmount(-d.spentCi),
                color = colors.expense,
                modifier = Modifier.weight(1f),
                showDivider = true,
            )
            EconomyColumn(
                label = "结余",
                value = "%,d".format(d.earnedCi - d.spentCi),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
                showDivider = true,
            )
        }
    }
}

@Composable
private fun EconomyColumn(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier,
    showDivider: Boolean = false,
) {
    Row(modifier = modifier) {
        if (showDivider) {
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
        }
        Column(
            modifier = Modifier.padding(start = if (showDivider) CiSpacing.md else 0.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall.tabularNums(),
                color = color,
            )
        }
    }
}

/** 「标签 — 数值」+ 下方一条进度条，领域分布与完成率共用。 */
@Composable
private fun LabeledBar(
    label: String,
    valueLabel: String,
    progress: Float,
    color: Color,
    height: Dp = 10.dp,
) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = valueLabel,
                style = MaterialTheme.typography.labelSmall.tabularNums(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        CiProgressBar(progress = progress, color = color, height = height)
    }
}

/** 热力格子。[label] 非空时在格子下方标注日期。 */
@Composable
private fun HeatCell(
    level: Int,
    scale: HeatScale,
    shape: androidx.compose.ui.graphics.Shape,
    modifier: Modifier = Modifier,
    label: String? = null,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(shape)
                .background(scale.container(level))
        )
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
