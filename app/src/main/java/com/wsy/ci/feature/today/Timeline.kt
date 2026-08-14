package com.wsy.ci.feature.today

import androidx.annotation.DrawableRes
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
import androidx.compose.foundation.layout.size
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
import com.wsy.ci.R
import com.wsy.ci.core.db.QuestEntity
import com.wsy.ci.core.db.SessionEntity
import com.wsy.ci.core.db.TaskEntity
import com.wsy.ci.core.db.TaskStatus
import com.wsy.ci.core.designsystem.CiShapes
import com.wsy.ci.core.designsystem.CiFunctionIcon
import com.wsy.ci.core.designsystem.CiSizes
import com.wsy.ci.core.designsystem.CiSpacing
import com.wsy.ci.core.designsystem.CiTextStyles
import com.wsy.ci.core.designsystem.CiTheme
import com.wsy.ci.core.designsystem.tabularNums
import com.wsy.ci.core.designsystem.TaskBlockColors
import com.wsy.ci.core.timeline.DaySegments
import com.wsy.ci.core.timeline.Span
import com.wsy.ci.core.timeline.TaskLanes
import com.wsy.ci.core.timeline.TaskSegment
import com.wsy.ci.core.util.TimeFormat

/**
 * 时间线上的一个实际记录块（由 session 换算而来）。
 * [isContinuation] 为 true 表示这是前一天的专注延续过来的尾巴，只画占用框不写标题。
 */
