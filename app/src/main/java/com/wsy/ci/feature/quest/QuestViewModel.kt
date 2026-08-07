package com.wsy.ci.feature.quest

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wsy.ci.CiApp
import com.wsy.ci.core.db.DomainEntity
import com.wsy.ci.core.db.QuestEntity
import com.wsy.ci.core.db.QuestStatus
import com.wsy.ci.core.db.QuestType
import com.wsy.ci.core.db.TaskEntity
import com.wsy.ci.core.db.TaskStatus
import com.wsy.ci.core.economy.Difficulty
import com.wsy.ci.core.porting.CiImport
import com.wsy.ci.core.porting.CiImportFile
import com.wsy.ci.core.porting.ImportParseResult
import com.wsy.ci.core.porting.ImportPreview
import com.wsy.ci.core.porting.previewPlan
import com.wsy.ci.core.quest.checkinTaskOf
import com.wsy.ci.core.title.Titles
import com.wsy.ci.core.util.TimeFormat
import com.wsy.ci.llm.LlmParsed
import com.wsy.ci.llm.RoutePlan
import com.wsy.ci.widget.CiWidgetUpdater
import com.wsy.ci.widget.TimerService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
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

/** 同时进行中的主线上限。 */
internal const val MAX_ACTIVE_MAIN_QUESTS = 4

/** 主线批量关联弹窗所需的数据快照。 */
data class BatchAssignState(
    val quest: QuestEntity,
    val tasks: List<TaskEntity>,
)

@OptIn(ExperimentalCoroutinesApi::class)
class QuestViewModel(app: Application) : AndroidViewModel(app) {

    private val container = (app as CiApp).container
    private val db = container.db

    /** 含已完成与已归档：任务屏要分「进行中」「已完成 · 已归档」两个 tab 展示。 */
    val quests: StateFlow<List<QuestEntity>> = db.questDao().observeEvery()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val domains: StateFlow<List<DomainEntity>> = db.domainDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 主线超限时保存被拒并给出提示。 */
    val message = MutableStateFlow<String?>(null)

    // ---------- 任务线详情（章节 + 具体任务 + 立即开始） ----------

    /** 当前展开详情的任务线；null 表示没有打开详情。 */
    val selectedQuestId = MutableStateFlow<Long?>(null)

