package com.wsy.ci.feature.today

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wsy.ci.core.db.QuestEntity
import com.wsy.ci.core.db.SessionEntity
import com.wsy.ci.core.db.TaskEntity
import com.wsy.ci.core.db.TaskStatus
import com.wsy.ci.core.designsystem.CiShapes
import com.wsy.ci.core.designsystem.CiSizes
import com.wsy.ci.core.designsystem.CiSpacing
import com.wsy.ci.core.designsystem.CiTextStyles
import com.wsy.ci.core.designsystem.CiTheme
import com.wsy.ci.core.designsystem.tabularNums
import com.wsy.ci.core.designsystem.TaskBlockColors
import com.wsy.ci.core.timeline.Span
import com.wsy.ci.core.timeline.TaskLanes
import com.wsy.ci.core.util.TimeFormat

/** 时间线上的一个实际记录块（由 session 换算而来）。 */
data class ActualBlock(
    val title: String,
    val startMinute: Int,
    val endMinute: Int,
    val running: Boolean,
    val rewardCi: Long,
)

/** 每分钟 1.1dp，即每小时 66dp，与设计画布的纵向比例一致。 */
private val HOUR_HEIGHT: Dp = 66.dp
private const val MINUTES_PER_HOUR = 60f

/** 任务块最小可读高度，短任务不至于挤成一条线。 */
private val MIN_BLOCK_HEIGHT: Dp = 44.dp

/** 相邻块之间留出的呼吸缝。 */
private val BLOCK_GAP: Dp = 3.dp

/** 自动滚动时，当前时刻上方预留的一段上下文高度。 */
private val SCROLL_LEAD_IN: Dp = 120.dp

/** 计划轨 : 实际轨 = 55 : 45。 */
private const val PLAN_TRACK_WEIGHT = 0.55f
private const val ACTUAL_TRACK_WEIGHT = 0.45f

/**
 * 双轨时间线：左轨计划块（可点击），右轨实际记录块，左侧 48dp 时刻尺。
 * 高度按分钟线性映射，默认渲染 5:00–24:00。
 *
 * [showActualTrack] 为 false 时退化为单轨满宽（日程屏的「日」视图）。
 */
@Composable
fun DayTimeline(
    tasks: List<TaskEntity>,
    actuals: List<ActualBlock>,
    onTaskClick: (TaskEntity) -> Unit,
    modifier: Modifier = Modifier,
    startHour: Int = 5,
    endHour: Int = 24,
    nowMinute: Int? = null,
    showActualTrack: Boolean = true,
) {
    val totalHeight = HOUR_HEIGHT * (endHour - startHour)
    fun yOf(minute: Int): Dp =
        HOUR_HEIGHT * ((minute - startHour * 60).coerceAtLeast(0) / MINUTES_PER_HOUR)

    fun heightOf(start: Int, end: Int): Dp =
        (yOf(end) - yOf(start) - BLOCK_GAP).coerceAtLeast(MIN_BLOCK_HEIGHT)

    // 首次测量出滚动范围后，把当前时刻滚到视野上沿偏下一点，避免一进来只看到清晨的空白。
    // 只滚一次，之后不再和用户的手动滚动抢方向。
    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    var didAutoScroll by remember { mutableStateOf(false) }
    LaunchedEffect(scrollState.maxValue, nowMinute) {
        val target = nowMinute ?: return@LaunchedEffect
        if (didAutoScroll || scrollState.maxValue == 0) return@LaunchedEffect
        val offsetPx = with(density) { (yOf(target) - SCROLL_LEAD_IN).toPx() }
        scrollState.animateScrollTo(offsetPx.toInt().coerceIn(0, scrollState.maxValue))
        didAutoScroll = true
    }

    Column(
        modifier = modifier
            .clip(CiShapes.field)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CiShapes.field)
    ) {
        if (showActualTrack) TrackHeader()

        Box(modifier = Modifier.verticalScroll(scrollState)) {
            Row(modifier = Modifier.height(totalHeight).fillMaxWidth()) {
                HourRuler(startHour = startHour, endHour = endHour)
                TrackColumn(
                    weight = if (showActualTrack) PLAN_TRACK_WEIGHT else 1f,
                    dashedLeftEdge = true,
                ) {
                    // 时段重叠的任务并排画，互不遮挡；不重叠时照旧独占整轨
                    val lanes = remember(tasks) {
                        TaskLanes.assign(tasks.map { Span(it.startMinute, it.endMinute) })
                    }
                    val trackWidth = maxWidth
                    tasks.forEachIndexed { index, task ->
                        val lane = lanes[index]
                        PlannedBlock(
                            task = task,
                            x = trackWidth * lane.lane / lane.laneCount,
                            y = yOf(task.startMinute),
                            width = trackWidth / lane.laneCount,
                            height = heightOf(task.startMinute, task.endMinute),
                            onClick = { onTaskClick(task) },
                        )
                    }
                }
                if (showActualTrack) {
                    TrackDivider()
                    TrackColumn(weight = ACTUAL_TRACK_WEIGHT, dashedLeftEdge = false) {
                        actuals.forEach { block ->
                            ActualBlockView(
                                block = block,
                                y = yOf(block.startMinute),
                                height = heightOf(block.startMinute, block.endMinute),
                            )
                        }
                    }
                }
            }
            nowMinute?.takeIf { it in startHour * 60 until endHour * 60 }?.let { now ->
                CurrentTimeLine(minute = now, y = yOf(now))
            }
        }
    }
}

