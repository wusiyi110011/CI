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

import android.app.Application
import androidx.room.withTransaction
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wsy.ci.CiApp
import com.wsy.ci.core.data.Settlement
import com.wsy.ci.core.db.BlockerEntity
import com.wsy.ci.core.db.DomainEntity
import com.wsy.ci.core.db.QuestEntity
import com.wsy.ci.core.db.SessionEntity
import com.wsy.ci.core.db.TaskEntity
import com.wsy.ci.core.db.TaskStatus
import com.wsy.ci.core.economy.FocusOutcome
import com.wsy.ci.core.scheduler.RescheduleResult
import com.wsy.ci.core.scheduler.endedAt
import com.wsy.ci.core.timeline.DaySegments
import com.wsy.ci.core.timeline.TaskSegment
import com.wsy.ci.core.util.TimeFormat
import com.wsy.ci.core.util.currentEpochDayFlow
import com.wsy.ci.llm.LlmParsed
import com.wsy.ci.llm.ParsedBlocker
import com.wsy.ci.widget.CiWidgetUpdater
import com.wsy.ci.widget.TimerService
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 合并两条 Room 查询的计时快照。今日记录已经出现同 id 的已结束记录时，
 * 即使 open-session 查询还滞留在旧值，也必须以已结束事实为准。
 */
internal fun reconcileRunningSession(
    open: SessionEntity?,
    rangedSessions: List<SessionEntity>,
): SessionEntity? {
    val rangedOpen = rangedSessions.lastOrNull { it.endAt == null }
    return when {
        rangedOpen != null -> rangedOpen
        open != null && rangedSessions.any { it.id == open.id } -> null
        else -> open
    }
}

/** 结束专注后、Room 任务流追上事务前，先把刚提交的状态投影到【今日】。 */
internal data class TaskStopProjection(
    val taskId: Long,
    val status: TaskStatus,
    val endMinute: Int,
)

internal fun applyTaskStopProjection(
    tasks: List<TaskEntity>,
    projection: TaskStopProjection?,
): List<TaskEntity> {
    if (projection == null) return tasks
    return tasks.map { task ->
        if (task.id == projection.taskId) {
            task.copy(status = projection.status, endMinute = projection.endMinute)
        } else {
            task
        }
    }
}

