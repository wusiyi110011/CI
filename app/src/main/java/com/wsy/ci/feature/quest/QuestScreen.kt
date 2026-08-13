package com.wsy.ci.feature.quest

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wsy.ci.core.db.DomainEntity
import com.wsy.ci.core.db.QuestEntity
import com.wsy.ci.core.db.QuestStatus
import com.wsy.ci.core.db.QuestType
import com.wsy.ci.core.db.TaskEntity
import com.wsy.ci.core.designsystem.CiChip
import com.wsy.ci.core.designsystem.CiFormDialog
import com.wsy.ci.core.designsystem.CiPanelCard
import com.wsy.ci.core.designsystem.CiProgressBar
import com.wsy.ci.core.designsystem.CiShapes
import com.wsy.ci.core.designsystem.CiSizes
import com.wsy.ci.core.designsystem.CiSpacing
import com.wsy.ci.core.designsystem.CiUnderlineTabs
import com.wsy.ci.core.economy.Economy
import com.wsy.ci.core.quest.QuestProgress
import com.wsy.ci.core.title.Titles
import com.wsy.ci.feature.today.TaskEditorDialog
import java.time.LocalDate

private enum class QuestTab(val label: String) {
    MAIN_SIDE("主线 · 支线"),
    FINISHED("已完成主线 · 支线"),
    DOMAIN("领域头衔"),
}

/** 主线卡：每条独占一行。 */
private const val MAIN_CARDS_PER_ROW = 1
private val MAIN_CARD_HEIGHT = 180.dp

/** 支线卡：一行四张。 */
private const val SIDE_CARDS_PER_ROW = 4
private val SIDE_CARD_HEIGHT = 132.dp

/** 截止日临近告警阈值。 */
private const val DEADLINE_WARN_DAYS = 3

