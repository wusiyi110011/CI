package com.wsy.ci.feature.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wsy.ci.core.db.TaskEntity
import com.wsy.ci.core.db.TaskStatus
import com.wsy.ci.core.economy.FocusOutcome
import com.wsy.ci.core.util.TimeFormat
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.delay

@Composable
fun TodayScreen(viewModel: TodayViewModel = viewModel()) {
    val tasks by viewModel.tasks.collectAsState()
    val sessions by viewModel.sessions.collectAsState()
    val running by viewModel.runningSession.collectAsState()
    val domains by viewModel.domains.collectAsState()
    val quests by viewModel.quests.collectAsState()
    val balance by viewModel.balance.collectAsState()
    val settlement by viewModel.lastSettlement.collectAsState()
    val nlState by viewModel.nlState.collectAsState()

    var editing by remember { mutableStateOf<TaskEntity?>(null) }
    var detailTask by remember { mutableStateOf<TaskEntity?>(null) }
    var showStopDialog by remember { mutableStateOf(false) }

    // 每秒刷新，驱动计时器与「当前时刻」红线
    var nowTick by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowTick = System.currentTimeMillis()
            delay(1000)
        }
    }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text("添加任务") },
                icon = { Text("＋") },
                onClick = {
                    val now = LocalTime.now()
                    val startMin = (now.hour * 60 + now.minute + 14) / 15 * 15
                    editing = TaskEntity(
                        title = "",
                        epochDay = LocalDate.now().toEpochDay(),
                        startMinute = startMin.coerceAtMost(23 * 60),
                        endMinute = (startMin + 60).coerceAtMost(24 * 60 - 1),
                    )
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            HeaderBar(balance = balance)
            running?.let { session ->
                RunningCard(
                    title = tasks.firstOrNull { it.id == session.taskId }?.title ?: "自由专注",
                    elapsedMillis = nowTick - session.startAt,
                    onStop = { showStopDialog = true },
                )
            } ?: FreeFocusRow(onStart = { viewModel.startTimer(null) })
            NlAdjustRow(loading = nlState is TodayViewModel.NlState.Loading, onSubmit = viewModel::parseNl)
            Spacer(modifier = Modifier.padding(4.dp))
            DayTimeline(
                tasks = tasks,
                actuals = sessionsToBlocks(sessions, nowTick),
                onTaskClick = { detailTask = it },
                nowMinute = LocalTime.now().let { it.hour * 60 + it.minute },
                modifier = Modifier.weight(1f),
            )
        }
    }

    editing?.let { task ->
        TaskEditorDialog(
            initial = task,
            domains = domains,
            quests = quests,
            onSave = viewModel::saveTask,
            onDelete = if (task.id != 0L) viewModel::deleteTask else null,
            onDismiss = { editing = null },
            onCreateDomain = viewModel::addDomain,
        )
    }

    detailTask?.let { task ->
        TaskDetailDialog(
            task = task,
            isTimerRunning = running != null,
            onStart = { viewModel.startTimer(task); detailTask = null },
            onEdit = { editing = task; detailTask = null },
            onSkip = { viewModel.skipTask(task); detailTask = null },
            onDismiss = { detailTask = null },
        )
    }

    if (showStopDialog) {
        StopFocusDialog(
            onPick = { focus ->
                viewModel.stopTimer(focus)
                showStopDialog = false
            },
            onDismiss = { showStopDialog = false },
        )
    }

    settlement?.let { s ->
        SettlementDialog(settlement = s, onDismiss = viewModel::dismissSettlement)
    }

    NlDialogs(state = nlState, viewModel = viewModel)
}

@Composable
private fun NlAdjustRow(loading: Boolean, onSubmit: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        androidx.compose.material3.OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("一句话调整：如「明天下午2-5点有事」") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        Button(
            onClick = { onSubmit(text); text = "" },
            enabled = !loading && text.isNotBlank(),
        ) { Text(if (loading) "解析中…" else "AI 重排") }
    }
}

