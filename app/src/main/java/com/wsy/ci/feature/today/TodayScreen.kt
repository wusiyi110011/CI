package com.wsy.ci.feature.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wsy.ci.core.db.DomainEntity
import com.wsy.ci.core.db.QuestEntity
import com.wsy.ci.core.db.QuestType
import com.wsy.ci.core.db.SessionEntity
import com.wsy.ci.core.db.TaskEntity
import com.wsy.ci.core.db.TaskStatus
import com.wsy.ci.core.designsystem.CiBalanceChip
import com.wsy.ci.core.designsystem.CiChip
import com.wsy.ci.core.designsystem.CiDifficultyChip
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
    val tasks by viewModel.tasks.collectAsState()
    val segments by viewModel.segments.collectAsState()
    val sessions by viewModel.sessions.collectAsState()
    val running by viewModel.runningSession.collectAsState()
    val runningTask by viewModel.runningTask.collectAsState()
    val domains by viewModel.domains.collectAsState()
    val quests by viewModel.quests.collectAsState()
    val balance by viewModel.balance.collectAsState()
    val settlement by viewModel.lastSettlement.collectAsState()
    val nlState by viewModel.nlState.collectAsState()

    var editing by remember { mutableStateOf<TaskEntity?>(null) }
    var detailTask by remember { mutableStateOf<TaskEntity?>(null) }
    /** 正在补录的自由专注：没有任务可点，就地给这段时间补一个。 */
    var freeSession by remember { mutableStateOf<SessionEntity?>(null) }
    var showStopDialog by remember { mutableStateOf(false) }

    // 每秒刷新，驱动计时器与「当前时刻」指示线
    var nowTick by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowTick = System.currentTimeMillis()
            delay(1000)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(CiSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(CiSpacing.sm + 2.dp),
        ) {
            CiScreenHeader(
                title = "今日",
                subtitle = TimeFormat.date(LocalDate.now().toEpochDay()),
                trailing = { CiBalanceChip(balance) },
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
            } ?: IdleFocusCard(onStart = { viewModel.startTimer(null) })

            NlAdjustRow(
                loading = nlState is TodayViewModel.NlState.Loading,
                onSubmit = viewModel::parseNl,
            )

            DayTimeline(
                segments = segments,
                // 计时中的任务补进查表范围：刚开始的那一瞬任务还没落到今日列表里
                actuals = sessionsToBlocks(
                    sessions = sessions,
                    tasks = tasks + listOfNotNull(runningTask),
                    nowMillis = nowTick,
                    epochDay = viewModel.todayEpochDay,
                    quests = quests,
                ),
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
                modifier = Modifier.weight(1f),
            )

            TimelineLegend()
        }

        FloatingActionButton(
            onClick = {
                // 起点取下一个整刻；跨过零点就落到明天的 00:xx，而不是把时间压回白天
                val now = LocalTime.now()
                val rounded = (now.hour * 60 + now.minute + 14) / 15 * 15
                editing = TaskEntity(
                    title = "",
                    epochDay = LocalDate.now().toEpochDay() + rounded / MINUTES_PER_DAY,
                    startMinute = rounded % MINUTES_PER_DAY,
                    endMinute = rounded % MINUTES_PER_DAY + 60,
                )
            },
            shape = CiShapes.fab,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(CiSpacing.lg)
                .size(CiSizes.fab),
        ) {
            Text("＋", style = MaterialTheme.typography.headlineSmall)
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
            onPick = { focus, note ->
                viewModel.stopTimer(focus, note)
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

/**
 * 自由专注的补录草稿：时段取这次专注的真实起止，领域/任务线沿用 session 上记的。
 * 状态直接给「已完成」——这段时间是真的投入过了，不是待办。
 */
private fun freeSessionDraft(session: SessionEntity, nowMillis: Long): TaskEntity {
    val end = session.endAt ?: nowMillis
    val minutes = ((end - session.startAt) / 60_000L).toInt().coerceAtLeast(1)
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
                    container = MaterialTheme.colorScheme.tertiaryContainer,
                    content = MaterialTheme.colorScheme.onTertiaryContainer,
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
                color = MaterialTheme.colorScheme.tertiary,
            )
            Button(
                onClick = onStop,
                shape = CiShapes.pill,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                ),
                contentPadding = PaddingValues(
                    horizontal = 30.dp,
                    vertical = 14.dp,
                ),
            ) {
                Text("结束", style = MaterialTheme.typography.labelLarge)
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
private fun IdleFocusCard(onStart: () -> Unit) {
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
                    text = "点开时间线上的任务开始，或直接自由专注",
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
                Text("▶ 自由专注", style = MaterialTheme.typography.labelLarge)
            }
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
            Text(
                text = if (loading) "解析中…" else "✨ AI 重排",
                style = MaterialTheme.typography.labelLarge,
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
