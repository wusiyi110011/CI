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
import com.wsy.ci.core.designsystem.CiChip
import com.wsy.ci.core.designsystem.CiPanelCard
import com.wsy.ci.core.designsystem.CiProgressBar
import com.wsy.ci.core.designsystem.CiShapes
import com.wsy.ci.core.designsystem.CiSizes
import com.wsy.ci.core.designsystem.CiSpacing
import com.wsy.ci.core.designsystem.CiTextField
import com.wsy.ci.core.designsystem.CiUnderlineTabs
import com.wsy.ci.core.economy.Economy
import com.wsy.ci.core.title.Titles
import com.wsy.ci.core.util.TimeFormat
import java.time.LocalDate

private enum class QuestTab(val label: String) {
    MAIN_SIDE("主线 · 支线"),
    DOMAIN("领域头衔"),
}

/** 主线卡宽度：内容区 1344dp 下正好两张一行。 */
private val MAIN_CARD_WIDTH = 660.dp
private val MAIN_CARD_HEIGHT = 180.dp

/** 支线卡宽度：一行四张。 */
private val SIDE_CARD_WIDTH = 320.dp
private val SIDE_CARD_HEIGHT = 132.dp

/** 截止日临近告警阈值。 */
private const val DEADLINE_WARN_DAYS = 3