/** 「计划 / 实际」列头，随内容区固定在时间线顶部。 */
@Composable
private fun TrackHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(vertical = CiSpacing.xxs + 2.dp),
    ) {
        Box(modifier = Modifier.width(CiSizes.timeRulerWidth))
        TrackHeaderLabel(text = "计划", weight = PLAN_TRACK_WEIGHT)
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(CiSpacing.md)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
        TrackHeaderLabel(text = "实际", weight = ACTUAL_TRACK_WEIGHT)
    }
}

@Composable
private fun RowScope.TrackHeaderLabel(text: String, weight: Float) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.weight(weight),
    )
}

/** 左侧时刻尺：每小时一个刻度标签。 */
@Composable
private fun HourRuler(startHour: Int, endHour: Int) {
    Box(modifier = Modifier.width(CiSizes.timeRulerWidth).fillMaxHeight()) {
        for (hour in startHour..endHour) {
            Text(
                text = "%02d:00".format(hour),
                style = MaterialTheme.typography.labelSmall.tabularNums(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                modifier = Modifier
                    .offset(y = HOUR_HEIGHT * (hour - startHour))
                    .fillMaxWidth()
                    .padding(end = CiSpacing.xxs + 2.dp),
            )
        }
    }
}

/**
 * 一条轨道。[dashedLeftEdge] 为 true 时在左缘画虚线，对应设计稿的
 * `border-left: 1px dashed`（Compose 无 dashed border，用 dashed path effect 画）。
 *
 * 用 `BoxWithConstraints` 是为了让内容拿得到轨宽——计划轨要按重叠分栏切宽度。
 */
@Composable
private fun RowScope.TrackColumn(
    weight: Float,
    dashedLeftEdge: Boolean,
    content: @Composable BoxWithConstraintsScope.() -> Unit,
) {
    val edgeColor = MaterialTheme.colorScheme.outlineVariant
    BoxWithConstraints(
        modifier = Modifier
            .weight(weight)
            .fillMaxHeight()
            .then(if (dashedLeftEdge) Modifier.dashedLeftEdge(edgeColor) else Modifier),
        content = content,
    )
}

private fun Modifier.dashedLeftEdge(color: Color) = drawBehind {
    drawLine(
        color = color,
        start = Offset.Zero,
        end = Offset(0f, size.height),
        strokeWidth = 1.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
    )
}

@Composable
private fun TrackDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}

