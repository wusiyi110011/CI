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

package com.wsy.ci.feature.today

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wsy.ci.R
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wsy.ci.core.db.DomainEntity
import com.wsy.ci.core.db.BlockerEntity
import com.wsy.ci.core.db.QuestEntity
import com.wsy.ci.core.db.QuestType
import com.wsy.ci.core.db.SessionEntity
import com.wsy.ci.core.db.TaskEntity
import com.wsy.ci.core.db.TaskStatus
import com.wsy.ci.core.designsystem.CiBalanceChip
import com.wsy.ci.core.designsystem.CiChip
import com.wsy.ci.core.designsystem.CiDifficultyChip
import com.wsy.ci.core.designsystem.CiFunctionIcon
import com.wsy.ci.core.designsystem.CiLegendDot
import com.wsy.ci.core.designsystem.CiPanelCard
import com.wsy.ci.core.designsystem.CiScreenHeader
import com.wsy.ci.core.designsystem.CiShapes
import com.wsy.ci.core.designsystem.CiSizes
import com.wsy.ci.core.designsystem.CiSpacing
import com.wsy.ci.core.designsystem.CiTextField
import com.wsy.ci.core.designsystem.CiTextStyles
import com.wsy.ci.core.designsystem.CiTheme
import com.wsy.ci.core.economy.Difficulty
import com.wsy.ci.core.timeline.MINUTES_PER_DAY
import com.wsy.ci.core.util.TimeFormat
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.delay

