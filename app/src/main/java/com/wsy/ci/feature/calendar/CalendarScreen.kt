package com.wsy.ci.feature.calendar

import android.app.Application
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wsy.ci.CiApp
import com.wsy.ci.core.db.SessionEntity
import com.wsy.ci.core.db.TaskEntity
import com.wsy.ci.core.designsystem.CiShapes
import com.wsy.ci.core.designsystem.CiSizes
import com.wsy.ci.core.designsystem.CiSpacing
import com.wsy.ci.core.designsystem.CiScreenHeader
import com.wsy.ci.core.designsystem.CiSegmentedControl
import com.wsy.ci.core.designsystem.CiTheme
import com.wsy.ci.core.designsystem.tabularNums
import com.wsy.ci.core.util.TimeFormat
import com.wsy.ci.feature.today.DayTimeline
import com.wsy.ci.feature.today.TaskEditorDialog
import com.wsy.ci.feature.today.sessionsToBlocks
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
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

    val dayTasks = selectedDay.flatMapLatest { db.taskDao().observeByDay(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val daySessions = selectedDay.flatMapLatest {
        db.sessionDao().observeByTimeRange(TimeFormat.dayStartMillis(it), TimeFormat.dayEndMillis(it))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val weekTasks = selectedDay.flatMapLatest {
        val start = weekStart(it)
        db.taskDao().observeByRange(start, start + 6)
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

    val quests = db.questDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun shift(days: Long) { selectedDay.value += days }

    fun shiftMonth(delta: Long) {
        selectedDay.value = LocalDate.ofEpochDay(selectedDay.value).plusMonths(delta).toEpochDay()
    }
    fun today() { selectedDay.value = LocalDate.now().toEpochDay() }

    fun saveTask(task: TaskEntity) {
        viewModelScope.launch {
            if (task.id == 0L) db.taskDao().insert(task) else db.taskDao().update(task)
        }
    }

    fun deleteTask(task: TaskEntity) {
        viewModelScope.launch { db.taskDao().delete(task) }
    }
}

/** 周视图：每小时行高，19 行铺满 06:00–24:00。 */
private val WEEK_HOUR_HEIGHT: Dp = 36.dp
private const val WEEK_START_HOUR = 6
private const val WEEK_END_HOUR = 24

/** 周视图左侧时刻尺宽度，比日视图略窄以给 7 列腾空间。 */
private val WEEK_RULER_WIDTH: Dp = 44.dp

/** 周视图任务块最小高度。 */
private val WEEK_MIN_BLOCK_HEIGHT: Dp = 30.dp

/** 月视图日历格高度。 */
private val MONTH_CELL_HEIGHT: Dp = 104.dp

/** 月视图无专注日的整格淡出程度。 */
private const val MONTH_EMPTY_ALPHA = 0.6f

private val WEEKDAY_LABELS = listOf("一", "二", "三", "四", "五", "六", "日")

@Composable
fun CalendarScreen(viewModel: CalendarViewModel = viewModel()) {
    val selectedDay by viewModel.selectedDay.collectAsState()
    val dayTasks by viewModel.dayTasks.collectAsState()
    val daySessions by viewModel.daySessions.collectAsState()
    val weekTasks by viewModel.weekTasks.collectAsState()
    val monthSessions by viewModel.monthMinutes.collectAsState()
    val domains by viewModel.domains.collectAsState()
    val quests by viewModel.quests.collectAsState()

    var mode by remember { mutableStateOf(CalendarMode.DAY) }
    var editing by remember { mutableStateOf<TaskEntity?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().padding(CiSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(CiSpacing.sm + 2.dp),
    ) {
        CiScreenHeader(
            title = "日程",
            subtitle = TimeFormat.date(selectedDay),
            trailing = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(CiSpacing.xs),
                ) {
                    StepButton("◀") {
                        when (mode) {
                            CalendarMode.DAY -> viewModel.shift(-1)
                            CalendarMode.WEEK -> viewModel.shift(-7)
                            CalendarMode.MONTH -> viewModel.shiftMonth(-1)
                        }
                    }
                    TextButton(onClick = viewModel::today) {
                        Text("回到今天", style = MaterialTheme.typography.labelMedium)
                    }
                    StepButton("▶") {
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

        when (mode) {
            CalendarMode.DAY -> DayTimeline(
                tasks = dayTasks,
                actuals = sessionsToBlocks(daySessions, dayTasks, System.currentTimeMillis()),
                onTaskClick = { editing = it },
                nowMinute = nowMinuteIfToday(selectedDay),
                showActualTrack = false,
                modifier = Modifier.weight(1f),
            )
            CalendarMode.WEEK -> WeekGrid(
                weekStartDay = LocalDate.ofEpochDay(selectedDay).with(DayOfWeek.MONDAY).toEpochDay(),
                tasks = weekTasks,
                onTaskClick = { editing = it },
                modifier = Modifier.weight(1f),
            )
            CalendarMode.MONTH -> MonthHeatmap(
                selectedDay = selectedDay,
                sessions = monthSessions,
                onDayClick = { day ->
                    viewModel.selectedDay.value = day
                    mode = CalendarMode.DAY
                },
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
            onDelete = viewModel::deleteTask,
            onDismiss = { editing = null },
            onCreateDomain = { _, _ -> },
        )
    }
}

/** 只有查看今天时才画当前时刻线。 */
private fun nowMinuteIfToday(selectedDay: Long): Int? {
    if (selectedDay != LocalDate.now().toEpochDay()) return null
    val now = java.time.LocalTime.now()
    return now.hour * 60 + now.minute
}

@Composable
private fun StepButton(glyph: String, onClick: () -> Unit) {
    Text(
        text = glyph,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(CiShapes.pill)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

/** 周视图：左侧时刻尺 + 7 列，列头显示「周一 7/27」。 */
@Composable
private fun WeekGrid(
    weekStartDay: Long,
    tasks: List<TaskEntity>,
    onTaskClick: (TaskEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val byDay = tasks.groupBy { it.epochDay }
    val today = LocalDate.now().toEpochDay()
    val totalHeight = WEEK_HOUR_HEIGHT * (WEEK_END_HOUR - WEEK_START_HOUR)

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
                    text = "周${WEEKDAY_LABELS[index]} ${TimeFormat.shortDate(day)}",
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
        Box(modifier = Modifier.verticalScroll(rememberScrollState())) {
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
                        (byDay[day] ?: emptyList()).forEach { task ->
                            WeekTaskBlock(task = task, onClick = { onTaskClick(task) })
                        }
                    }
                }
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
private fun WeekTaskBlock(task: TaskEntity, onClick: () -> Unit) {
    val colors = CiTheme.colors.taskBlock(task.status)
    val minuteHeight = WEEK_HOUR_HEIGHT / 60f
    val y = minuteHeight * (task.startMinute - WEEK_START_HOUR * 60).coerceAtLeast(0)
    val height = (minuteHeight * (task.endMinute - task.startMinute))
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
        Text(
            text = task.title,
            style = MaterialTheme.typography.labelSmall,
            color = colors.content,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textDecoration = if (colors.strikethrough) TextDecoration.LineThrough else null,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 3.dp),
        )
    }
}

/** 月视图：7 列热力日历，格内日期 + 专注时长，色阶走电青 focusHeat。 */
@Composable
private fun MonthHeatmap(
    selectedDay: Long,
    sessions: List<SessionEntity>,
    onDayClick: (Long) -> Unit,
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

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(CiSpacing.xs)) {
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
    modifier: Modifier = Modifier,
) {
    val scale = CiTheme.colors.focusHeat
    Column(
        modifier = modifier
            .height(MONTH_CELL_HEIGHT)
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
