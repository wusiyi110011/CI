package com.wsy.ci.feature.today

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wsy.ci.CiApp
import com.wsy.ci.core.data.Settlement
import com.wsy.ci.core.db.DomainEntity
import com.wsy.ci.core.db.QuestEntity
import com.wsy.ci.core.db.SessionEntity
import com.wsy.ci.core.db.TaskEntity
import com.wsy.ci.core.economy.FocusOutcome
import com.wsy.ci.core.util.TimeFormat
import com.wsy.ci.widget.CiWidgetUpdater
import com.wsy.ci.widget.TimerService
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TodayViewModel(app: Application) : AndroidViewModel(app) {

    private val container = (app as CiApp).container
    private val db = container.db
    private val today: Long = LocalDate.now().toEpochDay()

    val tasks: StateFlow<List<TaskEntity>> = db.taskDao().observeByDay(today)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val sessions: StateFlow<List<SessionEntity>> = db.sessionDao()
        .observeByTimeRange(TimeFormat.dayStartMillis(today), TimeFormat.dayEndMillis(today))
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val runningSession: StateFlow<SessionEntity?> = db.sessionDao().observeOpenSession()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val domains: StateFlow<List<DomainEntity>> = db.domainDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val quests: StateFlow<List<QuestEntity>> = db.questDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val balance: StateFlow<Long> = db.ledgerDao().observeBalance()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    /** 最近一次结算结果，驱动入账提示与升级庆祝弹窗。 */
    val lastSettlement = MutableStateFlow<Settlement?>(null)

    init {
        viewModelScope.launch { container.shopRepository.ensureTodayPicks() }
    }

    fun startTimer(task: TaskEntity?) {
        TimerService.start(getApplication(), task?.id, task?.title ?: "自由专注")
        viewModelScope.launch { CiWidgetUpdater.updateAll(getApplication()) }
    }

    fun stopTimer(focus: FocusOutcome) {
        viewModelScope.launch {
            val settlement = container.timerRepository.stopSession(focus)
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

    fun deleteTask(task: TaskEntity) {
        viewModelScope.launch {
            db.taskDao().delete(task)
            CiWidgetUpdater.updateAll(getApplication())
        }
    }

    fun skipTask(task: TaskEntity) {
        viewModelScope.launch {
            db.taskDao().update(task.copy(status = com.wsy.ci.core.db.TaskStatus.SKIPPED))
        }
    }

    fun addDomain(name: String, onCreated: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val id = db.domainDao().insert(DomainEntity(name = name))
            onCreated(id)
        }
    }
}
