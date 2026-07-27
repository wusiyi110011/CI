package com.wsy.ci.feature.quest

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wsy.ci.core.db.QuestEntity
import com.wsy.ci.core.db.QuestStatus
import com.wsy.ci.core.db.QuestType
import com.wsy.ci.core.db.DomainEntity
import com.wsy.ci.core.economy.Economy
import com.wsy.ci.core.title.Titles
import com.wsy.ci.core.util.TimeFormat
import java.time.LocalDate

@Composable
fun QuestScreen(viewModel: QuestViewModel = viewModel()) {
    val quests by viewModel.quests.collectAsState()
    val domains by viewModel.domains.collectAsState()
    val message by viewModel.message.collectAsState()

    var tab by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<QuestEntity?>(null) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.dismissMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (tab == 0) {
                ExtendedFloatingActionButton(
                    text = { Text("新建任务线") },
                    icon = { Text("＋") },
                    onClick = {
                        editing = QuestEntity(type = QuestType.SIDE, title = "")
                    },
                )
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("主线 / 支线") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("领域头衔") })
            }
            when (tab) {
                0 -> QuestList(
                    quests = quests,
                    onEdit = { editing = it },
                    onComplete = viewModel::completeQuest,
                    onArchive = viewModel::archiveQuest,
                )
                1 -> DomainTitleList(domains = domains, onAddDomain = viewModel::addDomain)
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
}

@Composable
private fun QuestList(
    quests: List<QuestEntity>,
    onEdit: (QuestEntity) -> Unit,
    onComplete: (QuestEntity) -> Unit,
    onArchive: (QuestEntity) -> Unit,
) {
    val mains = quests.filter { it.type == QuestType.MAIN }
    val sides = quests.filter { it.type == QuestType.SIDE }
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize().padding(top = 12.dp),
    ) {
        item { Text("🗡 主线（${mains.count { it.status == QuestStatus.ACTIVE }}/2）", style = MaterialTheme.typography.titleMedium) }
        items(mains, key = { it.id }) { q ->
            QuestCard(q, onEdit, onComplete, onArchive)
        }
        item { Text("🔁 支线", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
        items(sides, key = { it.id }) { q ->
            QuestCard(q, onEdit, onComplete, onArchive)
        }
        if (quests.isEmpty()) {
            item { Text("还没有任务线，点右下角创建第一条吧", color = MaterialTheme.colorScheme.outline) }
        }
    }
}

@Composable
private fun QuestCard(
    quest: QuestEntity,
    onEdit: (QuestEntity) -> Unit,
    onComplete: (QuestEntity) -> Unit,
    onArchive: (QuestEntity) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    quest.title + if (quest.status == QuestStatus.DONE) "（已完成）" else "",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                if (quest.type == QuestType.SIDE && quest.streakDays > 0) {
                    AssistChip(
                        onClick = {},
                        label = {
                            val bonus = (Economy.streakBonus(quest.streakDays) * 100).toInt()
                            Text("🔥${quest.streakDays}天" + if (bonus > 0) " +$bonus%" else "")
                        },
                    )
                }
            }
            if (quest.description.isNotBlank()) {
                Text(quest.description, style = MaterialTheme.typography.bodyMedium)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                quest.deadlineEpochDay?.let {
                    val daysLeft = it - LocalDate.now().toEpochDay()
                    Text(
                        "截止 ${TimeFormat.shortDate(it)}（${if (daysLeft >= 0) "剩 $daysLeft 天" else "已超期"}）",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (daysLeft < 3) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.weight(1f),
                    )
                } ?: androidx.compose.foundation.layout.Spacer(modifier = Modifier.weight(1f))
                if (quest.status == QuestStatus.ACTIVE) {
                    TextButton(onClick = { onEdit(quest) }) { Text("编辑") }
                    TextButton(onClick = { onComplete(quest) }) { Text("完成") }
                }
                TextButton(onClick = { onArchive(quest) }) { Text("归档") }
            }
            if (quest.type == QuestType.SIDE && quest.bestStreak > 0) {
                Text(
                    "历史最佳连击 ${quest.bestStreak} 天",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

@Composable
private fun DomainTitleList(domains: List<DomainEntity>, onAddDomain: (String) -> Unit) {
    var newName by remember { mutableStateOf("") }
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize().padding(top = 12.dp),
    ) {
        items(domains, key = { it.id }) { domain ->
            DomainCard(domain)
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("新领域名（如：深度学习）") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = {
                        if (newName.isNotBlank()) {
                            onAddDomain(newName.trim())
                            newName = ""
                        }
                    },
                ) { Text("添加") }
            }
        }
    }
}

@Composable
private fun DomainCard(domain: DomainEntity) {
    val level = Economy.levelForExp(domain.totalExp)
    val titles = Titles.titleLine(domain)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(domain.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(
                    "Lv.$level ${Titles.currentTitle(domain)}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            LinearProgressIndicator(
                progress = { Economy.levelProgress(domain.totalExp) },
                modifier = Modifier.fillMaxWidth(),
            )
            val next = Economy.expToNextLevel(domain.totalExp)
            Text(
                if (next != null) "累计经验 ${domain.totalExp} · 距 ${titles[level]} 还差 $next"
                else "累计经验 ${domain.totalExp} · 已达最高头衔",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.outline,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                titles.forEachIndexed { i, t ->
                    FilterChip(
                        selected = i < level,
                        onClick = {},
                        label = { Text(t, style = MaterialTheme.typography.labelSmall) },
                    )
                }
            }
        }
    }
}
