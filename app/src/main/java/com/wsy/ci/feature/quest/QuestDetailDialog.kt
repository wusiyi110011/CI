package com.wsy.ci.feature.quest

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wsy.ci.core.db.QuestEntity
import com.wsy.ci.core.db.QuestStatus
import com.wsy.ci.core.db.QuestType
import com.wsy.ci.core.db.TaskEntity
import com.wsy.ci.core.db.TaskStatus
import com.wsy.ci.core.designsystem.CiChip
import com.wsy.ci.core.designsystem.CiDifficultyChip
import com.wsy.ci.core.designsystem.CiFormDialog
import com.wsy.ci.core.designsystem.CiProgressBar
import com.wsy.ci.core.designsystem.CiShapes
import com.wsy.ci.core.designsystem.CiSizes
import com.wsy.ci.core.designsystem.CiSpacing
import com.wsy.ci.core.designsystem.CiTheme
import com.wsy.ci.core.designsystem.tabularNums
import com.wsy.ci.core.quest.QuestProgress
import com.wsy.ci.core.util.TimeFormat
import com.wsy.ci.llm.RouteChapter
import java.time.LocalDate
import kotlinx.serialization.json.Json

/** 任务行左侧状态圆点直径。 */
private val STATUS_DOT_SIZE = 8.dp

private val LENIENT_JSON = Json { ignoreUnknownKeys = true }

/**
 * 任务线详情：一屏看完「章节结构 + 排出来的具体任务 + 完成到哪了」，
 * 并且可以直接对某个任务开始专注，不必先跳去日程屏找。
 */
