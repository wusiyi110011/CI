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

package com.wsy.ci.feature.today

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.wsy.ci.R
import com.wsy.ci.core.data.Settlement
import com.wsy.ci.core.db.TaskEntity
import com.wsy.ci.core.db.TaskStatus
import com.wsy.ci.core.designsystem.CiElevation
import com.wsy.ci.core.designsystem.CiFormDialog
import com.wsy.ci.core.designsystem.CiFormField
import com.wsy.ci.core.designsystem.CiFunctionIcon
import com.wsy.ci.core.designsystem.CiMotion
import com.wsy.ci.core.designsystem.CiShapes
import com.wsy.ci.core.designsystem.CiSizes
import com.wsy.ci.core.designsystem.CiSpacing
import com.wsy.ci.core.designsystem.CiTheme
import com.wsy.ci.core.designsystem.tabularNums
import com.wsy.ci.core.economy.Economy
import com.wsy.ci.core.economy.FocusOutcome
import com.wsy.ci.core.util.TimeFormat

/**
 * 庆祝类对话框外壳：480dp 宽、圆角 28，带放射光环装饰。
 * [accent] 决定光环与主数值的强调色（入账用电青、升级用金铜）。
 */
@Composable
private fun CiCelebrateDialog(
    accent: Color,
    onDismiss: () -> Unit,
    actionLabel: String,
    onAction: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val reducedMotion = CiTheme.reducedMotion
    var contentVisible by remember { mutableStateOf(!reducedMotion) }
    LaunchedEffect(Unit) { contentVisible = true }
    val contentAlpha by animateFloatAsState(
        targetValue = if (contentVisible) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (reducedMotion) CiMotion.REDUCED else 0,
        ),
        label = "结算淡入",
    )
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .width(CiSizes.dialogCelebrateWidth)
                .alpha(contentAlpha),
            shape = CiShapes.dialog,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = CiElevation.celebrate,
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (!reducedMotion) {
                    RadiatingRings(color = accent)
                }
                Column(
                    modifier = Modifier.padding(CiSpacing.xl),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    content()
                    Button(
                        onClick = onAction ?: onDismiss,
                        shape = CiShapes.pill,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accent,
                            contentColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ),
                        modifier = Modifier.padding(top = 10.dp),
                    ) {
                        Text(actionLabel, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

/** 两圈交错扩散的光环，近似设计稿里 `ciRing` 关键帧动画。 */
@Composable
private fun RadiatingRings(color: Color) {
    val transition = rememberInfiniteTransition(label = "ring")
    listOf(0, RING_STAGGER_MILLIS).forEach { delayMillis ->
        val progress by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(RING_DURATION_MILLIS, delayMillis, LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "ring$delayMillis",
        )
        Box(
            modifier = Modifier
                .size(120.dp)
                .scale(RING_START_SCALE + (RING_END_SCALE - RING_START_SCALE) * progress)
                .alpha(RING_START_ALPHA * (1f - progress))
                .border(width = 2.dp, color = color, shape = CiShapes.pill)
        )
    }
}

private const val RING_DURATION_MILLIS = 1600
private const val RING_STAGGER_MILLIS = 500
private const val RING_START_SCALE = 0.6f
private const val RING_END_SCALE = 1.9f
private const val RING_START_ALPHA = 0.65f

/**
 * 任务卡。[focusedMinutes] 是这个任务名下所有已结束专注的分钟合计，>0 时显式展示。
 *
 * 三个动作回调都可空：复盘屏点开的是历史任务，只看不动，就一个都不传。
 */
@Composable
internal fun TaskDetailDialog(
    task: TaskEntity,
    onDismiss: () -> Unit,
    focusedMinutes: Int = 0,
    isTimerRunning: Boolean = false,
    onStart: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    onSkip: (() -> Unit)? = null,
) {
    val canStart = onStart != null && task.status == TaskStatus.PLANNED && !isTimerRunning
    CiFormDialog(
        title = task.title,
        onDismiss = onDismiss,
        confirmLabel = if (canStart) "开始专注" else null,
        onConfirm = if (canStart) onStart else null,
        confirmIcon = if (canStart) R.drawable.ic_ci_focus_timer else null,
        dismissLabel = "关闭",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(CiSpacing.xxs)) {
            Text(
                text = "${TimeFormat.date(task.epochDay)} · " +
                    "${TimeFormat.minuteOfDay(task.startMinute)} – " +
                    TimeFormat.minuteOfDay(task.endMinute),
                style = MaterialTheme.typography.bodyLarge.tabularNums(),
            )
            CiFocusedMinutesLine(focusedMinutes)
            Text(
                text = "难度：${task.difficulty.label} ×${task.difficulty.factor}" +
                    " · 状态：${task.status.label}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (task.note.isNotBlank()) {
                Text(task.note, style = MaterialTheme.typography.bodyMedium)
            }
            if (isTimerRunning && task.status == TaskStatus.PLANNED) {
                Text(
                    text = "已有进行中的专注，结束后才能开始新任务",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (onEdit != null || (onSkip != null && task.status == TaskStatus.PLANNED)) {
                Row(
                    modifier = Modifier.padding(top = CiSpacing.xs),
                    horizontalArrangement = Arrangement.spacedBy(CiSpacing.xs),
                ) {
                    onEdit?.let { TextButton(onClick = it) { Text("编辑") } }
                    if (task.status == TaskStatus.PLANNED) {
                        onSkip?.let { TextButton(onClick = it) { Text("跳过") } }
                    }
                }
            }
        }
    }
}

/**
 * 「学习了多久」那一行。单位固定用分钟——任务卡上要的是可直接跟阶梯倍率
 * （30/60 分钟两道坎）对照的数字，换算成「1小时20分」反而要在脑子里再折回去。
 *
 * 只报分钟不报倍率：这里是一个任务名下多次专注的合计，而倍率是按单次专注算的，
 * 拿合计分钟去反推倍率会虚高（分三次坐满 90 分钟并不等于一口气坐 90 分钟）。
 * 单次的倍率在结算弹窗里给。[minutes] 为 0 时整行不画。
 */
@Composable
internal fun CiFocusedMinutesLine(minutes: Int) {
    if (minutes <= 0) return
    Text(
        text = "已学习 $minutes 分钟",
        style = MaterialTheme.typography.bodyLarge.tabularNums(),
        color = MaterialTheme.colorScheme.tertiary,
    )
}

/**
 * 结束专注：先填可选的完成描述，再点专注结果按钮落库。
 * 描述会追加到任务备注里（自由专注则落到流水备注），点结果按钮即视为提交。
 */
@Composable
internal fun StopFocusDialog(onPick: (FocusOutcome, String) -> Unit, onDismiss: () -> Unit) {
    var note by remember { mutableStateOf("") }
    CiFormDialog(
        title = "这次专注的结果？",
        onDismiss = onDismiss,
        confirmLabel = null,
        onConfirm = null,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(CiSpacing.xs)) {
            CiFormField(
                value = note,
                onValueChange = { note = it },
                label = "这次做了什么？（可选）",
                singleLine = false,
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            FocusOutcome.entries.forEach { outcome ->
                Button(
                    onClick = { onPick(outcome, note) },
                    shape = CiShapes.pill,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("${outcome.label}（系数 ×${outcome.factor}）")
                }
            }
        }
    }
}

/** 坐满 30 分钟才有阶梯加成，没跨过第一道坎就不提倍率，免得写一串「×1.00」。 */
private fun durationMultiplierSuffix(minutes: Int): String {
    val multiplier = Economy.durationMultiplier(minutes)
    if (multiplier <= 1.0) return ""
    return " · 时长倍率 ×${"%.2f".format(multiplier)}"
}

@Composable
internal fun SettlementDialog(
    settlement: Settlement,
    onDismiss: () -> Unit,
    onContinue: (() -> Unit)? = null,
) {
    val isLevelUp = settlement.newLevel != null
    val accent = if (isLevelUp) {
        MaterialTheme.colorScheme.secondary
    } else {
        MaterialTheme.colorScheme.tertiary
    }
    CiCelebrateDialog(
        accent = accent,
        onDismiss = onDismiss,
        actionLabel = if (isLevelUp) "继续" else if (onContinue != null) "继续下一项" else "太棒了",
        onAction = onContinue,
    ) {
        Text(
            text = if (isLevelUp) "领域升级" else "专注入账",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CiSpacing.xs),
        ) {
            CiFunctionIcon(
                resourceId = R.drawable.ic_ci_coin,
                contentDescription = "CI 币入账",
                modifier = Modifier.size(CiSizes.featureIcon),
            )
            Text(
                text = "+${settlement.rewardCi} CI",
                style = MaterialTheme.typography.displaySmall.tabularNums(),
                color = accent,
                textAlign = TextAlign.Center,
            )
        }
        Text(
            text = "专注 ${settlement.minutes} 分钟" +
                durationMultiplierSuffix(settlement.minutes) +
                if (settlement.expGained > 0) " · 经验 +${settlement.expGained}" else "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (settlement.checkinRewardCi > 0) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CiSpacing.xs),
            ) {
                CiFunctionIcon(
                    resourceId = R.drawable.ic_ci_streak,
                    contentDescription = "连续打卡",
                    modifier = Modifier.size(CiSizes.actionIcon),
                )
                Text(
                    text = "连续打卡 ${settlement.checkinStreak} 天，" +
                        "额外 +${settlement.checkinRewardCi} CI",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
            }
        }
        settlement.newLevel?.let { level ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CiSpacing.xs),
            ) {
                CiFunctionIcon(
                    resourceId = R.drawable.ic_ci_level_up,
                    contentDescription = "领域升级",
                    modifier = Modifier.size(CiSizes.actionIcon),
                )
                Text(
                    text = "头衔升至 Lv.$level，奖励 +${settlement.levelUpRewardCi} CI",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** 一句话调整相关的三个提示弹窗。 */
@Composable
internal fun NlDialogs(state: TodayViewModel.NlState, viewModel: TodayViewModel) {
    when (state) {
        is TodayViewModel.NlState.BlockerPreview -> AlertDialog(
            onDismissRequest = viewModel::dismissNl,
            shape = CiShapes.dialog,
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(CiSpacing.xs),
                ) {
                    CiFunctionIcon(
                        resourceId = R.drawable.ic_ci_blocker,
                        contentDescription = null,
                        modifier = Modifier.size(CiSizes.actionIcon),
                    )
                    Text("解析出以下占位时段")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(CiSpacing.xxs)) {
                    state.blockers.forEach {
                        Text("· ${it.date} ${it.start}–${it.end}  ${it.title}")
                    }
                    Text(
                        text = "确认后这些时段将不可安排任务，并自动重排受影响的日程",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmBlockers(state.blockers) },
                    shape = CiShapes.pill,
                ) { Text("确认并重排") }
            },
            dismissButton = { TextButton(onClick = viewModel::dismissNl) { Text("取消") } },
        )
        is TodayViewModel.NlState.Diff -> AlertDialog(
            onDismissRequest = {},
            shape = CiShapes.dialog,
            title = { Text("重排预览") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(CiSpacing.xxs)) {
                    state.lines.forEach { Text("· $it") }
                }
            },
            confirmButton = {
                Button(onClick = { viewModel.applyDiff(state) }, shape = CiShapes.pill) {
                    Text("应用")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelDiff) { Text("放弃预览") }
            },
        )
        is TodayViewModel.NlState.Error -> AlertDialog(
            onDismissRequest = viewModel::dismissNl,
            shape = CiShapes.dialog,
            title = { Text("解析失败") },
            text = { Text(state.message) },
            confirmButton = { TextButton(onClick = viewModel::dismissNl) { Text("知道了") } },
        )
        else -> Unit
    }
}
