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

package com.wsy.ci.feature.stats

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wsy.ci.CiApp
import com.wsy.ci.core.designsystem.UNCLASSIFIED_DOMAIN_COLOR_ARGB
import com.wsy.ci.core.db.LedgerType
import com.wsy.ci.core.db.QuestStatus
import com.wsy.ci.core.db.QuestType
import com.wsy.ci.core.db.SessionEntity
import com.wsy.ci.core.db.TaskEntity
import com.wsy.ci.core.db.TaskStatus
import com.wsy.ci.core.stats.ReviewDigest
import com.wsy.ci.core.stats.ReviewMainQuest
import com.wsy.ci.core.stats.ReviewPeriodSnapshot
import com.wsy.ci.core.stats.ReviewSideQuest
import com.wsy.ci.core.stats.SessionTimeSlice
import com.wsy.ci.core.stats.intersectSessionTime
import com.wsy.ci.core.stats.sessionMinuteBuckets
import com.wsy.ci.core.util.TimeFormat
import com.wsy.ci.llm.LlmParsed
import com.wsy.ci.llm.ReviewAnalysis
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class StatsPeriod(val label: String) { WEEK("本周"), MONTH("本月") }

/** 复盘弹窗的状态：结果连同它对应的对比粒度一起展示。 */
data class AnalysisUi(val granularityLabel: String, val result: ReviewAnalysis)

/** 复盘摘要里黄金时段取前几个。 */
private const val REVIEW_TOP_HOURS = 3

data class DomainStat(val name: String, val minutes: Int, val colorArgb: Long)

/** 一条已结束专注及其落在当前统计周期内的裁剪片段。 */
private data class PeriodSession(
    val session: SessionEntity,
    val slice: SessionTimeSlice,
    /** CI 与经验在结束计时时结算，只归入结束时刻所在的统计周期。 */
    val settledInPeriod: Boolean,
)

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
    /** 任务线信息为快照派生值；空表示该任务未关联任务线。 */
    val questType: QuestType? = null,
    val questTitle: String? = null,
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

/** 某一类任务线的筛选；All 表示该类全部，Only 表示具体任务线。 */
sealed interface QuestFilter {
    data object All : QuestFilter
    data class Only(val questId: Long) : QuestFilter
}