/** 当前时刻：电青实线 2dp + 左侧时间 pill。 */
@Composable
private fun CurrentTimeLine(minute: Int, y: Dp) {
    val lineColor = CiTheme.colors.currentTimeLine
    Box(modifier = Modifier.offset(y = y).fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(CiSizes.currentTimeLine)
                .background(lineColor)
        )
        Text(
            text = TimeFormat.minuteOfDay(minute),
            style = MaterialTheme.typography.labelSmall.tabularNums(),
            color = MaterialTheme.colorScheme.onTertiary,
            modifier = Modifier
                .offset(x = 2.dp, y = (-11).dp)
                .clip(CiShapes.pill)
                .background(lineColor)
                .padding(horizontal = CiSpacing.xs, vertical = 2.dp),
        )
    }
}

@Composable
private fun PlannedBlock(
    task: TaskEntity,
    x: Dp,
    y: Dp,
    width: Dp,
    height: Dp,
    onClick: () -> Unit,
) {
    val colors = CiTheme.colors.taskBlock(task.status)
    TaskBlock(
        colors = colors,
        x = x,
        y = y,
        width = width,
        height = height,
        title = (if (task.locked && task.status == TaskStatus.PLANNED) "🔒 " else "") + task.title,
        caption = "${TimeFormat.minuteOfDay(task.startMinute)}–${TimeFormat.minuteOfDay(task.endMinute)}" +
            " · ${task.difficulty.label}",
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun ActualBlockView(block: ActualBlock, y: Dp, height: Dp) {
    val colors = if (block.running) CiTheme.colors.taskRunning else CiTheme.colors.taskDone
    val caption = if (block.running) {
        "专注中…"
    } else {
        TimeFormat.duration(block.endMinute - block.startMinute) +
            if (block.rewardCi > 0) " · +${block.rewardCi} CI" else ""
    }
    TaskBlock(
        colors = colors,
        y = y,
        height = height,
        title = block.title,
        caption = caption,
    )
}

/**
 * 任务块通用外观：圆角 8、左侧 3dp 强调条、标题 + 副标题。
 * [width] 为 null 时占满整轨（实际轨不分栏）。
 */
@Composable
private fun TaskBlock(
    colors: TaskBlockColors,
    y: Dp,
    height: Dp,
    title: String,
    caption: String,
    x: Dp = 0.dp,
    width: Dp? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = Modifier
            .offset(x = x, y = y)
            .height(height)
            .then(if (width != null) Modifier.width(width) else Modifier.fillMaxWidth())
            .padding(horizontal = CiSpacing.xs)
            .alpha(colors.alpha)
            .clip(CiShapes.taskBlock)
            .background(colors.container)
            .then(modifier),
    ) {
        Box(
            modifier = Modifier
                .width(CiSizes.blockAccent)
                .fillMaxHeight()
                .background(colors.accent)
        )
        Column(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = CiSpacing.xxs + 2.dp),
            verticalArrangement = Arrangement.Top,
        ) {
            Text(
                text = title,
                style = CiTextStyles.blockTitle,
                color = colors.content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (colors.strikethrough) TextDecoration.LineThrough else null,
            )
            Text(
                text = caption,
                style = CiTextStyles.blockCaption,
                color = colors.content.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * sessions → 时间线块。标题按「具体任务 → 任务线 → 自由专注」依次回填：
 * 支线打卡不挂任务，只有 questId，落到任务线名上才不会一片「自由专注」。
 */
fun sessionsToBlocks(
    sessions: List<SessionEntity>,
    tasks: List<TaskEntity>,
    nowMillis: Long,
    quests: List<QuestEntity> = emptyList(),
): List<ActualBlock> = sessions.map { session ->
    val startMinute = TimeFormat.millisToMinuteOfDay(session.startAt)
    val end = session.endAt ?: nowMillis
    val task = tasks.firstOrNull { it.id == session.taskId }
    val quest = quests.firstOrNull { it.id == (task?.questId ?: session.questId) }
    ActualBlock(
        title = task?.title ?: quest?.title ?: "自由专注",
        startMinute = startMinute,
        endMinute = TimeFormat.millisToMinuteOfDay(end).coerceAtLeast(startMinute + 1),
        running = session.endAt == null,
        rewardCi = session.rewardCi,
    )
}