@Composable
fun TodayScreen(viewModel: TodayViewModel = viewModel()) {
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val segments by viewModel.segments.collectAsStateWithLifecycle()
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val blockers by viewModel.blockers.collectAsStateWithLifecycle()
    val running by viewModel.runningSession.collectAsStateWithLifecycle()
    val runningTask by viewModel.runningTask.collectAsStateWithLifecycle()
    val domains by viewModel.domains.collectAsStateWithLifecycle()
    val quests by viewModel.quests.collectAsStateWithLifecycle()
    val balance by viewModel.balance.collectAsStateWithLifecycle()
    val settlement by viewModel.lastSettlement.collectAsStateWithLifecycle()
    val nlState by viewModel.nlState.collectAsStateWithLifecycle()
    val undoSchedule by viewModel.undoSchedule.collectAsStateWithLifecycle()
    val todayEpochDay by viewModel.todayEpochDay.collectAsStateWithLifecycle()

    var editing by remember { mutableStateOf<TaskEntity?>(null) }
    var detailTask by remember { mutableStateOf<TaskEntity?>(null) }
    /** 正在补录的自由专注：没有任务可点，就地给这段时间补一个。 */
    var freeSession by remember { mutableStateOf<SessionEntity?>(null) }
    var showStopDialog by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    // 应用重排后只在短时间内提供撤销，动作实际由 ViewModel 恢复内存快照。
    LaunchedEffect(undoSchedule) {
        val undo = undoSchedule ?: return@LaunchedEffect
        val result = snackbar.showSnackbar(
            message = "日程已重排",
            actionLabel = "撤销",
            duration = SnackbarDuration.Indefinite,
        )
        if (viewModel.undoSchedule.value == undo) {
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.undoLastReschedule()
            } else {
                viewModel.dismissUndo()
            }
        }
    }

    // 专注中每秒刷新计时；空闲时只需每分钟校准一次当前时刻线。
    var nowTick by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(running?.id) {
        while (true) {
            nowTick = System.currentTimeMillis()
            delay(if (running == null) 60_000 else 1_000)
        }
    }

    val actualBlocks = sessionsToBlocks(
        sessions = sessions,
        tasks = tasks + listOfNotNull(runningTask),
        nowMillis = nowTick,
        epochDay = todayEpochDay,
        quests = quests,
    )
    val nextTask = segments.firstOrNull {
        !it.isContinuation && it.task.status == TaskStatus.PLANNED
    }?.task

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(CiSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(CiSpacing.sm + 2.dp),
        ) {
            CiScreenHeader(
                title = "今日",
                subtitle = "${TimeFormat.date(todayEpochDay)} · 今日已完成 " +
                    "${tasks.count { it.epochDay == todayEpochDay && it.status == TaskStatus.DONE }} / " +
                    "${tasks.count { it.epochDay == todayEpochDay }}",
                trailing = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(CiSpacing.xs),
                    ) {
                        CiBalanceChip(balance)
                        TextButton(onClick = { viewModel.startTimer(null) }) {
                            CiFunctionIcon(
                                resourceId = R.drawable.ic_ci_focus_timer,
                                contentDescription = null,
                                modifier = Modifier.size(CiSizes.compactIcon),
                            )
                            Text("自由专注", modifier = Modifier.padding(start = CiSpacing.xxs))
                        }
                    }
                },
            )

            running?.let { session ->
                RunningCard(
                    session = session,
                    task = runningTask,
                    quests = quests,
                    domains = domains,
                    elapsedMillis = nowTick - session.startAt,
                    onStop = { showStopDialog = true },
                )
            } ?: IdleFocusCard(
                nextTask = nextTask,
                onStart = { nextTask?.let(viewModel::startTimer) ?: viewModel.startTimer(null) },
            )

            NlAdjustRow(
                loading = nlState is TodayViewModel.NlState.Loading,
                onSubmit = viewModel::parseNl,
            )

            val conflictCount = remember(segments) { timelineConflictCount(segments) }
            TimelineFeedback(
                blockers = blockers.size,
                conflicts = conflictCount,
            )
            BlockerSummary(blockers)

            Row(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(CiSpacing.md),
            ) {
                Column(
                    modifier = Modifier.weight(0.62f).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(CiSpacing.xs),
                ) {
                    DayTimeline(
                        segments = segments,
                        actuals = actualBlocks,
                        blockers = blockers,
                        onTaskClick = { detailTask = it },
                        // 实际轨：挂了任务的点开任务卡，自由专注点开补录卡（给它补个名字就成了任务）
                        onActualClick = { block ->
                            val session = sessions.firstOrNull { it.id == block.sessionId }
                            val task = session?.taskId?.let { id ->
                                (tasks + listOfNotNull(runningTask)).firstOrNull { it.id == id }
                            }
                            if (task != null) detailTask = task else freeSession = session
                        },
                        nowMinute = LocalTime.now().let { it.hour * 60 + it.minute },
                        scrollIdentity = todayEpochDay,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    )
                    TimelineLegend()
                }
                TodayLedger(
                    segments = segments,
                    actuals = actualBlocks,
                    modifier = Modifier.weight(0.38f).fillMaxHeight(),
                )
            }
        }

        FloatingActionButton(
            onClick = {
                // 起点取下一个整刻；跨过零点就落到明天的 00:xx，而不是把时间压回白天
                val now = LocalTime.now()
                val rounded = (now.hour * 60 + now.minute + 14) / 15 * 15
                editing = TaskEntity(
                    title = "",
                    epochDay = todayEpochDay + rounded / MINUTES_PER_DAY,
                    startMinute = rounded % MINUTES_PER_DAY,
                    endMinute = rounded % MINUTES_PER_DAY + 60,
                )
            },
            shape = CiShapes.fab,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(CiSpacing.lg)
                .size(CiSizes.fab),
        ) {
            CiFunctionIcon(
                resourceId = R.drawable.ic_ci_add,
                contentDescription = "新建任务",
            )
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(CiSpacing.lg),
        )
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

    freeSession?.let { session ->
        TaskEditorDialog(
            initial = freeSessionDraft(session, nowTick),
            domains = domains,
            quests = quests,
            onSave = { viewModel.attachTaskToSession(it, session.id) },
            onDelete = { viewModel.deleteSession(session.id) },
            onDismiss = { freeSession = null },
            onCreateDomain = viewModel::addDomain,
            deleteLabel = "删除这段记录",
            focusedMinutes = sessionMinutes(session, nowTick),
        )
    }

    detailTask?.let { task ->
        val minutes by remember(task.id) { viewModel.focusMinutes(task.id) }
            .collectAsStateWithLifecycle(initialValue = 0)
        TaskDetailDialog(
            task = task,
            focusedMinutes = minutes,
            isTimerRunning = running != null,
            onStart = { viewModel.startTimer(task); detailTask = null },
            onEdit = { editing = task; detailTask = null },
            onSkip = { viewModel.skipTask(task); detailTask = null },
            onDismiss = { detailTask = null },
        )
    }

    if (showStopDialog) {
        StopFocusDialog(
            onPick = { focus, note ->
                viewModel.stopTimer(focus, note)
                showStopDialog = false
            },
            onDismiss = { showStopDialog = false },
        )
    }

    settlement?.let { s ->
        SettlementDialog(
            settlement = s,
            onDismiss = viewModel::dismissSettlement,
            onContinue = nextTask?.let { task ->
                {
                    viewModel.dismissSettlement()
                    viewModel.startTimer(task)
                }
            },
        )
    }

    NlDialogs(state = nlState, viewModel = viewModel)
}

/**
 * 自由专注的补录草稿：时段取这次专注的真实起止，领域/任务线沿用 session 上记的。
 * 状态直接给「已完成」——这段时间是真的投入过了，不是待办。
 */