data class ActualBlock(
    val sessionId: Long,
    val title: String,
    val startMinute: Int,
    val endMinute: Int,
    val running: Boolean,
    val rewardCi: Long,
    val isContinuation: Boolean = false,
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

/**
 * 窗口末尾额外留出的高度：末班车任务（如 23:50 起）按最小块高往下撑，
 * 末尾整点的刻度标签也要地方落笔，不留这一段就会被容器裁掉。
 */
private val BOTTOM_SLACK: Dp = MIN_BLOCK_HEIGHT

/** 计划轨 : 实际轨 = 55 : 45。 */
private const val PLAN_TRACK_WEIGHT = 0.55f
private const val ACTUAL_TRACK_WEIGHT = 0.45f

/**
 * 双轨时间线：左轨计划块（可点击），右轨实际记录块，左侧 48dp 时刻尺。
 * 高度按分钟线性映射，整天 0:00–24:00 全铺，深夜与凌晨的任务照样有自己的位置；
 * 进屏时自动滚到当前时刻，不会一开门就是一片空夜。
 *
 * 画的是「这一天」的片段（见 [DaySegments]）：跨零点的块在两天各画一段，
 * 次日那段只有占用框、不重复写标题。
 *
 * [showActualTrack] 为 false 时退化为单轨满宽（日程屏的「日」视图）。
 */
@Composable
fun DayTimeline(
    segments: List<TaskSegment>,
    actuals: List<ActualBlock>,
    onTaskClick: (TaskEntity) -> Unit,
    modifier: Modifier = Modifier,
    onActualClick: (ActualBlock) -> Unit = {},
    startHour: Int = 0,
    endHour: Int = 24,
    nowMinute: Int? = null,
    showActualTrack: Boolean = true,
) {
    val totalHeight = HOUR_HEIGHT * (endHour - startHour) + BOTTOM_SLACK
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
                    val lanes = remember(segments) {
                        TaskLanes.assign(segments.map { Span(it.startMinute, it.endMinute) })
                    }
                    val trackWidth = maxWidth
                    segments.forEachIndexed { index, segment ->
                        val lane = lanes[index]
                        PlannedBlock(
                            segment = segment,
                            x = trackWidth * lane.lane / lane.laneCount,
                            y = yOf(segment.startMinute),
                            width = trackWidth / lane.laneCount,
                            height = heightOf(segment.startMinute, segment.endMinute),
                            onClick = { onTaskClick(segment.task) },
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
                                onClick = { onActualClick(block) },
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
    segment: TaskSegment,
    x: Dp,
    y: Dp,
    width: Dp,
    height: Dp,
    onClick: () -> Unit,
) {
    val task = segment.task
    val colors = CiTheme.colors.taskBlock(task.status)
    // 跨零点延续过来的那一段只占位：标题在开工的那天已经写过了
    TaskBlock(
        colors = colors,
        x = x,
        y = y,
        width = width,
        height = height,
        title = if (segment.isContinuation) "" else task.title,
        leadingIcon = if (
            !segment.isContinuation && task.locked && task.status == TaskStatus.PLANNED
        ) {
            R.drawable.ic_ci_lock
        } else {
            null
        },
        caption = if (segment.isContinuation) {
            ""
        } else {
            "${TimeFormat.minuteOfDay(task.startMinute)}–${TimeFormat.minuteOfDay(task.endMinute)}" +
                " · ${task.difficulty.label}"
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun ActualBlockView(block: ActualBlock, y: Dp, height: Dp, onClick: () -> Unit) {
    val colors = if (block.running) CiTheme.colors.taskRunning else CiTheme.colors.taskDone
    val caption = when {
        block.isContinuation -> ""
        block.running -> "专注中…"
        else -> TimeFormat.duration(block.endMinute - block.startMinute) +
            if (block.rewardCi > 0) " · +${block.rewardCi} CI" else ""
    }
    TaskBlock(
        colors = colors,
        y = y,
        height = height,
        title = if (block.isContinuation) "" else block.title,
        caption = caption,
        modifier = Modifier.clickable(onClick = onClick),
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
    @DrawableRes leadingIcon: Int? = null,
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
        // 文案为空的是跨天延续段，只留下框本身占住时段
        Column(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = CiSpacing.xxs + 2.dp),
            verticalArrangement = Arrangement.Top,
        ) {
            if (title.isNotEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(CiSpacing.xxs),
                ) {
                    leadingIcon?.let {
                        CiFunctionIcon(
                            resourceId = it,
                            contentDescription = "已锁定",
                            modifier = Modifier.size(CiSizes.compactIcon),
                        )
                    }
                    Text(
                        text = title,
                        style = CiTextStyles.blockTitle,
                        color = colors.content,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textDecoration = if (colors.strikethrough) TextDecoration.LineThrough else null,
                    )
                }
            }
            if (caption.isNotEmpty()) {
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
}

/**
 * sessions → [epochDay] 这一天的时间线块。标题按「具体任务 → 任务线 → 自由专注」
 * 依次回填：支线打卡不挂任务，只有 questId，落到任务线名上才不会一片「自由专注」。
 *
 * 与计划轨同一套跨天规则：结束时刻按时长推算（次日的「当日分钟数」比起始还小，
 * 直接取会把整段压成一条线），再按天切片，跨零点的专注在两天各画一段。
 */
fun sessionsToBlocks(
    sessions: List<SessionEntity>,
    tasks: List<TaskEntity>,
    nowMillis: Long,
    epochDay: Long,
    quests: List<QuestEntity> = emptyList(),
): List<ActualBlock> = sessions.mapNotNull { session ->
    val startDay = TimeFormat.millisToEpochDay(session.startAt)
    val startMinute = TimeFormat.millisToMinuteOfDay(session.startAt)
    val end = session.endAt ?: nowMillis
    val elapsedMinutes = ((end - session.startAt) / 60_000L).toInt().coerceAtLeast(1)
    val slice = DaySegments.project(
        fromEpochDay = startDay,
        startMinute = startMinute,
        endMinute = startMinute + elapsedMinutes,
        onEpochDay = epochDay,
    ) ?: return@mapNotNull null
    val task = tasks.firstOrNull { it.id == session.taskId }
    val quest = quests.firstOrNull { it.id == (task?.questId ?: session.questId) }
    ActualBlock(
        sessionId = session.id,
        title = task?.title ?: quest?.title ?: "自由专注",
        startMinute = slice.startMinute,
        endMinute = slice.endMinute,
        running = session.endAt == null,
        rewardCi = session.rewardCi,
        isContinuation = slice.isContinuation,
    )
}
