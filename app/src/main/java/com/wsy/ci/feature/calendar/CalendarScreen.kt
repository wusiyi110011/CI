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

package com.wsy.ci.feature.calendar

import android.app.Application
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wsy.ci.CiApp
import com.wsy.ci.R
import com.wsy.ci.core.db.BlockerEntity
import com.wsy.ci.core.db.DomainEntity
import com.wsy.ci.core.db.QuestStatus
import com.wsy.ci.core.db.QuestType
import com.wsy.ci.core.db.SessionEntity
import com.wsy.ci.core.db.TaskEntity
import com.wsy.ci.core.db.TaskStatus
import com.wsy.ci.core.designsystem.CiShapes
import com.wsy.ci.core.designsystem.CiFunctionIcon
import com.wsy.ci.core.designsystem.CiSizes
import com.wsy.ci.core.designsystem.CiSpacing
import com.wsy.ci.core.designsystem.CiScreenHeader
import com.wsy.ci.core.designsystem.CiSegmentedControl
import com.wsy.ci.core.designsystem.CiTheme
import com.wsy.ci.core.designsystem.CiWindowSize
import com.wsy.ci.core.designsystem.LocalCiWindowSize
import com.wsy.ci.core.designsystem.tabularNums
import com.wsy.ci.core.timeline.DaySegments
import com.wsy.ci.core.timeline.MINUTES_PER_DAY
import com.wsy.ci.core.timeline.TaskSegment
import com.wsy.ci.core.scheduler.Scheduler
import com.wsy.ci.core.scheduler.Slot
import com.wsy.ci.core.util.TimeFormat
import com.wsy.ci.feature.today.DayTimeline
import com.wsy.ci.feature.today.TimelineFeedback
import com.wsy.ci.feature.today.TaskDetailDialog
import com.wsy.ci.feature.today.TaskEditorDialog
import com.wsy.ci.feature.today.sessionsToBlocks
import com.wsy.ci.feature.today.timelineConflictCount
import com.wsy.ci.feature.today.weekConflictCount
import com.wsy.ci.widget.CiWidgetUpdater
import com.wsy.ci.widget.TimerService
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class CalendarMode(val label: String) { DAY("日"), WEEK("周"), MONTH("月") }

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModel(app: Application) : AndroidViewModel(app) {
    private val db = (app as CiApp).container.db

    val selectedDay = MutableStateFlow(LocalDate.now().toEpochDay())

    /** 所选日所在周的周一。 */
    private fun weekStart(epochDay: Long): Long {
        val date = LocalDate.ofEpochDay(epochDay)
        return date.with(DayOfWeek.MONDAY).toEpochDay()
    }

    /** 各视图都多查前一天：跨零点的块属于前一天，但要在这天画出延续段。 */
    val dayTasks = selectedDay.flatMapLatest { db.taskDao().observeByRange(it - 1, it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val daySessions = selectedDay.flatMapLatest {
        db.sessionDao()
            .observeByTimeRange(TimeFormat.dayStartMillis(it - 1), TimeFormat.dayEndMillis(it))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 所选日占位事件：在日视图和共享时间线中都占住对应时段。 */
    val dayBlockers = selectedDay.flatMapLatest { db.blockerDao().observeByDay(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val weekTasks = selectedDay.flatMapLatest {
        val start = weekStart(it)
        db.taskDao().observeByRange(start - 1, start + 6)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 周视图的占位事件按日期归组，保持锁定语义不因切换视图丢失。 */
    val weekBlockers = selectedDay.flatMapLatest { day ->
        val start = weekStart(day)
        combine(*(0..6).map { offset ->
            db.blockerDao().observeByDay(start + offset)
        }.toTypedArray()) { values ->
            (0..6).associate { offset -> start + offset to values[offset] }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val quests = db.questDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * 按与 ScheduleRepository.previewReschedule 相同的规则预估未安置任务。
     * 特别补上主线截止日，否则日程页的预览顺序会和 AI 重排确认结果不一致。
     */
    val unplacedTasks = combine(dayTasks, dayBlockers, quests, selectedDay) { tasks, blockers, quests, day ->
        val nowMinute = if (day == LocalDate.now().toEpochDay()) {
            LocalTime.now().let { it.hour * 60 + it.minute }
        } else {
            null
        }
        Scheduler.reschedule(
            tasks = tasks.filter { it.epochDay == day },
            blockers = blockers.map { Slot(it.startMinute, it.endMinute) },
            nowMinute = nowMinute,
            deadlineByQuestId = quests
                .asSequence()
                .filter { it.type == QuestType.MAIN && it.status == QuestStatus.ACTIVE }
                .mapNotNull { quest -> quest.deadlineEpochDay?.let { quest.id to it } }
                .toMap(),
        ).unplaced
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 所选月份每日专注分钟（月热力图）。 */
    val monthMinutes = selectedDay.flatMapLatest { day ->
        val date = LocalDate.ofEpochDay(day)
        val first = date.withDayOfMonth(1).toEpochDay()
        val last = date.withDayOfMonth(date.lengthOfMonth()).toEpochDay()
        db.sessionDao()
            .observeByTimeRange(TimeFormat.dayStartMillis(first), TimeFormat.dayEndMillis(last))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val domains = db.domainDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 有进行中的专注时不允许再开一个，任务详情里据此禁用「开始专注」。 */
    val runningSession = db.sessionDao().observeOpenSession()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun shift(days: Long) { selectedDay.value += days }

    fun shiftMonth(delta: Long) {
        selectedDay.value = LocalDate.ofEpochDay(selectedDay.value).plusMonths(delta).toEpochDay()
    }
    fun today() { selectedDay.value = LocalDate.now().toEpochDay() }

    fun saveTask(task: TaskEntity) {
        viewModelScope.launch {
            if (task.id == 0L) db.taskDao().insert(task) else db.taskDao().update(task)
            CiWidgetUpdater.updateAll(getApplication())
        }
    }

    fun addDomain(name: String, onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            val id = db.domainDao().insert(DomainEntity(name = name))
            onCreated(id)
        }
    }

    fun deleteTask(task: TaskEntity) {
        viewModelScope.launch {
            db.taskDao().delete(task)
            CiWidgetUpdater.updateAll(getApplication())
        }
    }

    fun skipTask(task: TaskEntity) {
        viewModelScope.launch {
            db.taskDao().update(task.copy(status = TaskStatus.SKIPPED))
            CiWidgetUpdater.updateAll(getApplication())
        }
    }

    /** 某任务累计学习分钟（含历次专注），任务卡展示用。 */
    fun focusMinutes(taskId: Long) = db.sessionDao().observeFocusMillis(taskId)
        .map { TimeFormat.millisToMinutes(it) }

    /** 从日程屏直接开始某个任务的专注（不限当天，提前开工也允许）。 */
    fun startTimer(task: TaskEntity) {
        TimerService.start(getApplication(), task.id, task.title)
        viewModelScope.launch { CiWidgetUpdater.updateAll(getApplication()) }
    }
}

/** 周视图：每小时行高，整天 0:00–24:00 全铺，进屏滚到当前时刻。 */
private val WEEK_HOUR_HEIGHT: Dp = 36.dp
private const val WEEK_START_HOUR = 0
private const val WEEK_END_HOUR = 24

/** 周视图自动滚动时，当前时刻上方预留的一段上下文高度。 */
private val WEEK_SCROLL_LEAD_IN: Dp = 72.dp

/** 周视图左侧时刻尺宽度，比日视图略窄以给 7 列腾空间。 */
private val WEEK_RULER_WIDTH: Dp = 44.dp

/** 周视图任务块最小高度。 */
private val WEEK_MIN_BLOCK_HEIGHT: Dp = 30.dp

/** 窗口末尾留白，保证末班车任务与末尾刻度不被容器裁掉。 */
private val WEEK_BOTTOM_SLACK: Dp = WEEK_MIN_BLOCK_HEIGHT

/** 月视图日历格高度。 */
private val MONTH_CELL_HEIGHT: Dp = 104.dp

/** 手机竖屏月历压缩格高，确保六周月份完整可见。 */
private val COMPACT_MONTH_CELL_HEIGHT: Dp = CiSpacing.xxxl + CiSpacing.xs

/** 月视图无专注日的整格淡出程度。 */
private const val MONTH_EMPTY_ALPHA = 0.6f

private val WEEKDAY_LABELS = listOf("一", "二", "三", "四", "五", "六", "日")

@Composable
fun CalendarScreen(viewModel: CalendarViewModel = viewModel()) {
    val selectedDay by viewModel.selectedDay.collectAsStateWithLifecycle()
    val dayTasks by viewModel.dayTasks.collectAsStateWithLifecycle()
    val daySessions by viewModel.daySessions.collectAsStateWithLifecycle()
    val dayBlockers by viewModel.dayBlockers.collectAsStateWithLifecycle()
    val weekTasks by viewModel.weekTasks.collectAsStateWithLifecycle()
    val weekBlockers by viewModel.weekBlockers.collectAsStateWithLifecycle()
    val unplacedTasks by viewModel.unplacedTasks.collectAsStateWithLifecycle()
    val monthSessions by viewModel.monthMinutes.collectAsStateWithLifecycle()
    val domains by viewModel.domains.collectAsStateWithLifecycle()
    val quests by viewModel.quests.collectAsStateWithLifecycle()
    val running by viewModel.runningSession.collectAsStateWithLifecycle()

    var mode by remember { mutableStateOf(CalendarMode.DAY) }
    var editing by remember { mutableStateOf<TaskEntity?>(null) }
    var detailTask by remember { mutableStateOf<TaskEntity?>(null) }
    val isCompact = LocalCiWindowSize.current == CiWindowSize.COMPACT

    Column(
        modifier = Modifier.fillMaxSize().padding(if (isCompact) CiSpacing.md else CiSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(CiSpacing.sm + 2.dp),
    ) {
        if (isCompact) {
            CompactCalendarHeader(
                selectedDay = selectedDay,
                mode = mode,
                onPrevious = {
                    when (mode) {
                        CalendarMode.DAY -> viewModel.shift(-1)
                        CalendarMode.WEEK -> viewModel.shift(-7)
                        CalendarMode.MONTH -> viewModel.shiftMonth(-1)
                    }
                },
                onToday = viewModel::today,
                onNext = {
                    when (mode) {
                        CalendarMode.DAY -> viewModel.shift(1)
                        CalendarMode.WEEK -> viewModel.shift(7)
                        CalendarMode.MONTH -> viewModel.shiftMonth(1)
                    }
                },
                onModeSelect = { mode = it },
            )
        } else {
            CiScreenHeader(
                title = "日程",
                subtitle = TimeFormat.date(selectedDay),
                trailing = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(CiSpacing.xs),
                    ) {
                        StepButton(R.drawable.ic_ci_previous, "上一时段") {
                            when (mode) {
                                CalendarMode.DAY -> viewModel.shift(-1)
                                CalendarMode.WEEK -> viewModel.shift(-7)
                                CalendarMode.MONTH -> viewModel.shiftMonth(-1)
                            }
                        }
                        TextButton(onClick = viewModel::today) {
                            Text("回到今天", style = MaterialTheme.typography.labelMedium)
                        }
                        StepButton(R.drawable.ic_ci_next, "下一时段") {
                            when (mode) {
                                CalendarMode.DAY -> viewModel.shift(1)
                                CalendarMode.WEEK -> viewModel.shift(7)
                                CalendarMode.MONTH -> viewModel.shiftMonth(1)
                            }
                        }
                        CiSegmentedControl(
                            options = CalendarMode.entries,
                            selected = mode,
                            label = { it.label },
                            onSelect = { mode = it },
                        )
                    }
                },
            )
        }

        Box(modifier = Modifier.weight(1f)) {
            when (mode) {
                CalendarMode.DAY -> {
                    val daySegments = DaySegments.tasksOn(dayTasks, selectedDay)
                    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(CiSpacing.xs)) {
                        TimelineFeedback(
                            blockers = dayBlockers.size,
                            conflicts = timelineConflictCount(daySegments),
                            unplaced = unplacedTasks,
                        )
                        BlockerSummary(dayBlockers, compact = isCompact)
                        DayTimeline(
                            segments = daySegments,
                            actuals = sessionsToBlocks(
                                sessions = daySessions,
                                tasks = dayTasks,
                                nowMillis = System.currentTimeMillis(),
                                epochDay = selectedDay,
                                quests = quests,
                            ),
                            blockers = dayBlockers,
                            onTaskClick = { detailTask = it },
                            nowMinute = nowMinuteIfToday(selectedDay),
                            showActualTrack = false,
                            scrollIdentity = selectedDay,
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                        )
                    }
                }
                CalendarMode.WEEK -> {
                    val weekStart = LocalDate.ofEpochDay(selectedDay)
                        .with(DayOfWeek.MONDAY).toEpochDay()
                    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(CiSpacing.xs)) {
                        TimelineFeedback(
                            blockers = weekBlockers.values.sumOf { it.size },
                            // 冲突按天统计：不同天的块画在各自列里，同一天内重叠才算冲突
                            conflicts = weekConflictCount(weekTasks, weekStart),
                            unplaced = unplacedTasks,
                        )
                        WeekGrid(
                            weekStartDay = weekStart,
                            tasks = weekTasks,
                            blockersByDay = weekBlockers,
                            onTaskClick = { detailTask = it },
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                        )
                    }
                }
                CalendarMode.MONTH -> MonthHeatmap(
                    selectedDay = selectedDay,
                    sessions = monthSessions,
                    onDayClick = { day ->
                        viewModel.selectedDay.value = day
                        mode = CalendarMode.DAY
                    },
                    compact = isCompact,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            FloatingActionButton(
                onClick = { editing = newTaskDraft(selectedDay) },
                shape = CiShapes.fab,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(if (isCompact) CiSpacing.md else CiSpacing.lg)
                    .size(CiSizes.fab),
            ) {
                CiFunctionIcon(
                    resourceId = R.drawable.ic_ci_add,
                    contentDescription = "新建任务",
                )
            }
        }
    }

    detailTask?.let { task ->
        val focusedMinutes by remember(task.id) { viewModel.focusMinutes(task.id) }
            .collectAsStateWithLifecycle(initialValue = 0)
        TaskDetailDialog(
            task = task,
            focusedMinutes = focusedMinutes,
            isTimerRunning = running != null,
            onStart = { viewModel.startTimer(task); detailTask = null },
            onEdit = { editing = task; detailTask = null },
            onSkip = { viewModel.skipTask(task); detailTask = null },
            onDismiss = { detailTask = null },
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
}

@Composable
private fun CompactCalendarHeader(
    selectedDay: Long,
    mode: CalendarMode,
    onPrevious: () -> Unit,
    onToday: () -> Unit,
    onNext: () -> Unit,
    onModeSelect: (CalendarMode) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(CiSpacing.xs)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "日程", style = com.wsy.ci.core.designsystem.CiTextStyles.pageTitle)
                Text(
                    text = TimeFormat.date(selectedDay),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CiSpacing.xxs),
            ) {
                StepButton(R.drawable.ic_ci_previous, "上一时段", onPrevious)
                TextButton(
                    onClick = onToday,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = CiSpacing.xs,
                        vertical = CiSpacing.xxs,
                    ),
                ) {
                    Text("今天", style = MaterialTheme.typography.labelMedium)
                }
                StepButton(R.drawable.ic_ci_next, "下一时段", onNext)
            }
        }
        CiSegmentedControl(
            options = CalendarMode.entries,
            selected = mode,
            label = { it.label },
            onSelect = onModeSelect,
        )
    }
}

/** 新任务默认落在当前选中日的下一个 15 分钟刻度；临近午夜时使用当天最后一小时。 */
internal fun newTaskDraft(
    selectedDay: Long,
    currentMinute: Int = LocalTime.now().let { it.hour * 60 + it.minute },
): TaskEntity {
    val rounded = (currentMinute + 14) / 15 * 15
    val startMinute = rounded.coerceAtMost(MINUTES_PER_DAY - 60)
    return TaskEntity(
        title = "",
        epochDay = selectedDay,
        startMinute = startMinute,
        endMinute = startMinute + 60,
    )
}

/** 只有查看今天时才画当前时刻线。 */
private fun nowMinuteIfToday(selectedDay: Long): Int? {
    if (selectedDay != LocalDate.now().toEpochDay()) return null
    val now = java.time.LocalTime.now()
    return now.hour * 60 + now.minute
}

@Composable
private fun StepButton(
    @DrawableRes icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
) {
    CiFunctionIcon(
        resourceId = icon,
        contentDescription = contentDescription,
        modifier = Modifier
            .size(CiSizes.fieldHeight)
            .clip(CiShapes.pill)
            .clickable(onClick = onClick)
            .padding(CiSizes.iconTouchPadding),
    )
}

/** 周视图：左侧时刻尺 + 7 列，列头显示「周一 7/27」。 */
@Composable
private fun WeekGrid(
    weekStartDay: Long,
    tasks: List<TaskEntity>,
    blockersByDay: Map<Long, List<BlockerEntity>> = emptyMap(),
    onTaskClick: (TaskEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isCompact = LocalCiWindowSize.current == CiWindowSize.COMPACT
    // 每列按天切片，跨零点的块在两列各占一段
    val segmentsByDay = (0..6).associate { index ->
        val day = weekStartDay + index
        day to DaySegments.tasksOn(tasks, day)
    }
    val today = LocalDate.now().toEpochDay()
    val totalHeight =
        WEEK_HOUR_HEIGHT * (WEEK_END_HOUR - WEEK_START_HOUR) + WEEK_BOTTOM_SLACK

    // 与日视图同一套规矩：首次测出滚动范围后把当前时刻带进视野，只滚一次
    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    var didAutoScroll by remember(weekStartDay) { mutableStateOf(false) }
    LaunchedEffect(weekStartDay, scrollState.maxValue) {
        if (didAutoScroll || scrollState.maxValue == 0) return@LaunchedEffect
        val now = java.time.LocalTime.now()
        val y = WEEK_HOUR_HEIGHT * ((now.hour * 60 + now.minute - WEEK_START_HOUR * 60) / 60f)
        val offsetPx = with(density) { (y - WEEK_SCROLL_LEAD_IN).toPx() }
        scrollState.animateScrollTo(offsetPx.toInt().coerceIn(0, scrollState.maxValue))
        didAutoScroll = true
    }

    Column(
        modifier = modifier
            .clip(CiShapes.field)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CiShapes.field)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(vertical = CiSpacing.xxs),
        ) {
            Box(modifier = Modifier.width(WEEK_RULER_WIDTH))
            for (index in 0..6) {
                val day = weekStartDay + index
                Text(
                    text = if (isCompact) {
                        "${WEEKDAY_LABELS[index]} ${LocalDate.ofEpochDay(day).dayOfMonth}"
                    } else {
                        "周${WEEKDAY_LABELS[index]} ${TimeFormat.shortDate(day)}"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (day == today) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
        Box(modifier = Modifier.verticalScroll(scrollState)) {
            Row(modifier = Modifier.height(totalHeight).fillMaxWidth()) {
                WeekHourRuler()
                for (index in 0..6) {
                    val day = weekStartDay + index
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .leftEdge(MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        (segmentsByDay[day] ?: emptyList()).forEach { segment ->
                            WeekTaskBlock(
                                segment = segment,
                                onClick = { onTaskClick(segment.task) },
                            )
                        }
                        // 后绘制锁定块，避免与任务重叠时被任务背景完全盖住。
                        (blockersByDay[day] ?: emptyList()).forEach { blocker ->
                            WeekBlocker(blocker)
                        }
                    }
                }
            }
        }
    }
}

/** 周视图中的占位事件，与日视图共用锁定/不结算语义。 */
@Composable
private fun WeekBlocker(blocker: BlockerEntity) {
    val minuteHeight = WEEK_HOUR_HEIGHT / 60f
    val y = minuteHeight * blocker.startMinute
    val height = (minuteHeight * (blocker.endMinute - blocker.startMinute))
        .coerceAtLeast(WEEK_MIN_BLOCK_HEIGHT)
    Row(
        modifier = Modifier
            .offset(y = y)
            .height(height)
            .fillMaxWidth()
            .padding(horizontal = CiSpacing.xxs)
            .clip(CiShapes.weekBlock)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CiShapes.weekBlock),
    ) {
        Box(
            modifier = Modifier
                .width(2.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.outline)
        )
        Text(
            text = blocker.title.ifBlank { "不可安排" },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = CiSpacing.xxs, vertical = CiSpacing.xxs),
        )
    }
}

/** 日视图锁定原因摘要：不拦截任务点击，且在重叠时仍保持 blocker 文案可见。 */
@Composable
private fun BlockerSummary(blockers: List<BlockerEntity>, compact: Boolean = false) {
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
            text = if (compact) {
                "${blockers.size} 个锁定时段已显示在时间线中"
            } else {
                "锁定时段（任务不会安排在这里）"
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!compact) {
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
}

@Composable
private fun WeekHourRuler() {
    Box(modifier = Modifier.width(WEEK_RULER_WIDTH).fillMaxHeight()) {
        for (hour in WEEK_START_HOUR..WEEK_END_HOUR) {
            Text(
                text = "%02d".format(hour),
                style = MaterialTheme.typography.labelSmall.tabularNums(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                modifier = Modifier
                    .offset(y = WEEK_HOUR_HEIGHT * (hour - WEEK_START_HOUR))
                    .fillMaxWidth()
                    .padding(end = CiSpacing.xxs),
            )
        }
    }
}

@Composable
private fun WeekTaskBlock(segment: TaskSegment, onClick: () -> Unit) {
    val colors = CiTheme.colors.taskBlock(segment.task.status)
    val minuteHeight = WEEK_HOUR_HEIGHT / 60f
    val y = minuteHeight * (segment.startMinute - WEEK_START_HOUR * 60).coerceAtLeast(0)
    val height = (minuteHeight * (segment.endMinute - segment.startMinute))
        .coerceAtLeast(WEEK_MIN_BLOCK_HEIGHT)

    Row(
        modifier = Modifier
            .offset(y = y)
            .height(height)
            .fillMaxWidth()
            .padding(horizontal = CiSpacing.xxs)
            .alpha(colors.alpha)
            .clip(CiShapes.weekBlock)
            .background(colors.container)
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .width(2.dp)
                .fillMaxHeight()
                .background(colors.accent)
        )
        // 跨零点延续过来的那一段只占位，标题在开工的那天已经写过了
        if (!segment.isContinuation) {
            Text(
                text = segment.task.title,
                style = MaterialTheme.typography.labelSmall,
                color = colors.content,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (colors.strikethrough) TextDecoration.LineThrough else null,
                modifier = Modifier.padding(horizontal = 5.dp, vertical = 3.dp),
            )
        }
    }
}

/** 月视图：7 列热力日历，格内日期 + 专注时长，色阶走电青 focusHeat。 */
@Composable
private fun MonthHeatmap(
    selectedDay: Long,
    sessions: List<SessionEntity>,
    onDayClick: (Long) -> Unit,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val scale = CiTheme.colors.focusHeat
    val date = LocalDate.ofEpochDay(selectedDay)
    val firstDay = date.withDayOfMonth(1)
    val daysInMonth = date.lengthOfMonth()
    val leadingBlanks = firstDay.dayOfWeek.value - 1
    val minutesByDay = sessions
        .filter { it.endAt != null }
        .groupBy { TimeFormat.millisToEpochDay(it.startAt) }
        .mapValues { (_, list) -> list.sumOf { ((it.endAt!! - it.startAt) / 60_000).toInt() } }
    val maxMinutes = minutesByDay.values.maxOrNull()?.coerceAtLeast(1) ?: 1
    val today = LocalDate.now().toEpochDay()

    Column(
        modifier = if (compact) modifier.verticalScroll(rememberScrollState()) else modifier,
        verticalArrangement = Arrangement.spacedBy(CiSpacing.xs),
    ) {
        Text(
            text = "${date.year} 年 ${date.monthValue} 月 · " +
                "共专注 ${TimeFormat.duration(minutesByDay.values.sum())}",
            style = MaterialTheme.typography.titleSmall,
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            WEEKDAY_LABELS.forEach { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        val cells = List(leadingBlanks) { null } + (1..daysInMonth).toList()
        cells.chunked(7).forEach { week ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(CiSpacing.xs),
            ) {
                week.forEach { dayOfMonth ->
                    if (dayOfMonth == null) {
                        Box(modifier = Modifier.weight(1f))
                    } else {
                        val epochDay = firstDay.plusDays((dayOfMonth - 1).toLong()).toEpochDay()
                        val minutes = minutesByDay[epochDay] ?: 0
                        MonthCell(
                            dayOfMonth = dayOfMonth,
                            minutes = minutes,
                            level = scale.levelOf(minutes, maxMinutes),
                            isToday = epochDay == today,
                            onClick = { onDayClick(epochDay) },
                            compact = compact,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                repeat(7 - week.size) { Box(modifier = Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun MonthCell(
    dayOfMonth: Int,
    minutes: Int,
    level: Int,
    isToday: Boolean,
    onClick: () -> Unit,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val scale = CiTheme.colors.focusHeat
    Column(
        modifier = modifier
            .height(if (compact) COMPACT_MONTH_CELL_HEIGHT else MONTH_CELL_HEIGHT)
            .alpha(if (level == 0) MONTH_EMPTY_ALPHA else 1f)
            .clip(CiShapes.monthCell)
            .background(scale.container(level))
            .then(
                if (isToday) {
                    Modifier.border(2.dp, CiTheme.colors.currentTimeLine, CiShapes.monthCell)
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onClick)
            .padding(CiSpacing.xs),
        verticalArrangement = Arrangement.spacedBy(CiSpacing.xxs),
    ) {
        Text(
            text = "$dayOfMonth",
            style = MaterialTheme.typography.titleSmall.tabularNums(),
            color = scale.content(level),
        )
        if (minutes > 0) {
            Text(
                text = TimeFormat.duration(minutes),
                style = MaterialTheme.typography.labelSmall,
                color = scale.content(level),
            )
        }
    }
}

/** 周视图列之间的 1dp 分隔线，只画左缘。 */
private fun Modifier.leftEdge(color: Color) = drawBehind {
    drawLine(
        color = color,
        start = Offset.Zero,
        end = Offset(0f, size.height),
        strokeWidth = 1.dp.toPx(),
    )
}
