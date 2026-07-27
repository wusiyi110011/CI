package com.wsy.ci.feature.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wsy.ci.core.util.TimeFormat

@Composable
fun StatsScreen(viewModel: StatsViewModel = viewModel()) {
    val period by viewModel.period.collectAsState()
    val data by viewModel.data.collectAsState()
    val analysis by viewModel.analysis.collectAsState()
    val analyzing by viewModel.analyzing.collectAsState()
    val message by viewModel.message.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { viewModel.refresh() }
    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); viewModel.dismissMessage() }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                Text("复盘", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                SingleChoiceSegmentedButtonRow {
                    StatsPeriod.entries.forEachIndexed { i, p ->
                        SegmentedButton(
                            selected = period == p,
                            onClick = { viewModel.setPeriod(p) },
                            shape = SegmentedButtonDefaults.itemShape(i, StatsPeriod.entries.size),
                        ) { Text(p.label) }
                    }
                }
            }

            val d = data
            if (d == null || d.totalMinutes == 0 && d.plannedCount == 0) {
                Text("这个周期还没有数据。完成几次专注后再来看看。", color = MaterialTheme.colorScheme.outline)
            } else {
                TimeLedgerPanel(d)
                PlanVsActualPanel(d)
                HeatmapPanel(d)
                HabitGridPanel(d)
                EconomyPanel(d)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = viewModel::analyze, enabled = !analyzing) {
                        Text(if (analyzing) "分析中…" else "🤖 AI 深度分析")
                    }
                    TextButton(onClick = viewModel::exportCsv) { Text("导出 CSV 到下载目录") }
                }
            }
        }
    }

    analysis?.let {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("AI 洞察") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) { Text(it) }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.analysis.value = null }) { Text("关闭") }
            },
        )
    }
}

@Composable
private fun PanelCard(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun TimeLedgerPanel(d: StatsData) {
    PanelCard("⏳ 时间账本 · 共 ${TimeFormat.duration(d.totalMinutes)}") {
        val max = d.byDomain.maxOfOrNull { it.minutes } ?: 1
        d.byDomain.forEach { stat ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stat.name, modifier = Modifier.width(120.dp), style = MaterialTheme.typography.bodyMedium)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(18.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(stat.minutes.toFloat() / max)
                            .height(18.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)),
                    )
                }
                Text(
                    " ${TimeFormat.duration(stat.minutes)}",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.width(90.dp),
                )
            }
        }
    }
}

@Composable
private fun PlanVsActualPanel(d: StatsData) {
    PanelCard("🎯 计划 vs 实际") {
        Text("完成率 ${(d.completionRate * 100).toInt()}%（计划 ${d.plannedCount} · 完成 ${d.doneCount} · 跳过 ${d.skippedCount}）")
        LinearProgressIndicator(progress = { d.completionRate }, modifier = Modifier.fillMaxWidth())
        Text(
            "计划投入 ${TimeFormat.duration(d.plannedMinutes)} · 实际投入 ${TimeFormat.duration(d.actualMinutes)}" +
                (d.estimateRatio?.let { "（实际/计划 = ${"%.2f".format(it)}）" } ?: ""),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun HeatmapPanel(d: StatsData) {
    PanelCard("🔥 黄金时段热力图（横轴 6~24 点）") {
        val maxCell = d.heat.maxOf { row -> row.max() }.coerceAtLeast(1)
        val days = listOf("一", "二", "三", "四", "五", "六", "日")
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            days.forEachIndexed { dow, label ->
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(16.dp))
                    for (hour in 6 until 24) {
                        val v = d.heat[dow][hour]
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(
                                    MaterialTheme.colorScheme.primary.copy(
                                        alpha = if (v == 0) 0.06f else 0.2f + 0.8f * v / maxCell
                                    )
                                ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HabitGridPanel(d: StatsData) {
    PanelCard("📅 打卡格（周期内每日专注）") {
        val days = (d.fromDay..d.toDay).toList()
        val maxMin = d.minutesByDay.values.maxOrNull()?.coerceAtLeast(1) ?: 1
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            days.forEach { day ->
                val v = d.minutesByDay[day] ?: 0
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                MaterialTheme.colorScheme.secondary.copy(
                                    alpha = if (v == 0) 0.08f else 0.25f + 0.75f * v / maxMin
                                )
                            ),
                    )
                    Text(
                        TimeFormat.shortDate(day).substringAfter("/"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
        }
    }
}

@Composable
private fun EconomyPanel(d: StatsData) {
    PanelCard("💰 经济收支") {
        Row {
            Column(modifier = Modifier.weight(1f)) {
                Text("收入", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                Text("+${d.earnedCi} CI", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.secondary)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("商城支出", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                Text("-${d.spentCi} CI", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.error)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("净入", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                Text("${d.earnedCi - d.spentCi} CI", style = MaterialTheme.typography.headlineSmall)
            }
        }
    }
}