    /** 展开的任务线下的全部任务，按日期 + 起始时间排。 */
    val questTasks: StateFlow<List<TaskEntity>> = selectedQuestId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else db.taskDao().observeByQuest(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val runningSession = db.sessionDao().observeOpenSession()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun openQuest(quest: QuestEntity) {
        selectedQuestId.value = quest.id
    }

    fun closeQuest() {
        selectedQuestId.value = null
    }

    // ---------- 主线批量关联任务 ----------

    val batchAssign = MutableStateFlow<BatchAssignState?>(null)

    fun openBatchAssign(quest: QuestEntity) {
        if (quest.type != QuestType.MAIN || quest.status != QuestStatus.ACTIVE) return
        viewModelScope.launch {
            batchAssign.value = BatchAssignState(quest, db.taskDao().unassigned())
        }
    }

    fun closeBatchAssign() {
        batchAssign.value = null
    }

    fun assignTasksToMain(taskIds: Set<Long>) {
        val state = batchAssign.value ?: return
        if (taskIds.isEmpty()) return
        viewModelScope.launch {
            val attached = db.taskDao().attachUnassignedToQuest(taskIds.toList(), state.quest.id)
            batchAssign.value = null
            message.value = "已将 $attached 个任务关联到「${state.quest.title}」"
            CiWidgetUpdater.updateAll(getApplication())
        }
    }

    /**
     * 从任务线详情直接开始某个任务的专注（不限当天，提前开工也允许）。
     * 开始后由界面切到今日屏，那里有计时卡，所以这里不再弹 snackbar。
     */
    fun startTimer(task: TaskEntity) {
        TimerService.start(getApplication(), task.id, task.title)
        viewModelScope.launch { CiWidgetUpdater.updateAll(getApplication()) }
    }

    /**
     * 从任务线本身开工。
     *
     * 支线是打卡型的，本来就不排时间块，但光记一条无任务的 session 事后查不到，
     * 所以就地补一个挂在这条支线上的任务再计时（见 [checkinTaskOf]，
     * 刻意不挂到它归属的主线上）。主线一般已有排好的时间块，
     * 从主线直接开工属于临时加练，仍走不挂任务的自由专注。
     */
    fun startQuestFocus(quest: QuestEntity) {
        viewModelScope.launch {
            // 计时是全局单例，已有在跑就别往库里塞一个永远开不了工的打卡块
            if (db.sessionDao().openSession() != null) {
                message.value = "已有进行中的专注，结束后才能开始新任务"
                return@launch
            }
            if (quest.type == QuestType.SIDE) {
                val now = System.currentTimeMillis()
                val task = checkinTaskOf(
                    quest = quest,
                    nowEpochDay = TimeFormat.millisToEpochDay(now),
                    nowMinute = TimeFormat.millisToMinuteOfDay(now),
                )
                val taskId = db.taskDao().insert(task)
                TimerService.start(getApplication(), taskId, quest.title)
            } else {
                TimerService.start(getApplication(), null, quest.title, quest.id)
            }
            CiWidgetUpdater.updateAll(getApplication())
        }
    }

    fun skipTask(task: TaskEntity) {
        viewModelScope.launch {
            db.taskDao().update(task.copy(status = TaskStatus.SKIPPED))
            CiWidgetUpdater.updateAll(getApplication())
        }
    }

    /** 任务线详情里点开任务卡改完存回。 */
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

    fun saveQuest(quest: QuestEntity) {
        viewModelScope.launch {
            if (quest.type == QuestType.MAIN && quest.status == QuestStatus.ACTIVE) {
                val activeMains = db.questDao().activeByType(QuestType.MAIN)
                    .filter { it.id != quest.id }
                if (activeMains.size >= MAX_ACTIVE_MAIN_QUESTS) {
                    message.value = "主线最多同时进行 $MAX_ACTIVE_MAIN_QUESTS 条，先完成或归档一条吧"
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

    /**
     * 彻底删掉一条任务线。
     *
     * 它下面的具体任务只解除关联、不删除——那些时间块背后挂着 session 与流水，
     * 是真实发生过的投入，删任务线不该把历史一并抹掉；挂靠它的支线同样只松开归属。
     */
    fun deleteQuest(quest: QuestEntity) {
        viewModelScope.launch {
            db.taskDao().detachFromQuest(quest.id)
            db.questDao().detachChildren(quest.id)
            db.questDao().delete(quest)
            closeQuest()
            message.value = "已删除「${quest.title}」，它排出的时间块保留在日程里"
            CiWidgetUpdater.updateAll(getApplication())
        }
    }

    /** 从「已完成」里捞回来接着做。主线已满时拒绝，规则与新建一致。 */
    fun restoreQuest(quest: QuestEntity) {
        viewModelScope.launch {
            if (quest.type == QuestType.MAIN) {
                val activeMains = db.questDao().activeByType(QuestType.MAIN)
                    .filter { it.id != quest.id }
                if (activeMains.size >= MAX_ACTIVE_MAIN_QUESTS) {
                    message.value = "主线最多同时进行 $MAX_ACTIVE_MAIN_QUESTS 条，先完成或归档一条吧"
                    return@launch
                }
            }
            db.questDao().update(quest.copy(status = QuestStatus.ACTIVE))
        }
    }

    /** [onCreated] 给任务卡里的「或新建领域」用：建完把新 id 回填进任务再存。 */
    fun addDomain(name: String, onCreated: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val id = db.domainDao().insert(DomainEntity(name = name))
            onCreated(id)
        }
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
            if (activeMains.size >= MAX_ACTIVE_MAIN_QUESTS) {
                message.value = "主线已满 $MAX_ACTIVE_MAIN_QUESTS 条，先完成或归档一条再生成"
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

    // ---------- JSON 导入（外部/AI 设计好的计划一键落库） ----------

    /** 校验通过、等用户点「确认导入」的一批内容。 */
    data class ImportPending(val file: CiImportFile, val preview: ImportPreview)

    /** 待确认的导入清单；null 表示还停在粘贴框。 */
    val importPending = MutableStateFlow<ImportPending?>(null)

    /** 返回给导入对话框的结果：null 表示尚未导入。 */
    val importResult = MutableStateFlow<String?>(null)

    /** 只校验、只出清单，一个字都不写库——落库要等 [confirmImport]。 */
    fun previewImport(text: String) {
        viewModelScope.launch {
            when (val parsed = CiImport.parse(text)) {
                is ImportParseResult.Err -> {
                    importResult.value = "❌ 校验未通过：\n" + parsed.errors.joinToString("\n") { "· $it" }
                }
                is ImportParseResult.Ok -> {
                    val file = parsed.file
                    val mainLimitError = checkMainLimit(file)
                    if (mainLimitError != null) {
                        importResult.value = mainLimitError
                        return@launch
                    }
                    importPending.value = ImportPending(
                        file = file,
                        // 复用/引用的判定口径要和 applyImport 里一致，否则预览说得和实际做的不是一回事
                        preview = previewPlan(
                            file = file,
                            existingDomainNames = db.domainDao().observeAll().first()
                                .map { it.name }.toSet(),
                            existingQuestTitles = db.questDao().observeAll().first()
                                .map { it.title }.toSet(),
                        ),
                    )
                }
            }
        }
    }

    fun confirmImport() {
        val pending = importPending.value ?: return
        viewModelScope.launch {
            importResult.value = applyImport(pending.file)
            importPending.value = null
        }
    }

    fun cancelImportPreview() {
        importPending.value = null
    }

    /** 主线超出上限就没必要让用户过目清单了，直接拦在预览之前。 */
    private suspend fun checkMainLimit(file: CiImportFile): String? {
        val importingMains = file.quests.count { it.type == "MAIN" }
        if (importingMains == 0) return null
        val activeMains = db.questDao().activeByType(QuestType.MAIN).size
        if (activeMains + importingMains <= MAX_ACTIVE_MAIN_QUESTS) return null
        return "❌ 主线最多同时 $MAX_ACTIVE_MAIN_QUESTS 条：当前已有 $activeMains 条，导入含 $importingMains 条"
    }

    private suspend fun applyImport(file: CiImportFile): String {
        checkMainLimit(file)?.let { return it }

        // 领域：按名字复用或新建；头衔线只在原来为空时覆盖
        var domainId: Long? = null
        file.domain?.let { d ->
            val titlesJson = d.titles.takeIf { it.size == 6 }?.let { Titles.encode(it) }
            val existing = db.domainDao().observeAll().first()
                .firstOrNull { it.name == d.name.trim() }
            domainId = if (existing != null) {
                if (titlesJson != null && existing.titlesJson == null) {
                    db.domainDao().update(existing.copy(titlesJson = titlesJson))
                }
                existing.id
            } else {
                db.domainDao().insert(DomainEntity(name = d.name.trim(), titlesJson = titlesJson))
            }
        }

        // 任务线：记录标题 → id 映射，供 tasks 引用
        val questIdByTitle = mutableMapOf<String, Long>()
        db.questDao().observeAll().first()
            .forEach { questIdByTitle[it.title] = it.id }
        val json = kotlinx.serialization.json.Json
        file.quests.forEach { q ->
            val chaptersJson = q.chapters.takeIf { it.isNotEmpty() }?.let {
                json.encodeToString(it.map { c ->
                    com.wsy.ci.llm.RouteChapter(c.title, c.hours, c.resources)
                })
            }
            val id = db.questDao().insert(
                QuestEntity(
                    domainId = domainId,
                    type = if (q.type == "MAIN") QuestType.MAIN else QuestType.SIDE,
                    title = q.title.trim(),
                    description = q.description.trim(),
                    deadlineEpochDay = q.deadline?.let { CiImport.parseDate(it) },
                    chaptersJson = chaptersJson,
                )
            )
            questIdByTitle[q.title.trim()] = id
        }

        // 具体任务
        var unresolvedQuestRefs = 0
        val tasks = file.tasks.map { t ->
            val questId = t.quest?.trim()?.let { ref ->
                questIdByTitle[ref].also { if (it == null) unresolvedQuestRefs++ }
            }
            TaskEntity(
                title = t.title.trim(),
                epochDay = CiImport.parseDate(t.date)!!,
                startMinute = CiImport.parseHm(t.start)!!,
                endMinute = CiImport.parseHm(t.end)!!,
                difficulty = CiImport.parseDifficulty(t.difficulty) ?: Difficulty.NORMAL,
                domainId = domainId,
                questId = questId,
                locked = t.locked,
                note = t.note,
            )
        }
        if (tasks.isNotEmpty()) db.taskDao().insertAll(tasks)

        return buildString {
            append("✅ 导入成功：")
            file.domain?.let { append("领域「${it.name}」、") }
            append("任务线 ${file.quests.size} 条、任务 ${file.tasks.size} 个")
            if (unresolvedQuestRefs > 0) {
                append("\n⚠️ 有 $unresolvedQuestRefs 个任务引用的任务线不存在，已按未关联导入")
            }
        }
    }

    fun dismissImportResult() {
        importResult.value = null
        importPending.value = null
    }
}
