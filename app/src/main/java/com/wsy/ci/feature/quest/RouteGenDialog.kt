package com.wsy.ci.feature.quest

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wsy.ci.core.util.TimeFormat
import com.wsy.ci.llm.RoutePlan

/** AI 学习路线生成：输入 → 生成中 → 预览确认，三态一个对话框。 */
@Composable
fun RouteGenDialog(
    state: RouteGenState,
    onGenerate: (domain: String, weeklyHours: Int, goal: String) -> Unit,
    onConfirm: (RoutePlan) -> Unit,
    onDismiss: () -> Unit,
) {
    when (state) {
        RouteGenState.Idle, is RouteGenState.Error -> RouteInputDialog(
            error = (state as? RouteGenState.Error)?.message,
            onGenerate = onGenerate,
            onDismiss = onDismiss,
        )
        RouteGenState.Loading -> AlertDialog(
            onDismissRequest = {},
            title = { Text("正在生成学习路线…") },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator()
                    Text("  深度推理中，可能需要十几秒", modifier = Modifier.padding(start = 12.dp))
                }
            },
            confirmButton = {},
        )
        is RouteGenState.Preview -> RoutePreviewDialog(
            plan = state.plan,
            weeklyHours = state.weeklyHours,
            onConfirm = { onConfirm(state.plan) },
            onDismiss = onDismiss,
        )
    }
}

@Composable
private fun RouteInputDialog(
    error: String?,
    onGenerate: (String, Int, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var domain by remember { mutableStateOf("") }
    var hours by remember { mutableStateOf("10") }
    var goal by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("🤖 AI 生成学习路线") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = domain, onValueChange = { domain = it },
                    label = { Text("想学什么（如：深度学习）") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = hours, onValueChange = { hours = it },
                    label = { Text("每周可投入小时数") }, singleLine = true,
                    modifier = Modifier.width(200.dp),
                )
                OutlinedTextField(
                    value = goal, onValueChange = { goal = it },
                    label = { Text("目标（可选，如：三个月后能做项目）") },
                    modifier = Modifier.fillMaxWidth(),
                )
                (localError ?: error)?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val h = hours.toIntOrNull()
                if (domain.isBlank()) { localError = "先填领域名"; return@Button }
                if (h == null || h <= 0) { localError = "每周小时数需为正整数"; return@Button }
                onGenerate(domain.trim(), h, goal.trim())
            }) { Text("生成") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun RoutePreviewDialog(
    plan: RoutePlan,
    weeklyHours: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val totalHours = plan.chapters.sumOf { it.hours }
    val weeks = if (weeklyHours > 0) kotlin.math.ceil(totalHours / weeklyHours).toInt() else 0
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("「${plan.domain}」路线预览") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
            ) {
                Text(
                    "共 ${plan.chapters.size} 章 · 预估 ${"%.0f".format(totalHours)} 小时 · 按每周 $weeklyHours 小时约 $weeks 周",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                )
                plan.chapters.forEachIndexed { i, chapter ->
                    Column {
                        Text(
                            "${i + 1}. ${chapter.title}（${TimeFormat.duration((chapter.hours * 60).toInt())}）",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        chapter.resources.take(3).forEach {
                            Text("   · $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
                if (plan.titles.size >= 6) {
                    Text(
                        "头衔线：${plan.titles.take(6).joinToString(" → ")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
        },
        confirmButton = { Button(onClick = onConfirm) { Text("确认创建主线") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("放弃") } },
    )
}