private fun freeSessionDraft(session: SessionEntity, nowMillis: Long): TaskEntity {
    // 至少给 1 分钟：零长度的时间块在时间线上是看不见的
    val minutes = sessionMinutes(session, nowMillis).coerceAtLeast(1)
    val startMinute = TimeFormat.millisToMinuteOfDay(session.startAt)
    return TaskEntity(
        title = "",
        epochDay = TimeFormat.millisToEpochDay(session.startAt),
        startMinute = startMinute,
        endMinute = startMinute + minutes,
        domainId = session.domainId,
        questId = session.questId,
        status = TaskStatus.DONE,
    )
}

/** 一次专注实际投入的分钟数；还没收工就算到此刻。 */
private fun sessionMinutes(session: SessionEntity, nowMillis: Long): Int =
    TimeFormat.millisToMinutes((session.endAt ?: nowMillis) - session.startAt)

/** 进行中计时卡：领域 chip + 标题｜大号计时器｜结束按钮。 */
@Composable
private fun RunningCard(
    session: SessionEntity,
    task: TaskEntity?,
    quests: List<QuestEntity>,
    domains: List<DomainEntity>,
    elapsedMillis: Long,
    onStop: () -> Unit,
) {
    // 没有具体任务时（对着支线直接打卡），任务线从 session 上取
    val quest = quests.firstOrNull { it.id == (task?.questId ?: session.questId) }
    val domain = domains.firstOrNull { it.id == (task?.domainId ?: session.domainId) }

    CiPanelCard(
        modifier = Modifier.fillMaxWidth().height(CiSizes.timerCardHeight),
        contentPadding = 20.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                modifier = Modifier.widthIn(max = 260.dp),
                verticalArrangement = Arrangement.spacedBy(CiSpacing.xs),
            ) {
                CiChip(
                    text = focusScopeLabel(quest, domain, task),
                    container = MaterialTheme.colorScheme.secondaryContainer,
                    content = MaterialTheme.colorScheme.onSecondaryContainer,
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    text = task?.title ?: quest?.title ?: "自由专注",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = TimeFormat.elapsed(elapsedMillis),
                style = CiTextStyles.timer,
                color = MaterialTheme.colorScheme.secondary,
            )
            OutlinedButton(
                onClick = onStop,
                shape = CiShapes.pill,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
                border = BorderStroke(CiSizes.border, MaterialTheme.colorScheme.error),
                contentPadding = PaddingValues(
                    horizontal = 30.dp,
                    vertical = 14.dp,
                ),
            ) {
                CiFunctionIcon(
                    resourceId = R.drawable.ic_ci_stop,
                    contentDescription = null,
                    modifier = Modifier.size(CiSizes.compactIcon),
                )
                Text(
                    "结束",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(start = CiSpacing.xs),
                )
            }
        }
    }
}

/**
 * 「领域 chip」文案：主线/支线 · 领域名，提前开工别的日子的任务时补上那天的日期，
 * 免得看不出计的是哪一天的时间块。
 */
private fun focusScopeLabel(
    quest: QuestEntity?,
    domain: DomainEntity?,
    task: TaskEntity?,
): String {
    val kind = when (quest?.type) {
        QuestType.MAIN -> "主线"
        QuestType.SIDE -> "支线"
        null -> "自由"
    }
    val scope = domain?.name?.let { "$kind · $it" } ?: kind
    val otherDay = task?.epochDay?.takeIf { it != LocalDate.now().toEpochDay() }
    return otherDay?.let { "$scope · ${TimeFormat.shortDate(it)} 的任务" } ?: scope
}

/** 无进行中专注时占住计时卡的位置，保持屏内布局稳定。 */
@Composable
private fun IdleFocusCard(nextTask: TaskEntity?, onStart: () -> Unit) {
    CiPanelCard(
        modifier = Modifier.fillMaxWidth().height(CiSizes.timerCardHeight),
        contentPadding = 20.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(CiSpacing.xs)) {
                CiChip(
                    text = "未在专注",
                    container = MaterialTheme.colorScheme.surfaceContainerHigh,
                    content = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    text = nextTask?.let {
                        "${TimeFormat.minuteOfDay(it.startMinute)} · ${it.title} · ${it.difficulty.label}"
                    } ?: "今天还没有计划，先开始一段自由专注",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "00:00",
                style = CiTextStyles.timer,
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            Button(
                onClick = onStart,
                shape = CiShapes.pill,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary,
                    contentColor = MaterialTheme.colorScheme.onTertiary,
                ),
                contentPadding = PaddingValues(
                    horizontal = 24.dp,
                    vertical = 14.dp,
                ),
            ) {
                CiFunctionIcon(
                    resourceId = R.drawable.ic_ci_focus_timer,
                    contentDescription = null,
                    modifier = Modifier.size(CiSizes.compactIcon),
                )
                Text(
                    if (nextTask == null) "自由专注" else "开始下一项",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(start = CiSpacing.xs),
                )
            }
        }
    }
}