@Composable
fun QuestScreen(
    viewModel: QuestViewModel = viewModel(),
    onNavigateToToday: () -> Unit = {},
) {
    val quests by viewModel.quests.collectAsState()
    val domains by viewModel.domains.collectAsState()
    val allTasks by viewModel.allTasks.collectAsState()
    val message by viewModel.message.collectAsState()
    val routeGen by viewModel.routeGen.collectAsState()
    val importPending by viewModel.importPending.collectAsState()
    val importResult by viewModel.importResult.collectAsState()
    val selectedQuestId by viewModel.selectedQuestId.collectAsState()
    val questTasks by viewModel.questTasks.collectAsState()
    val running by viewModel.runningSession.collectAsState()
    val batchAssign by viewModel.batchAssign.collectAsState()

    // 进行中与完成/归档分屏展示：完成的仍要能点开回看，所以两份都来自同一个全量流
    val activeQuests = quests.filter { it.status == QuestStatus.ACTIVE }
    val finishedQuests = quests.filter { it.status != QuestStatus.ACTIVE }
    val activeMains = activeQuests.filter { it.type == QuestType.MAIN }

    var tab by remember { mutableStateOf(QuestTab.MAIN_SIDE) }
    var editing by remember { mutableStateOf<QuestEntity?>(null) }
    var editingTask by remember { mutableStateOf<TaskEntity?>(null) }
    var openedDomain by remember { mutableStateOf<DomainEntity?>(null) }
    var editingDomain by remember { mutableStateOf<DomainEntity?>(null) }
    var deletingDomain by remember { mutableStateOf<DomainEntity?>(null) }
    var deleting by remember { mutableStateOf<QuestEntity?>(null) }
    var showRouteGen by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.dismissMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.surface,
        floatingActionButton = {
            if (tab == QuestTab.MAIN_SIDE || tab == QuestTab.DOMAIN) {
                FloatingActionButton(
                    onClick = {
                        if (tab == QuestTab.MAIN_SIDE) {
                            editing = QuestEntity(type = QuestType.SIDE, title = "")
                        } else {
                            editingDomain = DomainEntity(name = "")
                        }
                    },
                    shape = CiShapes.fab,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(CiSizes.fab),
                ) {
                    Text("＋", style = MaterialTheme.typography.headlineSmall)
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(CiSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(CiSpacing.md),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                CiUnderlineTabs(
                    options = QuestTab.entries,
                    selected = tab,
                    label = { it.label },
                    onSelect = { tab = it },
                    modifier = Modifier.weight(1f),
                )
                Row(
                    modifier = Modifier.padding(bottom = CiSpacing.xs),
                    horizontalArrangement = Arrangement.spacedBy(CiSpacing.xs),
                ) {
                    OutlinedButton(onClick = { showRouteGen = true }, shape = CiShapes.pill) {
                        Text("🤖 AI 生成学习路线", style = MaterialTheme.typography.labelMedium)
                    }
                    OutlinedButton(onClick = { showImport = true }, shape = CiShapes.pill) {
                        Text("📥 导入 JSON 计划", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            when (tab) {
                QuestTab.MAIN_SIDE -> QuestBoard(
                    quests = activeQuests,
                    tasks = allTasks,
                    emptyHint = "还没有进行中的任务线，点右下角创建第一条吧",
                    onOpen = viewModel::openQuest,
                    onEdit = { editing = it },
                    onComplete = viewModel::completeQuest,
                    onArchive = viewModel::archiveQuest,
                    onRestore = viewModel::restoreQuest,
                    onBatchAssign = viewModel::openBatchAssign,
                    modifier = Modifier.weight(1f),
                )
                QuestTab.FINISHED -> QuestBoard(
                    quests = finishedQuests,
                    tasks = allTasks,
                    emptyHint = "还没有完成或归档的任务线。完成一条主线/支线后它会挪到这里，" +
                        "点开仍能回看当初排出的具体任务。",
                    onOpen = viewModel::openQuest,
                    onEdit = { editing = it },
                    onComplete = viewModel::completeQuest,
                    onArchive = viewModel::archiveQuest,
                    onRestore = viewModel::restoreQuest,
                    onBatchAssign = viewModel::openBatchAssign,
                    modifier = Modifier.weight(1f),
                )
                QuestTab.DOMAIN -> DomainTitleBoard(
                    domains = domains,
                    onOpenDomain = { openedDomain = it },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    openedDomain?.let { domain ->
        DomainDetailDialog(
            domain = domain,
            quests = quests,
            onOpenQuest = { viewModel.openQuest(it); openedDomain = null },
            onEditDomain = { editingDomain = it; openedDomain = null },
            onDeleteDomain = {
                deletingDomain = it
                openedDomain = null
            },
            onDismiss = { openedDomain = null },
        )
    }

    deletingDomain?.let { domain ->
        DeleteDomainDialog(
            domain = domain,
            onConfirm = {
                viewModel.deleteDomain(domain)
                deletingDomain = null
                openedDomain = null
            },
            onDismiss = { deletingDomain = null },
        )
    }

    editingDomain?.let { domain ->
        DomainEditorDialog(
            initial = domain,
            onSave = { name, titles ->
                viewModel.saveDomain(domain, name, titles)
                editingDomain = null
            },
            onDismiss = { editingDomain = null },
        )
    }

    quests.firstOrNull { it.id == selectedQuestId }?.let { quest ->
        QuestDetailDialog(
            quest = quest,
            tasks = questTasks,
            parentMainTitle = quest.parentQuestId
                ?.let { parentId -> quests.firstOrNull { it.id == parentId } }
                ?.title,
            isTimerRunning = running != null,
            onStartQuest = {
                viewModel.startQuestFocus(quest)
                viewModel.closeQuest()
                onNavigateToToday()
            },
            onStartTask = {
                viewModel.startTimer(it)
                viewModel.closeQuest()
                onNavigateToToday()
            },
            onSkipTask = viewModel::skipTask,
            // 任务卡叠在详情之上，存完退回详情，接着改下一条
            onEditTask = { editingTask = it },
            onEditQuest = { editing = quest; viewModel.closeQuest() },
            onDeleteQuest = { deleting = quest },
            onDismiss = viewModel::closeQuest,
        )
    }

    deleting?.let { quest ->
        DeleteQuestDialog(
            quest = quest,
            taskCount = questTasks.size,
            onConfirm = { viewModel.deleteQuest(quest); deleting = null },
            onDismiss = { deleting = null },
        )
    }

    batchAssign?.let { state ->
        BatchAssignTasksDialog(
            state = state,
            onConfirm = viewModel::assignTasksToMain,
            onDismiss = viewModel::closeBatchAssign,
        )
    }

    editingTask?.let { task ->
        TaskEditorDialog(
            initial = task,
            domains = domains,
            // 只让挂进行中的任务线，避免新任务落到已完成的线上
            quests = activeQuests,
            onSave = viewModel::saveTask,
            onDelete = viewModel::deleteTask,
            onDismiss = { editingTask = null },
            onCreateDomain = { name, onCreated -> viewModel.addDomain(name, onCreated) },
        )
    }

    editing?.let { quest ->
        QuestEditorDialog(
            initial = quest,
            domains = domains,
            // 可挂靠的主线：进行中的 + 这条支线当前已挂的那条（哪怕它已完成）
            mains = (activeMains + quests.filter { it.id == quest.parentQuestId }).distinct(),
            onSave = { viewModel.saveQuest(it) },
            onDismiss = { editing = null },
        )
    }

    if (showImport) {
        ImportDialog(
            preview = importPending?.preview,
            result = importResult,
            onPreview = viewModel::previewImport,
            onConfirm = viewModel::confirmImport,
            onCancelPreview = viewModel::cancelImportPreview,
            onDismissResult = viewModel::dismissImportResult,
            onDismiss = {
                viewModel.cancelImportPreview()
                showImport = false
            },
        )
    }

    if (showRouteGen) {
        RouteGenDialog(
            state = routeGen,
            onGenerate = viewModel::generateRoute,
            onConfirm = {
                viewModel.confirmRoute(it)
                showRouteGen = false
            },
            onDismiss = {
                viewModel.dismissRouteGen()
                showRouteGen = false
            },
        )
    }
}

/** 删除前的二次确认：说清楚会连带影响什么，删除不可撤销。 */
@Composable
private fun DeleteQuestDialog(
    quest: QuestEntity,
    taskCount: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val kind = if (quest.type == QuestType.MAIN) "主线" else "支线"
    CiFormDialog(
        title = "删除$kind「${quest.title}」？",
        onDismiss = onDismiss,
        confirmLabel = null,
        onConfirm = null,
        dismissLabel = "取消",
        destructiveLabel = "确认删除",
        onDestructive = onConfirm,
    ) {
        Text(
            text = buildString {
                append("删除后不可恢复。")
                if (taskCount > 0) {
                    append("它下面的 $taskCount 个具体任务不会被删，只是解除关联，")
                    append("仍留在日程与复盘记录里。")
                } else {
                    append("这条$kind 没有排出任何时间块。")
                }
                if (quest.type == QuestType.MAIN) {
                    append("挂在它下面的支线会变回独立支线。")
                }
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 主线 + 支线两栏看板。进行中与已完成两个 tab 共用它，差别只在传进来的列表，
 * 卡片上的操作按钮按每张卡自己的状态给（见 [QuestCardActions]）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuestBoard(
    quests: List<QuestEntity>,
    tasks: List<TaskEntity>,
    emptyHint: String,
    onOpen: (QuestEntity) -> Unit,
    onEdit: (QuestEntity) -> Unit,
    onComplete: (QuestEntity) -> Unit,
    onArchive: (QuestEntity) -> Unit,
    onRestore: (QuestEntity) -> Unit,
    onBatchAssign: (QuestEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val mains = quests.filter { it.type == QuestType.MAIN }
    val sides = quests.filter { it.type == QuestType.SIDE }
    val tasksByQuest = remember(tasks) { tasks.groupBy { it.questId } }

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        if (quests.isEmpty()) {
            Text(
                text = emptyHint,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (mains.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val activeCount = mains.count { it.status == QuestStatus.ACTIVE }
                SectionLabel(
                    if (activeCount > 0) {
                        "主线（$activeCount/$MAX_ACTIVE_MAIN_QUESTS）"
                    } else {
                        "主线（${mains.size}）"
                    }
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(CiSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(CiSpacing.md),
                    maxItemsInEachRow = MAIN_CARDS_PER_ROW,
                ) {
                    mains.forEach { quest ->
                        MainQuestCard(
                            quest = quest,
                            progress = QuestProgress.of(tasksByQuest[quest.id].orEmpty()),
                            onOpen = onOpen,
                            onEdit = onEdit,
                            onComplete = onComplete,
                            onArchive = onArchive,
                            onRestore = onRestore,
                            onBatchAssign = onBatchAssign,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
        if (sides.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionLabel("支线（${sides.size}）")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(CiSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(CiSpacing.md),
                    maxItemsInEachRow = SIDE_CARDS_PER_ROW,
                ) {
                    sides.forEach { quest ->
                        SideQuestCard(
                            quest = quest,
                            onOpen = onOpen,
                            onEdit = onEdit,
                            onComplete = onComplete,
                            onArchive = onArchive,
                            onRestore = onRestore,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

/**
 * 卡片右上角的操作组。进行中给「编辑 / 完成 / 归档」，
 * 完成或归档后给「恢复」——归档不该是单程票，收错了要能捞回来。
 */
@Composable
private fun QuestCardActions(
    quest: QuestEntity,
    onEdit: (QuestEntity) -> Unit,
    onComplete: (QuestEntity) -> Unit,
    onArchive: (QuestEntity) -> Unit,
    onRestore: (QuestEntity) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(CiSpacing.xs)) {
        when (quest.status) {
            QuestStatus.ACTIVE -> {
                IconAction("✏️") { onEdit(quest) }
                IconAction("✅") { onComplete(quest) }
                IconAction("📦") { onArchive(quest) }
            }
            QuestStatus.DONE -> {
                IconAction("↩️") { onRestore(quest) }
                IconAction("📦") { onArchive(quest) }
            }
            QuestStatus.ARCHIVED -> IconAction("↩️") { onRestore(quest) }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** 主线卡：标题 + 描述 + 编辑/归档 + 截止 chip + 时间进度条。整卡可点，打开任务线详情。 */
@Composable
private fun MainQuestCard(
    quest: QuestEntity,
    progress: QuestProgress,
    onOpen: (QuestEntity) -> Unit,
    onEdit: (QuestEntity) -> Unit,
    onComplete: (QuestEntity) -> Unit,
    onArchive: (QuestEntity) -> Unit,
    onRestore: (QuestEntity) -> Unit,
    onBatchAssign: (QuestEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val today = LocalDate.now().toEpochDay()
    val daysLeft = quest.deadlineEpochDay?.minus(today)

    CiPanelCard(
        modifier = modifier.height(MAIN_CARD_HEIGHT).clickable { onOpen(quest) },
        contentPadding = 20.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(CiSpacing.xs)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = quest.title + statusSuffix(quest.status),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (quest.description.isNotBlank()) {
                        Text(
                            text = quest.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = CiSpacing.xxs),
                        )
                    }
                }
                if (quest.status == QuestStatus.ACTIVE) {
                    TextButton(onClick = { onBatchAssign(quest) }) {
                        Text("批量关联任务")
                    }
                }
                QuestCardActions(quest, onEdit, onComplete, onArchive, onRestore)
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    val deadlineText = when {
                        daysLeft == null -> "无截止日"
                        daysLeft >= 0 -> "剩 $daysLeft 天"
                        else -> "已超期 ${-daysLeft} 天"
                    }
                    CiChip(
                        text = deadlineText,
                        container = if (daysLeft != null && daysLeft < DEADLINE_WARN_DAYS) {
                            MaterialTheme.colorScheme.errorContainer
                        } else {
                            MaterialTheme.colorScheme.primaryContainer
                        },
                        content = if (daysLeft != null && daysLeft < DEADLINE_WARN_DAYS) {
                            MaterialTheme.colorScheme.onErrorContainer
                        } else {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        },
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Text(
                        text = if (progress.total > 0) {
                            "任务进度 ${progress.done + progress.skipped}/${progress.total} · " +
                                "已处理 ${(progress.ratio * 100).toInt()}%"
                        } else {
                            "暂无具体任务"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                CiProgressBar(
                    progress = progress.ratio,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/** 支线卡：标题 + 🔥连击 chip + 历史最佳。整卡可点，打开任务线详情。 */
@Composable
private fun SideQuestCard(
    quest: QuestEntity,
    onOpen: (QuestEntity) -> Unit,
    onEdit: (QuestEntity) -> Unit,
    onComplete: (QuestEntity) -> Unit,
    onArchive: (QuestEntity) -> Unit,
    onRestore: (QuestEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    CiPanelCard(
        modifier = modifier.height(SIDE_CARD_HEIGHT).clickable { onOpen(quest) },
        contentPadding = CiSpacing.md,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(CiSpacing.xxs)) {
                Text(
                    text = quest.title + statusSuffix(quest.status),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                QuestCardActions(quest, onEdit, onComplete, onArchive, onRestore)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                val bonusPercent = (Economy.streakBonus(quest.streakDays) * 100).toInt()
                CiChip(
                    text = "🔥 ${quest.streakDays} 天" +
                        if (bonusPercent > 0) " +$bonusPercent%" else "",
                    container = MaterialTheme.colorScheme.secondaryContainer,
                    content = MaterialTheme.colorScheme.onSecondaryContainer,
                    style = MaterialTheme.typography.labelMedium,
                    horizontalPadding = 10.dp,
                )
                Text(
                    text = "最佳 ${quest.bestStreak} 天",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 卡片标题后缀：已完成 tab 里两种状态混排，光看卡片得能分出来。 */
private fun statusSuffix(status: QuestStatus): String = when (status) {
    QuestStatus.ACTIVE -> ""
    QuestStatus.DONE -> "（已完成）"
    QuestStatus.ARCHIVED -> "（已归档）"
}

@Composable
private fun IconAction(emoji: String, onClick: () -> Unit) {
    Text(
        text = emoji,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.clickable(onClick = onClick).padding(2.dp),
    )
}

@Composable
private fun DomainTitleBoard(
    domains: List<DomainEntity>,
    onOpenDomain: (DomainEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(CiSpacing.sm + 2.dp),
    ) {
        SectionLabel("点开任一领域，可以看到挣这条头衔线经验的主线与支线（含已完成的）")
        domains.forEach { domain ->
            DomainCard(domain, onOpen = { onOpenDomain(domain) })
        }
    }
}

/** 领域头衔卡：Lv.x · 头衔名 + 经验条 + 6 级头衔 chip 行。整卡可点，看这块经验的来处。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DomainCard(domain: DomainEntity, onOpen: () -> Unit) {
    val level = Economy.levelForExp(domain.totalExp)
    val titles = Titles.titleLine(domain)
    val nextExp = Economy.expToNextLevel(domain.totalExp)

    CiPanelCard(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        contentPadding = 20.dp,
        verticalSpacing = 10.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "${domain.name} · Lv.$level ${Titles.currentTitle(domain)}",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = if (nextExp != null) {
                    "${domain.totalExp} XP · 距下一级还差 $nextExp"
                } else {
                    "${domain.totalExp} XP · 已达最高头衔"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        CiProgressBar(
            progress = Economy.levelProgress(domain.totalExp),
            color = MaterialTheme.colorScheme.tertiary,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(CiSpacing.xs)) {
            titles.forEachIndexed { index, title ->
                val unlocked = index < level
                CiChip(
                    text = title,
                    container = if (unlocked) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    },
                    content = if (unlocked) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    verticalPadding = 5.dp,
                )
            }
        }
    }
}