@Composable
fun QuestScreen(viewModel: QuestViewModel = viewModel()) {
    val quests by viewModel.quests.collectAsState()
    val domains by viewModel.domains.collectAsState()
    val message by viewModel.message.collectAsState()
    val routeGen by viewModel.routeGen.collectAsState()
    val importResult by viewModel.importResult.collectAsState()

    var tab by remember { mutableStateOf(QuestTab.MAIN_SIDE) }
    var editing by remember { mutableStateOf<QuestEntity?>(null) }
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
            if (tab == QuestTab.MAIN_SIDE) {
                FloatingActionButton(
                    onClick = { editing = QuestEntity(type = QuestType.SIDE, title = "") },
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
                if (tab == QuestTab.MAIN_SIDE) {
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
            }

            when (tab) {
                QuestTab.MAIN_SIDE -> QuestBoard(
                    quests = quests,
                    onEdit = { editing = it },
                    onComplete = viewModel::completeQuest,
                    onArchive = viewModel::archiveQuest,
                    modifier = Modifier.weight(1f),
                )
                QuestTab.DOMAIN -> DomainTitleBoard(
                    domains = domains,
                    onAddDomain = viewModel::addDomain,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    editing?.let { quest ->
        QuestEditorDialog(
            initial = quest,
            domains = domains,
            onSave = { viewModel.saveQuest(it) },
            onDismiss = { editing = null },
        )
    }

    if (showImport) {
        ImportDialog(
            result = importResult,
            onImport = viewModel::importJson,
            onDismissResult = viewModel::dismissImportResult,
            onDismiss = { showImport = false },
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuestBoard(
    quests: List<QuestEntity>,
    onEdit: (QuestEntity) -> Unit,
    onComplete: (QuestEntity) -> Unit,
    onArchive: (QuestEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val mains = quests.filter { it.type == QuestType.MAIN }
    val sides = quests.filter { it.type == QuestType.SIDE }

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        if (quests.isEmpty()) {
            Text(
                text = "还没有任务线，点右下角创建第一条吧",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (mains.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionLabel("主线（${mains.count { it.status == QuestStatus.ACTIVE }}/2）")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(CiSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(CiSpacing.md),
                ) {
                    mains.forEach { quest ->
                        MainQuestCard(quest, onEdit, onComplete, onArchive)
                    }
                }
            }
        }
        if (sides.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionLabel("支线")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(CiSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(CiSpacing.md),
                ) {
                    sides.forEach { quest ->
                        SideQuestCard(quest, onEdit, onComplete, onArchive)
                    }
                }
            }
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

/** 主线卡：标题 + 描述 + 编辑/归档 + 截止 chip + 时间进度条。 */
@Composable
private fun MainQuestCard(
    quest: QuestEntity,
    onEdit: (QuestEntity) -> Unit,
    onComplete: (QuestEntity) -> Unit,
    onArchive: (QuestEntity) -> Unit,
) {
    val today = LocalDate.now().toEpochDay()
    val daysLeft = quest.deadlineEpochDay?.minus(today)
    val progress = timeProgress(quest, today)

    CiPanelCard(
        modifier = Modifier.width(MAIN_CARD_WIDTH).height(MAIN_CARD_HEIGHT),
        contentPadding = 20.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(CiSpacing.xs)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = quest.title + if (quest.status == QuestStatus.DONE) "（已完成）" else "",
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
                Row(horizontalArrangement = Arrangement.spacedBy(CiSpacing.xs)) {
                    if (quest.status == QuestStatus.ACTIVE) {
                        IconAction("✏️") { onEdit(quest) }
                        IconAction("✅") { onComplete(quest) }
                    }
                    IconAction("📦") { onArchive(quest) }
                }
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
                        text = if (progress != null) {
                            "时间进度 ${(progress * 100).toInt()}%"
                        } else {
                            quest.description.takeIf { it.isNotBlank() } ?: ""
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                CiProgressBar(
                    progress = progress ?: 0f,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/**
 * 主线的时间进度：createdAt 到截止日之间已走过的比例。
 * 数据层未记录「已完成章节数」，故这里表达的是时间消耗而非内容完成度，标签已注明。
 */
private fun timeProgress(quest: QuestEntity, today: Long): Float? {
    val deadline = quest.deadlineEpochDay ?: return null
    val startDay = TimeFormat.millisToEpochDay(quest.createdAt)
    val span = (deadline - startDay).toFloat()
    if (span <= 0f) return 1f
    return ((today - startDay) / span).coerceIn(0f, 1f)
}

/** 支线卡：标题 + 🔥连击 chip + 历史最佳。 */
@Composable
private fun SideQuestCard(
    quest: QuestEntity,
    onEdit: (QuestEntity) -> Unit,
    onComplete: (QuestEntity) -> Unit,
    onArchive: (QuestEntity) -> Unit,
) {
    CiPanelCard(
        modifier = Modifier.width(SIDE_CARD_WIDTH).height(SIDE_CARD_HEIGHT),
        contentPadding = CiSpacing.md,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(CiSpacing.xxs)) {
                Text(
                    text = quest.title + if (quest.status == QuestStatus.DONE) "（已完成）" else "",
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (quest.status == QuestStatus.ACTIVE) {
                    IconAction("✏️") { onEdit(quest) }
                    IconAction("✅") { onComplete(quest) }
                } else {
                    IconAction("📦") { onArchive(quest) }
                }
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
    onAddDomain: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var newName by remember { mutableStateOf("") }
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(CiSpacing.sm + 2.dp),
    ) {
        domains.forEach { DomainCard(it) }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = CiSpacing.xxs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CiSpacing.sm),
        ) {
            CiTextField(
                value = newName,
                onValueChange = { newName = it },
                placeholder = "新领域名（如：深度学习）",
                modifier = Modifier.width(360.dp),
            )
            TextButton(
                onClick = {
                    if (newName.isNotBlank()) {
                        onAddDomain(newName.trim())
                        newName = ""
                    }
                },
            ) { Text("添加领域") }
        }
    }
}

/** 领域头衔卡：Lv.x · 头衔名 + 经验条 + 6 级头衔 chip 行。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DomainCard(domain: DomainEntity) {
    val level = Economy.levelForExp(domain.totalExp)
    val titles = Titles.titleLine(domain)
    val nextExp = Economy.expToNextLevel(domain.totalExp)

    CiPanelCard(
        modifier = Modifier.fillMaxWidth(),
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