/** 任务明细里已经添加到状态栏下方的筛选维度。 */
enum class RecordFilterKind(val label: String) {
    MAIN("主线"),
    SIDE("支线"),
    DOMAIN("领域"),
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
    val mainFilter = MutableStateFlow<QuestFilter>(QuestFilter.All)
    val sideFilter = MutableStateFlow<QuestFilter>(QuestFilter.All)
    val activeRecordFilters = MutableStateFlow<Set<RecordFilterKind>>(emptySet())
    val data = MutableStateFlow<StatsData?>(null)
    val analysis = MutableStateFlow<AnalysisUi?>(null)
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
        mainFilter.value = QuestFilter.All
        sideFilter.value = QuestFilter.All
        activeRecordFilters.value = emptySet()
        refresh()
    }

    fun setRecordFilter(f: RecordFilter) {
        recordFilter.value = f
    }

    fun applyDomainFilter(f: DomainFilter) {
        domainFilter.value = f
        activeRecordFilters.value += RecordFilterKind.DOMAIN
    }

    fun applyMainFilter(f: QuestFilter) {
        mainFilter.value = f
        activeRecordFilters.value += RecordFilterKind.MAIN
    }

    fun applySideFilter(f: QuestFilter) {
        sideFilter.value = f
        activeRecordFilters.value += RecordFilterKind.SIDE
    }

    fun removeRecordFilter(kind: RecordFilterKind) {
        when (kind) {
            RecordFilterKind.DOMAIN -> domainFilter.value = DomainFilter.All
            RecordFilterKind.MAIN -> mainFilter.value = QuestFilter.All
            RecordFilterKind.SIDE -> sideFilter.value = QuestFilter.All
        }
        activeRecordFilters.value -= kind
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
        val sessions = periodSessions(fromDay, toDay)
        val tasks = db.taskDao().byRange(fromDay, toDay)
        val ledger = db.ledgerDao()
            .byTimeRange(TimeFormat.dayStartMillis(fromDay), TimeFormat.dayEndMillis(toDay))
        // 历史专注仍保留已删除领域的 domainId，复盘需读取归档项才能还原领域名称。
        val domains = db.domainDao().observeEvery().first()
        val quests = db.questDao().observeEvery().first()

        // 与结算、任务卡同一套换算（四舍五入），免得同一次专注在两处显示差一分钟
        val minutesOf = { s: PeriodSession ->
            TimeFormat.millisToMinutes(s.slice.endAt - s.slice.startAt)
        }
        val totalMinutes = sessions.sumOf(minutesOf)

        val domainName = domains.associateBy({ it.id }, { it })
        val questById = quests.associateBy { it.id }
        val byDomain = sessions.groupBy { it.session.domainId }
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
            sessionMinuteBuckets(s.slice.startAt, minutesOf(s)).forEach { bucket ->
                heat[bucket.dayOfWeekIndex][bucket.hour]++
                minutesByDay[bucket.epochDay] = (minutesByDay[bucket.epochDay] ?: 0) + 1
            }
        }

        val sessionsByTask = sessions.groupBy { it.session.taskId }
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
                    rewardCi = own.sumOf { if (it.settledInPeriod) it.session.rewardCi else 0L },
                    expGained = own.sumOf { if (it.settledInPeriod) it.session.expGained else 0L },
                    questType = task.questId?.let { questById[it]?.type },
                    questTitle = task.questId?.let { questById[it]?.title },
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

    /**
     * 深度复盘（显式按钮触发，可不用）：本期 vs 上期对比 + 主线进度 + 支线连击，
     * 由 [ReviewDigest] 拼成摘要喂给 LLM，拿回结构化的洞察/风险/建议。
     */
    fun analyze() {
        if (data.value == null) return
        viewModelScope.launch {
            analyzing.value = true
            try {
                val today = LocalDate.now()
                val label = granularityLabel(period.value)
                val (curRange, prevRange) = reviewRanges(period.value, today)
                val current = reviewSnapshot(curRange.first, curRange.second)
                val previous = reviewSnapshot(prevRange.first, prevRange.second)

                // 主线/支线只报进行中的；本期投入从该期 session 按任务线归组统计
                val sessions = periodSessions(curRange.first, curRange.second)
                val minutesOf = { s: PeriodSession ->
                    TimeFormat.millisToMinutes(s.slice.endAt - s.slice.startAt)
                }
                val minutesByQuest = sessions.groupBy { it.session.questId }
                    .mapValues { (_, list) -> list.sumOf(minutesOf) }
                val focusCountByQuest = sessions.groupingBy { it.session.questId }.eachCount()
                val quests = db.questDao().observeEvery().first()
                val mains = quests
                    .filter { it.type == QuestType.MAIN && it.status == QuestStatus.ACTIVE }
                    .map { q ->
                        ReviewMainQuest(
                            title = q.title,
                            deadlineEpochDay = q.deadlineEpochDay,
                            chapterCount = ReviewDigest.chapterCount(q.chaptersJson),
                            periodMinutes = minutesByQuest[q.id] ?: 0,
                        )
                    }
                val sides = quests
                    .filter { it.type == QuestType.SIDE && it.status == QuestStatus.ACTIVE }
                    .map { q ->
                        ReviewSideQuest(
                            title = q.title,
                            streakDays = q.streakDays,
                            bestStreak = q.bestStreak,
                            lastDoneEpochDay = q.lastDoneEpochDay,
                            periodFocusCount = focusCountByQuest[q.id] ?: 0,
                        )
                    }

                val digest = ReviewDigest.build(
                    granularityLabel = label,
                    todayEpochDay = today.toEpochDay(),
                    current = current,
                    previous = previous,
                    mainQuests = mains,
                    sideQuests = sides,
                )
                when (val r = container.llmService.analyzeStats(digest)) {
                    is LlmParsed.Ok -> analysis.value = AnalysisUi(label, r.value)
                    is LlmParsed.Err -> message.value = r.message
                }
            } finally {
                analyzing.value = false
            }
        }
    }

    /** 复盘的固定粒度：本周 vs 上周 / 本月 vs 上月。上期取完整周期，本期到今天为止。 */
    private fun reviewRanges(
        p: StatsPeriod,
        today: LocalDate,
    ): Pair<Pair<Long, Long>, Pair<Long, Long>> = when (p) {
        StatsPeriod.WEEK -> {
            val monday = today.with(DayOfWeek.MONDAY)
            (monday.toEpochDay() to today.toEpochDay()) to
                (monday.minusDays(7).toEpochDay() to monday.minusDays(1).toEpochDay())
        }
        StatsPeriod.MONTH -> {
            val first = today.withDayOfMonth(1)
            (first.toEpochDay() to today.toEpochDay()) to
                (first.minusMonths(1).toEpochDay() to first.minusDays(1).toEpochDay())
        }
    }

    private fun granularityLabel(p: StatsPeriod): String = when (p) {
        StatsPeriod.WEEK -> "本周 vs 上周"
        StatsPeriod.MONTH -> "本月 vs 上月"
    }

    /** 查询所有与周期相交的已结束专注，并把两端裁剪到周期边界。 */
    private suspend fun periodSessions(fromDay: Long, toDay: Long): List<PeriodSession> {
        val from = TimeFormat.dayStartMillis(fromDay)
        val toExclusive = TimeFormat.dayStartMillis(toDay + 1)
        return db.sessionDao().endedIntersecting(from, toExclusive).mapNotNull { session ->
            val endAt = session.endAt ?: return@mapNotNull null
            intersectSessionTime(session.startAt, endAt, from, toExclusive)?.let { slice ->
                PeriodSession(
                    session = session,
                    slice = slice,
                    settledInPeriod = endAt in from until toExclusive,
                )
            }
        }
    }

    /** 复用统计屏同一套聚合口径（computeStats），映射成复盘摘要需要的轻量快照。 */
    private suspend fun reviewSnapshot(fromDay: Long, toDay: Long): ReviewPeriodSnapshot {
        val d = computeStats(fromDay, toDay)
        return ReviewPeriodSnapshot(
            fromDay = fromDay,
            toDay = toDay,
            totalMinutes = d.totalMinutes,
            plannedCount = d.plannedCount,
            doneCount = d.doneCount,
            skippedCount = d.skippedCount,
            minutesByDomain = d.byDomain.map { it.name to it.minutes },
            topHours = topHours(d.heat),
            earnedCi = d.earnedCi,
            spentCi = d.spentCi,
        )
    }

    private fun topHours(heat: List<IntArray>): List<Int> =
        (0..23).map { h -> h to heat.sumOf { it[h] } }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(REVIEW_TOP_HOURS)
            .map { it.first }

    /** 导出周期内 sessions 明细 CSV 到系统下载目录。 */
    fun exportCsv() {
        val d = data.value ?: return
        viewModelScope.launch {
            try {
                val sessions = periodSessions(d.fromDay, d.toDay)
                val taskIds = sessions.mapNotNull { it.session.taskId }.distinct()
                val tasks = if (taskIds.isEmpty()) emptyMap() else db.taskDao().byIds(taskIds).associateBy { it.id }
                val csv = buildString {
                    appendLine("date,start,end,task,minutes,reward_ci,exp")
                    sessions.forEach { periodSession ->
                        val s = periodSession.session
                        val slice = periodSession.slice
                        val mins = TimeFormat.millisToMinutes(slice.endAt - slice.startAt)
                        appendLine(
                            listOf(
                                LocalDate.ofEpochDay(TimeFormat.millisToEpochDay(slice.startAt)),
                                TimeFormat.clock(slice.startAt),
                                TimeFormat.clock(slice.endAt),
                                "\"${s.taskId?.let { tasks[it]?.title } ?: "自由专注"}\"",
                                mins,
                                if (periodSession.settledInPeriod) s.rewardCi else 0L,
                                if (periodSession.settledInPeriod) s.expGained else 0L,
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