@Composable
internal fun QuestDetailDialog(
    quest: QuestEntity,
    tasks: List<TaskEntity>,
    /** 支线归属的主线名，没挂或本身是主线时为 null。 */
    parentMainTitle: String?,
    isTimerRunning: Boolean,
    onStartQuest: () -> Unit,
    onStartTask: (TaskEntity) -> Unit,
    onSkipTask: (TaskEntity) -> Unit,
    onEditTask: (TaskEntity) -> Unit,
    onEditQuest: () -> Unit,
    onDeleteQuest: () -> Unit,
    onDismiss: () -> Unit,
) {
    val progress = QuestProgress.of(tasks)
    // 任务是异步流进来的，内容变高后把视口拉回顶部，否则会停在列表中段
    val bodyScroll = rememberScrollState()
    LaunchedEffect(quest.id, tasks.size) { bodyScroll.scrollTo(0) }

    CiFormDialog(
        title = quest.title,
        onDismiss = onDismiss,
        confirmLabel = if (quest.status == QuestStatus.ACTIVE) "编辑任务线" else null,
        onConfirm = if (quest.status == QuestStatus.ACTIVE) onEditQuest else null,
        dismissLabel = "关闭",
        // 已完成/归档的线更需要能删，所以不分状态都给
        destructiveLabel = "删除",
        onDestructive = onDeleteQuest,
        width = CiSizes.dialogWideWidth,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(CiSpacing.sm)) {
            QuestHeadline(quest = quest, progress = progress, parentMainTitle = parentMainTitle)

            if (quest.status == QuestStatus.ACTIVE) {
                StartQuestButton(
                    quest = quest,
                    isTimerRunning = isTimerRunning,
                    onStart = onStartQuest,
                )
            }

            Column(
                modifier = Modifier
                    .heightIn(max = CiSizes.dialogScrollMaxHeight)
                    .verticalScroll(bodyScroll),
                verticalArrangement = Arrangement.spacedBy(CiSpacing.sm),
            ) {
                chaptersOf(quest).takeIf { it.isNotEmpty() }?.let { chapters ->
                    SectionTitle("章节（${chapters.size}）")
                    chapters.forEachIndexed { index, chapter ->
                        ChapterRow(index = index, chapter = chapter)
                    }
                }

                SectionTitle("具体任务（${tasks.size}）· 点一行可编辑")
                if (tasks.isEmpty()) {
                    // 已完成/归档的线在任务编辑器里已经选不到了，别再引导去关联
                    Text(
                        text = if (quest.status == QuestStatus.ACTIVE) {
                            "这条任务线还没有排出时间块。去日程屏新建任务并关联到它，" +
                                "或者导入含 tasks 的 JSON 计划。"
                        } else {
                            "这条任务线没有留下任何时间块。"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                tasks.forEach { task ->
                    TaskRow(
                        task = task,
                        isTimerRunning = isTimerRunning,
                        onStart = { onStartTask(task) },
                        onSkip = { onSkipTask(task) },
                        onEdit = { onEditTask(task) },
                    )
                }
            }

            if (isTimerRunning) {
                Text(
                    text = "已有进行中的专注，结束后才能开始新任务",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/** 顶部小结：类型/状态/截止 chip 一行 + 描述 + 完成度进度条。 */
@Composable
private fun QuestHeadline(
    quest: QuestEntity,
    progress: QuestProgress,
    parentMainTitle: String?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(CiSpacing.xs)) {
        Row(horizontalArrangement = Arrangement.spacedBy(CiSpacing.xs)) {
            CiChip(
                text = if (quest.type == QuestType.MAIN) "主线" else "支线",
                container = MaterialTheme.colorScheme.tertiaryContainer,
                content = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            CiChip(
                text = when (quest.status) {
                    QuestStatus.ACTIVE -> "进行中"
                    QuestStatus.DONE -> "已完成"
                    QuestStatus.ARCHIVED -> "已归档"
                },
                container = MaterialTheme.colorScheme.surfaceContainerHighest,
                content = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            parentMainTitle?.let {
                CiChip(
                    text = "🗡 归属主线：$it",
                    container = MaterialTheme.colorScheme.secondaryContainer,
                    content = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            quest.deadlineEpochDay?.let { deadline ->
                val daysLeft = deadline - LocalDate.now().toEpochDay()
                CiChip(
                    text = if (daysLeft >= 0) {
                        "${TimeFormat.shortDate(deadline)} 截止 · 剩 $daysLeft 天"
                    } else {
                        "${TimeFormat.shortDate(deadline)} 截止 · 超期 ${-daysLeft} 天"
                    },
                    container = if (daysLeft < 0) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        MaterialTheme.colorScheme.primaryContainer
                    },
                    content = if (daysLeft < 0) {
                        MaterialTheme.colorScheme.onErrorContainer
                    } else {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    },
                )
            }
        }
        if (quest.description.isNotBlank()) {
            Text(
                text = quest.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = "完成 ${progress.done}/${progress.total} 个任务" +
                " · 计划 ${TimeFormat.duration(progress.plannedMinutes)}" +
                " · 已完成 ${TimeFormat.duration(progress.doneMinutes)}" +
                if (progress.skipped > 0) " · 跳过 ${progress.skipped} 个" else "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        CiProgressBar(
            progress = progress.ratio,
            color = MaterialTheme.colorScheme.tertiary,
        )
    }
}

/**
 * 任务线级别的开始按钮。
 * 支线（每日打卡类）基本没有排时间块，这是它唯一的开工入口——
 * 点下去会就地建一个挂在本支线上的时间块再计时，事后能在上面的列表里查到。
 */
@Composable
private fun StartQuestButton(quest: QuestEntity, isTimerRunning: Boolean, onStart: () -> Unit) {
    Button(
        onClick = onStart,
        enabled = !isTimerRunning,
        shape = CiShapes.pill,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.tertiary,
            contentColor = MaterialTheme.colorScheme.onTertiary,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = if (quest.type == QuestType.SIDE) {
                "▶ 开始打卡（自动记为本支线的一个任务）"
            } else {
                "▶ 开始专注这条主线（不挂具体任务）"
            },
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = CiSpacing.xxs),
    )
}

@Composable
private fun ChapterRow(index: Int, chapter: RouteChapter) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CiShapes.field)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = CiSpacing.sm, vertical = CiSpacing.xs),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = "${index + 1}. ${chapter.title} · 预估 ${"%.1f".format(chapter.hours)} 小时",
            style = MaterialTheme.typography.bodyMedium,
        )
        if (chapter.resources.isNotEmpty()) {
            Text(
                text = chapter.resources.joinToString("、"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 一条具体任务：状态点 + 日期时间 + 标题 + 难度，右侧「开始 / 跳过」。
 * 整行可点，打开任务编辑卡，改时间、难度、备注都不必跳去日程屏。
 */
@Composable
private fun TaskRow(
    task: TaskEntity,
    isTimerRunning: Boolean,
    onStart: () -> Unit,
    onSkip: () -> Unit,
    onEdit: () -> Unit,
) {
    val colors = CiTheme.colors.taskBlock(task.status)
    val canStart = task.status == TaskStatus.PLANNED && !isTimerRunning

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CiShapes.field)
            .background(colors.container)
            .alpha(colors.alpha)
            .clickable(onClick = onEdit)
            .padding(horizontal = CiSpacing.sm, vertical = CiSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CiSpacing.xs),
    ) {
        Box(
            modifier = Modifier
                .size(STATUS_DOT_SIZE)
                .clip(CiShapes.pill)
                .background(colors.accent)
        )
        Text(
            text = "${TimeFormat.shortDate(task.epochDay)} " +
                "${TimeFormat.minuteOfDay(task.startMinute)}–" +
                TimeFormat.minuteOfDay(task.endMinute),
            style = MaterialTheme.typography.labelMedium.tabularNums(),
            color = colors.content,
        )
        Text(
            text = task.title,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textDecoration = if (colors.strikethrough) TextDecoration.LineThrough else null,
            modifier = Modifier.weight(1f),
        )
        CiDifficultyChip(task.difficulty)
        if (canStart) {
            RowAction(text = "▶ 开始", onClick = onStart)
            RowAction(text = "跳过", onClick = onSkip)
        } else {
            Text(
                text = task.status.label,
                style = MaterialTheme.typography.labelSmall,
                color = colors.content,
            )
        }
    }
}

@Composable
private fun RowAction(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .clip(CiShapes.pill)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** 章节存的是 LLM/导入写进来的 JSON，解析失败按「没有章节」处理，不让详情页崩掉。 */
private fun chaptersOf(quest: QuestEntity): List<RouteChapter> {
    val json = quest.chaptersJson ?: return emptyList()
    return try {
        LENIENT_JSON.decodeFromString<List<RouteChapter>>(json)
    } catch (_: Exception) {
        emptyList()
    }
}
