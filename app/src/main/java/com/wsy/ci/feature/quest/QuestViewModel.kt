package com.wsy.ci.feature.quest

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wsy.ci.CiApp
import com.wsy.ci.core.db.DomainEntity
import com.wsy.ci.core.db.QuestEntity
import com.wsy.ci.core.db.QuestStatus
import com.wsy.ci.core.db.QuestType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class QuestViewModel(app: Application) : AndroidViewModel(app) {

    private val db = (app as CiApp).container.db

    val quests: StateFlow<List<QuestEntity>> = db.questDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val domains: StateFlow<List<DomainEntity>> = db.domainDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 主线上限 2 条：超限时保存被拒并给出提示。 */
    val message = MutableStateFlow<String?>(null)

    fun saveQuest(quest: QuestEntity) {
        viewModelScope.launch {
            if (quest.type == QuestType.MAIN && quest.status == QuestStatus.ACTIVE) {
                val activeMains = db.questDao().activeByType(QuestType.MAIN)
                    .filter { it.id != quest.id }
                if (activeMains.size >= 2) {
                    message.value = "主线最多同时进行 2 条，先完成或归档一条吧"
                    return@launch
                }
            }
            if (quest.id == 0L) db.questDao().insert(quest) else db.questDao().update(quest)
        }
    }

    fun completeQuest(quest: QuestEntity) {
        viewModelScope.launch { db.questDao().update(quest.copy(status = QuestStatus.DONE)) }
    }

    fun archiveQuest(quest: QuestEntity) {
        viewModelScope.launch { db.questDao().update(quest.copy(status = QuestStatus.ARCHIVED)) }
    }

    fun addDomain(name: String) {
        viewModelScope.launch { db.domainDao().insert(DomainEntity(name = name)) }
    }

    fun dismissMessage() {
        message.value = null
    }
}
