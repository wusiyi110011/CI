package com.wsy.ci.feature.quest

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wsy.ci.CiApp
import com.wsy.ci.core.db.DomainEntity
import com.wsy.ci.core.db.QuestEntity
import com.wsy.ci.core.db.QuestStatus
import com.wsy.ci.core.db.QuestType
import com.wsy.ci.core.title.Titles
import com.wsy.ci.llm.LlmParsed
import com.wsy.ci.llm.RoutePlan
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** AI 路线生成的界面状态。 */
sealed interface RouteGenState {
    data object Idle : RouteGenState
    data object Loading : RouteGenState
    data class Preview(val plan: RoutePlan, val weeklyHours: Int) : RouteGenState
    data class Error(val message: String) : RouteGenState
}

class QuestViewModel(app: Application) : AndroidViewModel(app) {

    private val container = (app as CiApp).container
    private val db = container.db

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

    // ---------- AI 学习路线 ----------

    val routeGen = MutableStateFlow<RouteGenState>(RouteGenState.Idle)

    fun generateRoute(domainName: String, weeklyHours: Int, goal: String) {
        viewModelScope.launch {
            routeGen.value = RouteGenState.Loading
            routeGen.value = when (val r = container.llmService.generateRoute(domainName, weeklyHours, goal)) {
                is LlmParsed.Ok -> RouteGenState.Preview(r.value, weeklyHours)
                is LlmParsed.Err -> RouteGenState.Error(r.message)
            }
        }
    }

    /** 确认路线：建/复用领域（写入 LLM 头衔线）+ 建主线（章节存 chaptersJson）。 */
    fun confirmRoute(plan: RoutePlan) {
        viewModelScope.launch {
            val activeMains = db.questDao().activeByType(QuestType.MAIN)
            if (activeMains.size >= 2) {
                message.value = "主线已满 2 条，先完成或归档一条再生成"
                routeGen.value = RouteGenState.Idle
                return@launch
            }
            val existing = domains.value.firstOrNull { it.name == plan.domain }
            val titlesJson = plan.titles.takeIf { it.size >= 6 }?.let { Titles.encode(it.take(6)) }
            val domainId = if (existing != null) {
                if (titlesJson != null && existing.titlesJson == null) {
                    db.domainDao().update(existing.copy(titlesJson = titlesJson))
                }
                existing.id
            } else {
                db.domainDao().insert(DomainEntity(name = plan.domain, titlesJson = titlesJson))
            }
            val totalHours = plan.chapters.sumOf { it.hours }
            db.questDao().insert(
                QuestEntity(
                    domainId = domainId,
                    type = QuestType.MAIN,
                    title = "${plan.domain}学习路线",
                    description = "共 ${plan.chapters.size} 章，预估 ${"%.0f".format(totalHours)} 小时",
                    chaptersJson = Json.encodeToString(plan.chapters),
                )
            )
            message.value = "✅ 主线已创建：${plan.chapters.size} 个章节。去今日页安排第一块学习时间吧"
            routeGen.value = RouteGenState.Idle
        }
    }

    fun dismissRouteGen() {
        routeGen.value = RouteGenState.Idle
    }
}