/** 今日账页：实际记录优先，其后列出尚未开始的计划，和时间线共享同一批数据。 */
@Composable
private fun TodayLedger(
    segments: List<com.wsy.ci.core.timeline.TaskSegment>,
    actuals: List<ActualBlock>,
    modifier: Modifier = Modifier,
) {
    val pending = segments
        .filter { !it.isContinuation && it.task.status == TaskStatus.PLANNED }
        .map { it.task }
    Column(
        modifier = modifier
            .clip(CiShapes.field)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CiShapes.field)
            .padding(CiSpacing.md),
        verticalArrangement = Arrangement.spacedBy(CiSpacing.xs),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("今天的账页", style = MaterialTheme.typography.titleSmall)
            Text(
                text = "${actuals.count { !it.isContinuation }} 条实际",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        if (actuals.none { !it.isContinuation } && pending.isEmpty()) {
            Text(
                text = "今天还没有专注记录或待办任务",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            actuals.filter { !it.isContinuation }.forEach { block ->
                LedgerRow(
                    time = TimeFormat.minuteOfDay(block.startMinute),
                    title = block.title,
                    detail = if (block.running) "专注中…" else {
                        "${block.focus.label} · 实际 " +
                            TimeFormat.duration(block.endMinute - block.startMinute)
                    },
                    rewardCi = block.rewardCi,
                )
            }
            pending.forEach { task ->
                LedgerRow(
                    time = TimeFormat.minuteOfDay(task.startMinute),
                    title = task.title,
                    detail = "计划 ${TimeFormat.duration(task.endMinute - task.startMinute)} · ${task.status.label}",
                )
            }
        }
    }
}

@Composable
private fun LedgerRow(
    time: String,
    title: String,
    detail: String,
    rewardCi: Long = 0,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CiSpacing.xs),
    ) {
        Text(
            text = time,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(min = CiSizes.timeRulerWidth),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (rewardCi > 0) {
            Text(
                text = "+$rewardCi CI",
                style = MaterialTheme.typography.labelSmall,
                color = CiTheme.colors.income,
            )
        }
    }
}

/**
 * blocker 在计划轨上可能与任务重叠。时间线仍保留任务点击能力，这里同步列出锁定原因，
 * 避免任务绘制层叠后用户只看到「为什么没法排」而看不到具体原因。
 */
@Composable
private fun BlockerSummary(blockers: List<BlockerEntity>) {
    if (blockers.isEmpty()) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CiShapes.field)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = CiSpacing.sm, vertical = CiSpacing.xs),
        verticalArrangement = Arrangement.spacedBy(CiSpacing.xxs),
    ) {
        Text(
            text = "锁定时段（任务不会安排在这里）",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        blockers.forEach { blocker ->
            Text(
                text = "${TimeFormat.minuteOfDay(blocker.startMinute)}–" +
                    "${TimeFormat.minuteOfDay(blocker.endMinute)} · " +
                    blocker.title.ifBlank { "不可安排" },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 一句话调整行：输入框 + AI 重排按钮。 */
@Composable
private fun NlAdjustRow(loading: Boolean, onSubmit: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    Row(
        modifier = Modifier.fillMaxWidth().height(CiSizes.fieldHeight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CiSpacing.sm),
    ) {
        CiTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = "用一句话说明变化，例如：下午2点后要出门",
            modifier = Modifier.weight(1f),
        )
        Button(
            onClick = { onSubmit(text); text = "" },
            enabled = !loading && text.isNotBlank(),
            shape = CiShapes.pill,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            ),
            contentPadding = PaddingValues(
                horizontal = 22.dp,
                vertical = CiSpacing.sm,
            ),
        ) {
            if (!loading) {
                CiFunctionIcon(
                    resourceId = R.drawable.ic_ci_ai_schedule,
                    contentDescription = null,
                    modifier = Modifier.size(CiSizes.compactIcon),
                )
            }
            Text(
                text = if (loading) "解析中…" else "AI 重排",
                style = MaterialTheme.typography.labelLarge,
                modifier = if (loading) Modifier else Modifier.padding(start = CiSpacing.xs),
            )
        }
    }
}

/** 图例行：4 档难度 chip + 4 态圆点。 */
@Composable
private fun TimelineLegend() {
    val colors = CiTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CiSpacing.md),
    ) {
        LegendCaption("难度")
        Difficulty.entries.forEach { CiDifficultyChip(it) }
        LegendCaption("状态", startPadding = CiSpacing.sm)
        TaskStatus.entries.forEach { CiLegendDot(colors.taskBlock(it).accent, it.label) }
    }
}

@Composable
private fun LegendCaption(text: String, startPadding: Dp = 0.dp) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = startPadding),
    )
}