/** 补录任务只建立关联；领域和任务线是开工时的结算快照，不能事后改写。 */
internal fun attachTaskSnapshot(session: SessionEntity, taskId: Long): SessionEntity =
    session.copy(taskId = taskId)

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModel(app: Application) : AndroidViewModel(app) {

    private val container = (app as CiApp).container
    private val db = container.db

    // 以下三个流刻意用 Eagerly 而非全工程统一的 WhileSubscribed(5000)：
    // init 块里有常驻收集器依赖 observedTasks / runningSession（清投影、清停止标记），
    // todayEpochDay 则被 Eagerly 的 observedTasks 常驻订阅；换 WhileSubscribed 也会因
    // 内部订阅而永不休眠，不如显式声明「本来就是常驻」。
    val todayEpochDay: StateFlow<Long> = currentEpochDayFlow()
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            LocalDate.now().toEpochDay(),
        )

    /**
     * 今日时间线的素材。查询多带上昨天：跨零点的任务和专注属于昨天，
     * 但今天要画出它们延续过来的那一段（切片交给 `DaySegments`）。
     */
    private val observedTasks: StateFlow<List<TaskEntity>> = todayEpochDay
        .flatMapLatest { today -> db.taskDao().observeByRange(today - 1, today) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _taskStopProjection = MutableStateFlow<TaskStopProjection?>(null)

    val tasks: StateFlow<List<TaskEntity>> = combine(
        observedTasks,
        _taskStopProjection,
        ::applyTaskStopProjection,
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val sessions: StateFlow<List<SessionEntity>> = todayEpochDay
        .flatMapLatest { today ->
            db.sessionDao().observeByTimeRange(
                TimeFormat.dayStartMillis(today - 1),
                TimeFormat.dayEndMillis(today),
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 今日占位事件：和计划共用时间坐标，但不属于可结算任务。 */
    val blockers: StateFlow<List<BlockerEntity>> = todayEpochDay
        .flatMapLatest { db.blockerDao().observeByDay(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 今日时间线上的计划片段（含昨天跨过来的尾巴）。 */
    val segments: StateFlow<List<TaskSegment>> = combine(tasks, todayEpochDay) { list, today ->
        DaySegments.tasksOn(list, today)
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * open-session 专用查询与今日实际记录互相校验：事务失效通知偶尔到达顺序不同，
     * 任一查询先看见「已结束」都应立即收起计时卡，不能继续展示另一个流里的旧快照。
     */
    private val observedOpenSession = db.sessionDao().observeOpenSession()
    val runningSession: StateFlow<SessionEntity?> = combine(
        observedOpenSession,
        sessions,
        ::reconcileRunningSession,
    ).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /**
     * 用户点下专注结果的真实时刻。事务提交前 UI 用它冻结计时，避免结算排队时继续跳秒；
     * 等 open session 的 Room 流确认变空后再清除，防止提交与失效通知之间闪回旧计时。
     */
    private val _stopRequestedAt = MutableStateFlow<Long?>(null)
    val stopRequestedAt: StateFlow<Long?> = _stopRequestedAt.asStateFlow()

    /**
     * 计时中的任务。单独查而不是从 [tasks] 里找：任务线详情和日程屏都能对
     * 非今天的任务开始专注，那种任务不在今日列表里。
     */
    val runningTask: StateFlow<TaskEntity?> = runningSession
        .flatMapLatest { session ->
            session?.taskId?.let { db.taskDao().observeById(it) } ?: flowOf(null)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val domains: StateFlow<List<DomainEntity>> = db.domainDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val quests: StateFlow<List<QuestEntity>> = db.questDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val balance: StateFlow<Long> = db.ledgerDao().observeBalance()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    /** 某任务累计学习分钟（含历次专注），任务卡展示用。 */
    fun focusMinutes(taskId: Long) = db.sessionDao().observeFocusMillis(taskId)
        .map { TimeFormat.millisToMinutes(it) }

    /** 最近一次结算结果，驱动入账提示与升级庆祝弹窗。 */
    private val _lastSettlement = MutableStateFlow<Settlement?>(null)
    val lastSettlement: StateFlow<Settlement?> = _lastSettlement.asStateFlow()

    init {
        viewModelScope.launch {
            // 顺序不能反：空货架抽不出今日精选
            container.shopRepository.ensureSeedItems()
            container.shopRepository.ensureTodayPicks()
        }
        viewModelScope.launch {
            runningSession.collect { session ->
                if (session == null) _stopRequestedAt.value = null
            }
        }
        viewModelScope.launch {
            observedTasks.collect { current ->
                val projection = _taskStopProjection.value ?: return@collect
                val task = current.firstOrNull { it.id == projection.taskId } ?: return@collect
                if (task.status == projection.status && task.endMinute == projection.endMinute) {
                    _taskStopProjection.value = null
                }
            }
        }
    }

    fun startTimer(task: TaskEntity?) {
        if (_stopRequestedAt.value != null) return
        TimerService.start(getApplication(), task?.id, task?.title ?: "自由专注")
        viewModelScope.launch { CiWidgetUpdater.updateAll(getApplication()) }
    }

    /** [note] 为结束弹窗里填的完成描述，可空。 */
    fun stopTimer(focus: FocusOutcome, note: String = "") {
        val stoppedAt = System.currentTimeMillis()
        if (!_stopRequestedAt.compareAndSet(expect = null, update = stoppedAt)) return
        val taskId = runningSession.value?.taskId
        taskId?.let { id ->
            tasks.value.firstOrNull { it.id == id }?.let { task ->
                val ended = endedAt(
                    task = task,
                    endEpochDay = TimeFormat.millisToEpochDay(stoppedAt),
                    endMinute = TimeFormat.millisToMinuteOfDay(stoppedAt),
                )
                _taskStopProjection.value = TaskStopProjection(
                    taskId = id,
                    status = if (focus == FocusOutcome.ABANDONED) {
                        TaskStatus.PLANNED
                    } else {
                        TaskStatus.DONE
                    },
                    endMinute = ended.endMinute,
                )
            }
        }
        viewModelScope.launch {
            try {
                val settlement = container.timerRepository.stopSession(
                    focus = focus,
                    note = note,
                    stoppedAtMillis = stoppedAt,
                )
                _lastSettlement.value = settlement
                // 返回 null 也表示数据库已确认没有 open session，旧服务同样必须撤掉。
                TimerService.stop(getApplication())
                CiWidgetUpdater.updateAll(getApplication())
                if (settlement == null) _stopRequestedAt.value = null
            } catch (error: Throwable) {
                _stopRequestedAt.value = null
                _taskStopProjection.value = null
                throw error
            }
        }
    }

    fun dismissSettlement() {
        _lastSettlement.value = null
    }

    fun saveTask(task: TaskEntity) {
        viewModelScope.launch {
            if (task.id == 0L) db.taskDao().insert(task) else db.taskDao().update(task)
            CiWidgetUpdater.updateAll(getApplication())
        }
    }

    /**
     * 把一次自由专注补录成任务：新建任务并让 session 挂上去。
     * 已经结算过的 CI 币和经验不重算——那是当时按实际投入发的，事后改名不该改账。
     */
    fun attachTaskToSession(task: TaskEntity, sessionId: Long) {
        viewModelScope.launch {
            db.withTransaction {
                val session = db.sessionDao().byId(sessionId) ?: return@withTransaction
                val taskId = db.taskDao().insert(task)
                db.sessionDao().update(attachTaskSnapshot(session, taskId))
            }
            CiWidgetUpdater.updateAll(getApplication())
        }
    }

    /** 删掉一次专注记录（连带撤回它发出的 CI 币与经验，见 `TimerRepository.deleteSession`）。 */
    fun deleteSession(sessionId: Long) {
        viewModelScope.launch {
            container.timerRepository.deleteSession(sessionId)
            CiWidgetUpdater.updateAll(getApplication())
        }
    }

    fun deleteTask(task: TaskEntity) {
        viewModelScope.launch {
            db.taskDao().delete(task)
            CiWidgetUpdater.updateAll(getApplication())
        }
    }

    fun skipTask(task: TaskEntity) {
        viewModelScope.launch {
            db.taskDao().update(task.copy(status = com.wsy.ci.core.db.TaskStatus.SKIPPED))
            CiWidgetUpdater.updateAll(getApplication())
        }
    }

    fun addDomain(name: String, onCreated: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val id = db.domainDao().insert(DomainEntity(name = name))
            onCreated(id)
        }
    }

    // ---------- 一句话调整（NL → blocker → 重排 diff → 确认） ----------
    // 状态机与重排/撤销逻辑已抽到 RescheduleFlow 以便语音指令复用；NlState / UndoSchedule
    // 仍嵌套在这里是因为 Kotlin 不支持嵌套 typealias，为了不动 TodayScreen.kt 里的引用。

    sealed interface NlState {
        data object Idle : NlState
        data object Loading : NlState
        data class BlockerPreview(val blockers: List<ParsedBlocker>) : NlState
        data class Diff(
            val results: List<Pair<Long, RescheduleResult>>,
            val lines: List<String>,
            val pendingBlockers: List<BlockerEntity>,
            /** 应用前各受影响任务的完整快照，用于短时撤销。 */
            val originalTasks: List<TaskEntity> = emptyList(),
        ) : NlState
        data class Error(val message: String) : NlState
    }

    /**
     * 最近一次重排的短时撤销入口。只保存在内存里，不增加 Room 字段；
     * 快照里保留完整任务实体，故位置和状态都能原样恢复。
     */
    data class UndoSchedule(
        val beforeTasks: List<TaskEntity>,
        val appliedTasks: List<TaskEntity>,
        val insertedBlockers: List<BlockerEntity>,
    )

    private val rescheduleFlow = container.rescheduleFlow

    val nlState: StateFlow<NlState> = rescheduleFlow.nlState
    val undoSchedule: StateFlow<UndoSchedule?> = rescheduleFlow.undoSchedule

    fun parseNl(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            rescheduleFlow.nlState.value = NlState.Loading
            rescheduleFlow.nlState.value = when (val r = container.llmService.parseBlockers(text)) {
                is LlmParsed.Ok -> NlState.BlockerPreview(r.value)
                is LlmParsed.Err -> NlState.Error(r.message)
            }
        }
    }

    fun confirmBlockers(parsed: List<ParsedBlocker>) = rescheduleFlow.confirmBlockers(parsed)
    fun applyDiff(diff: NlState.Diff) = rescheduleFlow.applyDiff(diff)
    fun undoLastReschedule() = rescheduleFlow.undoLastReschedule()
    fun dismissUndo() = rescheduleFlow.dismissUndo()
    fun cancelDiff() = rescheduleFlow.cancelDiff()
    fun dismissNl() = rescheduleFlow.dismissNl()
}
