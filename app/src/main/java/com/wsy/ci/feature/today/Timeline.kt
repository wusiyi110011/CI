package com.wsy.ci.feature.today

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wsy.ci.core.db.SessionEntity
import com.wsy.ci.core.db.TaskEntity
import com.wsy.ci.core.db.TaskStatus
import com.wsy.ci.core.util.TimeFormat

/** 时间线上的一个实际记录块（由 session 换算而来）。 */
data class ActualBlock(
    val startMinute: Int,
    val endMinute: Int,
    val running: Boolean,
    val rewardCi: Long,
)

private val HOUR_HEIGHT: Dp = 64.dp
private const val MINUTES_PER_HOUR = 60f

/**
 * 垂直双轨时间线：左轨计划块（可点击），右轨实际记录块。
 * 高度按分钟线性映射，默认渲染 5:00–24:00。
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
) {
    val totalHeight = HOUR_HEIGHT * (endHour - startHour)
    fun yOf(minute: Int): Dp =
        HOUR_HEIGHT * ((minute - startHour * 60).coerceAtLeast(0) / MINUTES_PER_HOUR)

    Box(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .height(totalHeight)
            .fillMaxWidth()
    ) {
        // 小时刻度
        for (hour in startHour until endHour) {
            Row(
                modifier = Modifier
                    .offset(y = HOUR_HEIGHT * (hour - startHour))
                    .fillMaxWidth()
            ) {
                Text(
                    text = "%02d:00".format(hour),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.width(48.dp),
                )
                HorizontalDivider(
                    modifier = Modifier.padding(top = 8.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                )
            }
        }
        // 当前时刻指示线
        nowMinute?.takeIf { it in startHour * 60 until endHour * 60 }?.let { now ->
            HorizontalDivider(
                modifier = Modifier
                    .offset(y = yOf(now))
                    .padding(start = 48.dp),
                thickness = 2.dp,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Row(modifier = Modifier.fillMaxSize().padding(start = 52.dp)) {
            // 左轨：计划
            Box(modifier = Modifier.weight(0.55f)) {
                tasks.forEach { task ->
                    PlannedBlock(
                        task = task,
                        y = yOf(task.startMinute),
                        height = (yOf(task.endMinute) - yOf(task.startMinute)).coerceAtLeast(24.dp),
                        onClick = { onTaskClick(task) },
                    )
                }
            }
            // 右轨：实际
            Box(modifier = Modifier.weight(0.45f).padding(start = 6.dp)) {
                actuals.forEach { block ->
                    ActualBlockView(
                        block = block,
                        y = yOf(block.startMinute),
                        height = (yOf(block.endMinute) - yOf(block.startMinute)).coerceAtLeast(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PlannedBlock(task: TaskEntity, y: Dp, height: Dp, onClick: () -> Unit) {
    val (container, content) = when (task.status) {
        TaskStatus.DONE -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
        TaskStatus.RUNNING -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        TaskStatus.SKIPPED -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f) to MaterialTheme.colorScheme.outline
        TaskStatus.PLANNED -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
    }
    Column(
        modifier = Modifier
            .offset(y = y)
            .height(height)
            .fillMaxWidth()
            .padding(vertical = 1.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(container)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        val prefix = when (task.status) {
            TaskStatus.DONE -> "✓ "
            TaskStatus.SKIPPED -> "⤫ "
            TaskStatus.RUNNING -> "▶ "
            else -> if (task.locked) "🔒 " else ""
        }
        Text(
            text = prefix + task.title,
            style = MaterialTheme.typography.bodyMedium,
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (height > 40.dp) {
            Text(
                text = "${TimeFormat.minuteOfDay(task.startMinute)} – ${TimeFormat.minuteOfDay(task.endMinute)} · ${task.difficulty.label}",
                style = MaterialTheme.typography.labelSmall,
                color = content.copy(alpha = 0.7f),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ActualBlockView(block: ActualBlock, y: Dp, height: Dp) {
    val color = if (block.running) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary
    Column(
        modifier = Modifier
            .offset(y = y)
            .height(height)
            .fillMaxWidth()
            .padding(vertical = 1.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = if (block.running) 0.85f else 0.6f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        val label = if (block.running) {
            "专注中…"
        } else {
            val mins = block.endMinute - block.startMinute
            "${TimeFormat.duration(mins)}${if (block.rewardCi > 0) " · +${block.rewardCi}CI" else ""}"
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** sessions → 时间线块（只保留落在当天的部分）。 */
fun sessionsToBlocks(sessions: List<SessionEntity>, nowMillis: Long): List<ActualBlock> =
    sessions.map { s ->
        val end = s.endAt ?: nowMillis
        ActualBlock(
            startMinute = TimeFormat.millisToMinuteOfDay(s.startAt),
            endMinute = TimeFormat.millisToMinuteOfDay(end).coerceAtLeast(
                TimeFormat.millisToMinuteOfDay(s.startAt) + 1
            ),
            running = s.endAt == null,
            rewardCi = s.rewardCi,
        )
    }