@Composable
private fun NlDialogs(state: TodayViewModel.NlState, viewModel: TodayViewModel) {
    when (state) {
        is TodayViewModel.NlState.BlockerPreview -> AlertDialog(
            onDismissRequest = viewModel::dismissNl,
            title = { Text("解析出以下占位时段") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    state.blockers.forEach {
                        Text("· ${it.date} ${it.start}–${it.end}  ${it.title}")
                    }
                    Text(
                        "确认后这些时段将不可安排任务，并自动重排受影响的日程",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            },
            confirmButton = {
                Button(onClick = { viewModel.confirmBlockers(state.blockers) }) { Text("确认并重排") }
            },
            dismissButton = { TextButton(onClick = viewModel::dismissNl) { Text("取消") } },
        )
        is TodayViewModel.NlState.Diff -> AlertDialog(
            onDismissRequest = {},
            title = { Text("重排预览") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    state.lines.forEach { Text("· $it") }
                }
            },
            confirmButton = {
                Button(onClick = { viewModel.applyDiff(state) }) { Text("应用") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelDiff(state) }) { Text("放弃（撤销占位）") }
            },
        )
        is TodayViewModel.NlState.Error -> AlertDialog(
            onDismissRequest = viewModel::dismissNl,
            title = { Text("解析失败") },
            text = { Text(state.message) },
            confirmButton = { TextButton(onClick = viewModel::dismissNl) { Text("知道了") } },
        )
        else -> Unit
    }
}

@Composable
private fun HeaderBar(balance: Long) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("今日", style = MaterialTheme.typography.headlineMedium)
            Text(
                TimeFormat.date(LocalDate.now().toEpochDay()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
            )
        }
        AssistChip(onClick = {}, label = { Text("💰 $balance CI") })
    }
}

@Composable
private fun RunningCard(title: String, elapsedMillis: Long, onStop: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    TimeFormat.elapsed(elapsedMillis),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            Button(onClick = onStop) { Text("结束专注") }
        }
    }
}

@Composable
private fun FreeFocusRow(onStart: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        TextButton(onClick = onStart) { Text("▶ 自由专注（不关联任务）") }
    }
}

@Composable
private fun TaskDetailDialog(
    task: TaskEntity,
    isTimerRunning: Boolean,
    onStart: () -> Unit,
    onEdit: () -> Unit,
    onSkip: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(task.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${TimeFormat.minuteOfDay(task.startMinute)} – ${TimeFormat.minuteOfDay(task.endMinute)}")
                Text("难度：${task.difficulty.label} ×${task.difficulty.factor}")
                if (task.note.isNotBlank()) Text(task.note)
                if (isTimerRunning && task.status == TaskStatus.PLANNED) {
                    Text(
                        "已有进行中的专注，结束后才能开始新任务",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            if (task.status == TaskStatus.PLANNED && !isTimerRunning) {
                Button(onClick = onStart) { Text("▶ 开始专注") }
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onEdit) { Text("编辑") }
                if (task.status == TaskStatus.PLANNED) {
                    TextButton(onClick = onSkip) { Text("跳过") }
                }
                TextButton(onClick = onDismiss) { Text("关闭") }
            }
        },
    )
}

@Composable
private fun StopFocusDialog(onPick: (FocusOutcome) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("这次专注的结果？") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FocusOutcome.entries.forEach { outcome ->
                    Button(
                        onClick = { onPick(outcome) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("${outcome.label}（系数 ×${outcome.factor}）")
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun SettlementDialog(
    settlement: com.wsy.ci.core.data.Settlement,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (settlement.newLevel != null) "🎉 升级啦！" else "✅ 专注入账") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("专注 ${TimeFormat.duration(settlement.minutes)}")
                Text(
                    "+${settlement.rewardCi} CI",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
                if (settlement.expGained > 0) Text("领域经验 +${settlement.expGained}")
                settlement.newLevel?.let { lv ->
                    Text(
                        "头衔升至 $lv 级，奖励 +${settlement.levelUpRewardCi} CI！",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("收下") } },
    )
}
