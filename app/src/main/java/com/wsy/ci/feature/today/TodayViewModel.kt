package com.wsy.ci.feature.today

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wsy.ci.CiApp
import com.wsy.ci.core.data.Settlement
import com.wsy.ci.core.db.BlockerEntity
import com.wsy.ci.core.db.DomainEntity
import com.wsy.ci.core.db.QuestEntity
import com.wsy.ci.core.db.SessionEntity
import com.wsy.ci.core.db.TaskEntity
import com.wsy.ci.core.economy.FocusOutcome
import com.wsy.ci.core.scheduler.RescheduleResult
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModel(app: Application) : AndroidViewModel(app) {

    private val container = (app as CiApp).container
    private val db = container.db

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
    val tasks: StateFlow<List<TaskEntity>> = todayEpochDay
        .flatMapLatest { today -> db.taskDao().observeByRange(today - 1, today) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

    val runningSession: StateFlow<SessionEntity?> = db.sessionDao().observeOpenSession()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

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
    val lastSettlement = MutableStateFlow<Settlement?>(null)

    init {
        viewModelScope.launch {
            // 顺序不能反：空货架抽不出今日精选
            container.shopRepository.ensureSeedItems()
            container.shopRepository.ensureTodayPicks()
        }
    }

    fun startTimer(task: TaskEntity?) {
        TimerService.start(getApplication(), task?.id, task?.title ?: "自由专注")
        viewModelScope.launch { CiWidgetUpdater.updateAll(getApplication()) }
    }

    /** [note] 为结束弹窗里填的完成描述，可空。 */
    fun stopTimer(focus: FocusOutcome, note: String = "") {
        viewModelScope.launch {
            val settlement = container.timerRepository.stopSession(focus, note)
            lastSettlement.value = settlement
            TimerService.stop(getApplication())
            CiWidgetUpdater.updateAll(getApplication())
        }
    }

    fun dismissSettlement() {
        lastSettlement.value = null
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
            val taskId = db.taskDao().insert(task)
            db.sessionDao().byId(sessionId)?.let { session ->
                db.sessionDao().update(
                    session.copy(
                        taskId = taskId,
                        domainId = task.domainId ?: session.domainId,
                        questId = task.questId ?: session.questId,
                    )
                )
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
