package com.wsy.ci.feature.stats

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wsy.ci.CiApp
import com.wsy.ci.core.designsystem.UNCLASSIFIED_DOMAIN_COLOR_ARGB
import com.wsy.ci.core.db.LedgerType
import com.wsy.ci.core.db.SessionEntity
import com.wsy.ci.core.db.TaskEntity
import com.wsy.ci.core.db.TaskStatus
import com.wsy.ci.core.stats.sessionMinuteBuckets
import com.wsy.ci.core.util.TimeFormat
import com.wsy.ci.llm.LlmParsed
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class StatsPeriod(val label: String) { WEEK("本周"), MONTH("本月") }

data class DomainStat(val name: String, val minutes: Int, val colorArgb: Long)

/**
 * 明细列表的一行：任务本体 + 它名下所有 session 汇总出的实际投入与结算产出。
 * 一个任务可能被专注多次（选「放弃」会退回 PLANNED 可重开），所以是求和不是取单条。
 */
data class TaskRecord(
    val task: TaskEntity,
    val domainName: String,
    val actualMinutes: Int,
    val rewardCi: Long,
    val expGained: Long,
)

/** 明细列表的状态筛选。RUNNING 归到「未完成」，用户视角里它确实还没完成。 */
enum class RecordFilter(val label: String) {
    ALL("全部"),
    DONE("已完成"),
    SKIPPED("已跳过"),
    OPEN("未完成");

    fun matches(status: TaskStatus): Boolean = when (this) {
        ALL -> true
        DONE -> status == TaskStatus.DONE
        SKIPPED -> status == TaskStatus.SKIPPED
        OPEN -> status == TaskStatus.PLANNED || status == TaskStatus.RUNNING
    }
}

/**
 * 明细列表的领域筛选。不能直接用 `Long?` 表达——null 已经被「未分类任务」占用了，
 * 再拿它兼表「全部」会撞车，所以显式分成两个分支。
 */
sealed interface DomainFilter {
    data object All : DomainFilter
    data class Only(val domainId: Long?) : DomainFilter
}

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
    /** 周期内全部任务明细，未经筛选；筛选是纯函数，在 UI 层按当前条件过。 */
    val records: List<TaskRecord> = emptyList(),
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
    /** 默认落在「已完成」——这个面板的主用途就是回看做完的事。 */
    val recordFilter = MutableStateFlow(RecordFilter.DONE)
    val domainFilter = MutableStateFlow<DomainFilter>(DomainFilter.All)
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
        // 换周期后原来选中的领域可能在新周期里没有任何任务，重置回「全部」免得列表空得莫名其妙
        domainFilter.value = DomainFilter.All
        refresh()
    }

    fun setRecordFilter(f: RecordFilter) {
        recordFilter.value = f
    }

    fun setDomainFilter(f: DomainFilter) {
        domainFilter.value = f
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

        // 与结算、任务卡同一套换算（四舍五入），免得同一次专注在两处显示差一分钟
        val minutesOf = { s: SessionEntity ->
            TimeFormat.millisToMinutes((s.endAt ?: s.startAt) - s.startAt)
        }
        val totalMinutes = sessions.sumOf(minutesOf)

        val domainName = domains.associateBy({ it.id }, { it })
        val byDomain = sessions.groupBy { it.domainId }
            .map { (id, list) ->
                val d = id?.let { domainName[it] }
                DomainStat(
                    d?.name ?: "未分类",
                    list.sumOf(minutesOf),
                    d?.colorArgb ?: UNCLASSIFIED_DOMAIN_COLOR_ARGB,
                )
            }
            .sortedByDescending { it.minutes }

        val heat = List(7) { IntArray(24) }
        val minutesByDay = mutableMapOf<Long, Int>()
        sessions.forEach { s ->
            sessionMinuteBuckets(s.startAt, minutesOf(s)).forEach { bucket ->
                heat[bucket.dayOfWeekIndex][bucket.hour]++
                minutesByDay[bucket.epochDay] = (minutesByDay[bucket.epochDay] ?: 0) + 1
            }
        }

        val sessionsByTask = sessions.groupBy { it.taskId }
        val records = tasks
            .sortedWith(
                compareByDescending<TaskEntity> { it.epochDay }.thenByDescending { it.startMinute }
            )
            .map { task ->
                val own = sessionsByTask[task.id].orEmpty()
                TaskRecord(
                    task = task,
                    domainName = task.domainId?.let { domainName[it]?.name } ?: "未分类",
                    actualMinutes = own.sumOf(minutesOf),
                    rewardCi = own.sumOf { it.rewardCi },
                    expGained = own.sumOf { it.expGained },
                )
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
            records = records,
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
                        val mins = TimeFormat.millisToMinutes((s.endAt ?: s.startAt) - s.startAt)
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
