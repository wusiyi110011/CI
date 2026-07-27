package com.wsy.ci.feature.stats

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wsy.ci.CiApp
import com.wsy.ci.core.db.LedgerType
import com.wsy.ci.core.db.SessionEntity
import com.wsy.ci.core.db.TaskStatus
import com.wsy.ci.core.util.TimeFormat
import com.wsy.ci.llm.LlmParsed
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class StatsPeriod(val label: String) { WEEK("本周"), MONTH("本月") }

data class DomainStat(val name: String, val minutes: Int, val colorArgb: Long)

data class StatsData(
    val fromDay: Long,
    val toDay: Long,
    val totalMinutes: Int = 0,
    val byDomain: List<DomainStat> = emptyList(),
    val plannedCount: Int = 0,
    val doneCount: Int = 0,
    val skippedCount: Int = 0,
    val plannedMinutes: Int = 0,
    val actualMinutes: Int = 0,
    /** 下标 (星期0~6, 小时0~23) → 专注分钟。 */
    val heat: List<IntArray> = List(7) { IntArray(24) },
    val earnedCi: Long = 0,
    val spentCi: Long = 0,
    /** 每日专注分钟（打卡格 + 月热力图数据源）。 */
    val minutesByDay: Map<Long, Int> = emptyMap(),
) {
    val completionRate: Float
        get() = if (plannedCount == 0) 0f else doneCount.toFloat() / plannedCount

    /** 预估偏差：实际/计划分钟。 */
    val estimateRatio: Float?
        get() = if (plannedMinutes == 0 || actualMinutes == 0) null
        else actualMinutes.toFloat() / plannedMinutes
}

class StatsViewModel(app: Application) : AndroidViewModel(app) {

    private val container = (app as CiApp).container
    private val db = container.db

