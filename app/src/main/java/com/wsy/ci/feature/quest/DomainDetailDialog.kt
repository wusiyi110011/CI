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

package com.wsy.ci.feature.quest

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import com.wsy.ci.R
import com.wsy.ci.core.db.DomainEntity
import com.wsy.ci.core.db.QuestEntity
import com.wsy.ci.core.db.QuestStatus
import com.wsy.ci.core.db.QuestType
import com.wsy.ci.core.designsystem.CiChip
import com.wsy.ci.core.designsystem.CiFormDialog
import com.wsy.ci.core.designsystem.CiFunctionIcon
import com.wsy.ci.core.designsystem.CiProgressBar
import com.wsy.ci.core.designsystem.CiShapes
import com.wsy.ci.core.designsystem.CiSizes
import com.wsy.ci.core.designsystem.CiSpacing
import com.wsy.ci.core.economy.Economy
import com.wsy.ci.core.title.Titles
import com.wsy.ci.core.util.TimeFormat
import java.time.LocalDate

/**
 * 领域详情：这块经验是靠哪些任务线挣来的。
 *
 * [quests] 传全量（含已完成、已归档），这里自己按领域筛——
 * 头衔是历史累计的，只列进行中的会看不出经验的来处。
 */
@Composable
internal fun DomainDetailDialog(
    domain: DomainEntity,
    quests: List<QuestEntity>,
    onOpenQuest: (QuestEntity) -> Unit,
    onEditDomain: (DomainEntity) -> Unit,
    onDeleteDomain: (DomainEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    val mine = quests.filter { it.domainId == domain.id }
    val mains = mine.filter { it.type == QuestType.MAIN }
    val sides = mine.filter { it.type == QuestType.SIDE }
    val level = Economy.levelForExp(domain.totalExp)

    CiFormDialog(
        title = "${domain.name} · Lv.$level ${Titles.currentTitle(domain)}",
        onDismiss = onDismiss,
        confirmLabel = "编辑领域",
        onConfirm = { onEditDomain(domain) },
        dismissLabel = "关闭",
        destructiveLabel = "删除领域",
        onDestructive = { onDeleteDomain(domain) },
        width = CiSizes.dialogWideWidth,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(CiSpacing.sm)) {
            val nextExp = Economy.expToNextLevel(domain.totalExp)
            Text(
                text = if (nextExp != null) {
                    "${domain.totalExp} XP · 距下一级还差 $nextExp"
                } else {
                    "${domain.totalExp} XP · 已达最高头衔"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            CiProgressBar(
                progress = Economy.levelProgress(domain.totalExp),
                color = MaterialTheme.colorScheme.tertiary,
            )

            Column(
                modifier = Modifier
                    .heightIn(max = CiSizes.dialogScrollMaxHeight)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(CiSpacing.xs),
            ) {
                if (mine.isEmpty()) {
                    Text(
                        text = "还没有任务线挂在这个领域下。新建或编辑任务线时选上它，" +
                            "完成的专注就会把经验记到这条头衔线上。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (mains.isNotEmpty()) {
                    GroupLabel("主线（${mains.size}）· 点一行看详情")
                    mains.forEach { QuestLinkRow(it, onClick = { onOpenQuest(it) }) }
                }
                if (sides.isNotEmpty()) {
                    GroupLabel("支线（${sides.size}）· 点一行看详情")
                    sides.forEach { QuestLinkRow(it, onClick = { onOpenQuest(it) }) }
                }
            }
        }
    }
}

/** 删除领域前的二次确认：软删除卡片，任务与历史记录均保留。 */
@Composable
internal fun DeleteDomainDialog(
    domain: DomainEntity,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    CiFormDialog(
        title = "删除领域「${domain.name}」？",
        onDismiss = onDismiss,
        confirmLabel = null,
        onConfirm = null,
        dismissLabel = "取消",
        destructiveLabel = "确认删除",
        onDestructive = onConfirm,
    ) {
        Text(
            text = "删除后不可在界面恢复。关联的任务线与具体任务会解除领域归属，" +
                "但任务、专注记录与 CI 流水都会保留。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun GroupLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = CiSpacing.xxs),
    )
}

/** 一条任务线：状态 chip + 名称 + 右侧尾注（主线看截止，支线看连击）。 */
@Composable
private fun QuestLinkRow(quest: QuestEntity, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CiShapes.field)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = CiSpacing.sm, vertical = CiSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CiSpacing.xs),
    ) {
        CiChip(
            text = questStatusLabel(quest.status),
            container = if (quest.status == QuestStatus.ACTIVE) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHighest
            },
            content = if (quest.status == QuestStatus.ACTIVE) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        Text(
            text = quest.title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (quest.type == QuestType.SIDE) {
                CiFunctionIcon(
                    resourceId = R.drawable.ic_ci_streak,
                    contentDescription = "连续打卡",
                    modifier = Modifier.size(CiSizes.compactIcon),
                )
            }
            Text(
                text = questTrailing(quest),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = if (quest.type == QuestType.SIDE) {
                    Modifier.padding(start = CiSpacing.xxs)
                } else {
                    Modifier
                },
            )
        }
    }
}

internal fun questStatusLabel(status: QuestStatus): String = when (status) {
    QuestStatus.ACTIVE -> "进行中"
    QuestStatus.DONE -> "已完成"
    QuestStatus.ARCHIVED -> "已归档"
}

private fun questTrailing(quest: QuestEntity): String = when {
    quest.type == QuestType.SIDE -> "${quest.streakDays} 天 · 最佳 ${quest.bestStreak}"
    quest.deadlineEpochDay == null -> "无截止日"
    else -> {
        val daysLeft = quest.deadlineEpochDay - LocalDate.now().toEpochDay()
        val suffix = if (daysLeft >= 0) "剩 $daysLeft 天" else "超期 ${-daysLeft} 天"
        "${TimeFormat.shortDate(quest.deadlineEpochDay)} 截止 · $suffix"
    }
}
