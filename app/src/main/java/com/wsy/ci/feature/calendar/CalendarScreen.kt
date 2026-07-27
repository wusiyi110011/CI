package com.wsy.ci.feature.calendar

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wsy.ci.CiApp
import com.wsy.ci.core.db.TaskEntity
import com.wsy.ci.core.db.TaskStatus
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

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = {
                when (mode) {
                    CalendarMode.DAY -> viewModel.shift(-1)
                    CalendarMode.WEEK -> viewModel.shift(-7)
                    CalendarMode.MONTH -> viewModel.shiftMonth(-1)
                }
            }) { Text("◀") }
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(TimeFormat.date(selectedDay), style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = viewModel::today) { Text("回到今天") }
            }
            IconButton(onClick = {
                when (mode) {
                    CalendarMode.DAY -> viewModel.shift(1)
                    CalendarMode.WEEK -> viewModel.shift(7)
                    CalendarMode.MONTH -> viewModel.shiftMonth(1)
                }
            }) { Text("▶") }
            SingleChoiceSegmentedButtonRow {
                CalendarMode.entries.forEachIndexed { i, m ->
                    SegmentedButton(
                        selected = mode == m,
                        onClick = { mode = m },
                        shape = SegmentedButtonDefaults.itemShape(i, CalendarMode.entries.size),
                    ) { Text(m.label) }
                }
            }
        }
        when (mode) {
            CalendarMode.DAY -> DayTimeline(
                tasks = dayTasks,
                actuals = sessionsToBlocks(daySessions, System.currentTimeMillis()),
                onTaskClick = { editing = it },
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

private const val WEEK_START_HOUR = 6
private const val WEEK_END_HOUR = 24
private val WEEK_HOUR_HEIGHT = 48.dp

@Composable
private fun WeekGrid(
    weekStartDay: Long,
    tasks: List<TaskEntity>,
    onTaskClick: (TaskEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val byDay = tasks.groupBy { it.epochDay }
    Column(modifier = modifier) {
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
            for (i in 0..6) {
                val day = weekStartDay + i
                val isToday = day == LocalDate.now().toEpochDay()
                Text(
                    text = "${listOf("一", "二", "三", "四", "五", "六", "日")[i]} ${TimeFormat.shortDate(day)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
            }
        }
        val totalHeight = WEEK_HOUR_HEIGHT * (WEEK_END_HOUR - WEEK_START_HOUR)
        Box(modifier = Modifier.verticalScroll(rememberScrollState()).height(totalHeight)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (i in 0..6) {
                    val day = weekStartDay + i
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(totalHeight)
                            .padding(horizontal = 1.dp)
                            .background(
                                if (day == LocalDate.now().toEpochDay())
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                                else MaterialTheme.colorScheme.surface
                            )
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
private fun MonthHeatmap(
    selectedDay: Long,
    sessions: List<com.wsy.ci.core.db.SessionEntity>,
    onDayClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val date = LocalDate.ofEpochDay(selectedDay)
    val firstDay = date.withDayOfMonth(1)
    val daysInMonth = date.lengthOfMonth()
    val leadingBlanks = firstDay.dayOfWeek.value - 1
    val minutesByDay = sessions
        .filter { it.endAt != null }
        .groupBy { TimeFormat.millisToEpochDay(it.startAt) }
        .mapValues { (_, list) -> list.sumOf { ((it.endAt!! - it.startAt) / 60_000).toInt() } }
    val maxMin = minutesByDay.values.maxOrNull()?.coerceAtLeast(1) ?: 1
    val today = LocalDate.now().toEpochDay()

    Column(modifier = modifier.padding(top = 12.dp)) {
        Text(
            "${date.year}年${date.monthValue}月 · 专注热力图（共 ${TimeFormat.duration(minutesByDay.values.sum())}）",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
            listOf("一", "二", "三", "四", "五", "六", "日").forEach {
                Text(
                    it,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        val cells = List(leadingBlanks) { null } + (1..daysInMonth).toList()
        cells.chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { dayOfMonth ->
                    Box(modifier = Modifier.weight(1f).padding(2.dp)) {
                        if (dayOfMonth != null) {
                            val epochDay = firstDay.plusDays((dayOfMonth - 1).toLong()).toEpochDay()
                            val minutes = minutesByDay[epochDay] ?: 0
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(64.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        MaterialTheme.colorScheme.primary.copy(
                                            alpha = if (minutes == 0) 0.05f else 0.2f + 0.8f * minutes / maxMin
                                        )
                                    )
                                    .clickable { onDayClick(epochDay) }
                                    .padding(4.dp),
                            ) {
                                Text(
                                    "$dayOfMonth",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (epochDay == today) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurface,
                                )
                                if (minutes > 0) {
                                    Text(
                                        TimeFormat.duration(minutes),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
                repeat(7 - week.size) { Box(modifier = Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun WeekTaskBlock(task: TaskEntity, onClick: () -> Unit) {
    val minuteHeight = WEEK_HOUR_HEIGHT / 60f
    val y = minuteHeight * (task.startMinute - WEEK_START_HOUR * 60).coerceAtLeast(0)
    val height = (minuteHeight * (task.endMinute - task.startMinute)).coerceAtLeast(20.dp)
    val done = task.status == TaskStatus.DONE
    Text(
        text = task.title,
        style = MaterialTheme.typography.labelSmall,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        color = if (done) MaterialTheme.colorScheme.onSurfaceVariant
        else MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier
            .offset(y = y)
            .height(height)
            .fillMaxWidth()
            .padding(vertical = 1.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(
                if (done) MaterialTheme.colorScheme.surfaceVariant
                else MaterialTheme.colorScheme.primaryContainer
            )
            .clickable(onClick = onClick)
            .padding(2.dp),
    )
}