    val period = MutableStateFlow(StatsPeriod.WEEK)
    val data = MutableStateFlow<StatsData?>(null)
    val analysis = MutableStateFlow<String?>(null)
    val analyzing = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)

    init {
        refresh()
    }

    fun setPeriod(p: StatsPeriod) {
        period.value = p
        analysis.value = null
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val today = LocalDate.now()
            val from = when (period.value) {
                StatsPeriod.WEEK -> today.with(DayOfWeek.MONDAY)
                StatsPeriod.MONTH -> today.withDayOfMonth(1)
            }.toEpochDay()
            val to = today.toEpochDay()
            data.value = computeStats(from, to)
        }
    }

    private suspend fun computeStats(fromDay: Long, toDay: Long): StatsData {
        val sessions = db.sessionDao()
            .byTimeRange(TimeFormat.dayStartMillis(fromDay), TimeFormat.dayEndMillis(toDay))
            .filter { it.endAt != null }
        val tasks = db.taskDao().byRange(fromDay, toDay)
        val ledger = db.ledgerDao()
            .byTimeRange(TimeFormat.dayStartMillis(fromDay), TimeFormat.dayEndMillis(toDay))
        val domains = db.domainDao().observeAll().first()

        val minutesOf = { s: SessionEntity -> (((s.endAt ?: s.startAt) - s.startAt) / 60_000).toInt() }
        val totalMinutes = sessions.sumOf(minutesOf)

        val domainName = domains.associateBy({ it.id }, { it })
        val byDomain = sessions.groupBy { it.domainId }
            .map { (id, list) ->
                val d = id?.let { domainName[it] }
                DomainStat(d?.name ?: "未分类", list.sumOf(minutesOf), d?.colorArgb ?: 0xFF9E9E9E)
            }
            .sortedByDescending { it.minutes }

        val heat = List(7) { IntArray(24) }
        val minutesByDay = mutableMapOf<Long, Int>()
        sessions.forEach { s ->
            val start = Instant.ofEpochMilli(s.startAt).atZone(ZoneId.systemDefault())
            val dow = start.dayOfWeek.value - 1
            var remaining = minutesOf(s)
            var hour = start.hour
            var minuteInHour = start.minute
            while (remaining > 0 && hour < 24) {
                val inThisHour = minOf(remaining, 60 - minuteInHour)
                heat[dow][hour] += inThisHour
                remaining -= inThisHour
                hour++
                minuteInHour = 0
            }
            val day = TimeFormat.millisToEpochDay(s.startAt)
            minutesByDay[day] = (minutesByDay[day] ?: 0) + minutesOf(s)
        }

        return StatsData(
            fromDay = fromDay,
            toDay = toDay,
            totalMinutes = totalMinutes,
            byDomain = byDomain,
            plannedCount = tasks.size,
            doneCount = tasks.count { it.status == TaskStatus.DONE },
            skippedCount = tasks.count { it.status == TaskStatus.SKIPPED },
            plannedMinutes = tasks.sumOf { it.endMinute - it.startMinute },
            actualMinutes = totalMinutes,
            heat = heat,
            earnedCi = ledger.filter { it.amount > 0 }.sumOf { it.amount },
            spentCi = -ledger.filter { it.amount < 0 && it.type == LedgerType.SPEND_SHOP }.sumOf { it.amount },
            minutesByDay = minutesByDay,
        )
    }

    /** 把统计摘要喂给 LLM 拿洞察（显式按钮触发，可不用）。 */
    fun analyze() {
        val d = data.value ?: return
        viewModelScope.launch {
            analyzing.value = true
            val summary = buildString {
                appendLine("统计周期：${TimeFormat.shortDate(d.fromDay)} ~ ${TimeFormat.shortDate(d.toDay)}")
                appendLine("总专注：${TimeFormat.duration(d.totalMinutes)}")
                appendLine("按领域：${d.byDomain.joinToString { "${it.name} ${it.minutes}分钟" }}")
                appendLine("任务：计划 ${d.plannedCount} 完成 ${d.doneCount} 跳过 ${d.skippedCount}")
                appendLine("计划分钟 ${d.plannedMinutes} vs 实际分钟 ${d.actualMinutes}")
                val bestHours = (0..23).sortedByDescending { h -> d.heat.sumOf { it[h] } }.take(3)
                appendLine("专注最多的时段：${bestHours.joinToString { "${it}点" }}")
                appendLine("CI币：入 ${d.earnedCi} 出 ${d.spentCi}")
            }
            when (val r = container.llmService.analyzeStats(summary)) {
                is LlmParsed.Ok -> analysis.value = r.value
                is LlmParsed.Err -> message.value = r.message
            }
            analyzing.value = false
        }
    }

    /** 导出周期内 sessions 明细 CSV 到系统下载目录。 */
    fun exportCsv() {
        val d = data.value ?: return
        viewModelScope.launch {
            try {
                val sessions = db.sessionDao()
                    .byTimeRange(TimeFormat.dayStartMillis(d.fromDay), TimeFormat.dayEndMillis(d.toDay))
                    .filter { it.endAt != null }
                val tasks = db.taskDao().byRange(d.fromDay, d.toDay).associateBy { it.id }
                val csv = buildString {
                    appendLine("date,start,end,task,minutes,reward_ci,exp")
                    sessions.forEach { s ->
                        val mins = (((s.endAt ?: s.startAt) - s.startAt) / 60_000).toInt()
                        appendLine(
                            listOf(
                                LocalDate.ofEpochDay(TimeFormat.millisToEpochDay(s.startAt)),
                                TimeFormat.clock(s.startAt),
                                TimeFormat.clock(s.endAt ?: s.startAt),
                                "\"${s.taskId?.let { tasks[it]?.title } ?: "自由专注"}\"",
                                mins, s.rewardCi, s.expGained,
                            ).joinToString(",")
                        )
                    }
                }
                val name = "ci_report_${LocalDate.ofEpochDay(d.fromDay)}_${LocalDate.ofEpochDay(d.toDay)}.csv"
                val uri = CsvExporter.saveToDownloads(getApplication(), name, csv)
                message.value = if (uri != null) "已导出到下载目录：$name" else "导出失败"
            } catch (e: Exception) {
                message.value = "导出失败：${e.message}"
            }
        }
    }

    fun dismissMessage() {
        message.value = null
    }
}
