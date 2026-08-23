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

package com.wsy.ci

import android.app.Application
import com.wsy.ci.core.data.QuestRepository
import com.wsy.ci.core.data.ScheduleRepository
import com.wsy.ci.core.data.ShopRepository
import com.wsy.ci.core.data.TimerRepository
import com.wsy.ci.core.data.VoiceStatsRepository
import com.wsy.ci.core.backup.DataBackupManager
import com.wsy.ci.core.db.CiDatabase
import com.wsy.ci.core.settings.AppSettings
import com.wsy.ci.core.voice.skill.SkillRegistry
import com.wsy.ci.core.voice.skill.VoiceSkillRouter
import com.wsy.ci.core.voice.skill.skills.AbandonTimerSkill
import com.wsy.ci.core.voice.skill.skills.ArchiveQuestSkill
import com.wsy.ci.core.voice.skill.skills.BlockTimeSkill
import com.wsy.ci.core.voice.skill.skills.CompleteQuestSkill
import com.wsy.ci.core.voice.skill.skills.CompleteTaskSkill
import com.wsy.ci.core.voice.skill.skills.CreateTaskSkill
import com.wsy.ci.core.voice.skill.skills.DeleteQuestSkill
import com.wsy.ci.core.voice.skill.skills.DeleteTaskSkill
import com.wsy.ci.core.voice.skill.skills.NavigateSkill
import com.wsy.ci.core.voice.skill.skills.LockTaskSkill
import com.wsy.ci.core.voice.skill.skills.MoveTaskSkill
import com.wsy.ci.core.voice.skill.skills.PurchaseItemSkill
import com.wsy.ci.core.voice.skill.skills.QueryDomainSkill
import com.wsy.ci.core.voice.skill.skills.QueryBalanceSkill
import com.wsy.ci.core.voice.skill.skills.QueryCheckinSkill
import com.wsy.ci.core.voice.skill.skills.QueryCurrentFocusSkill
import com.wsy.ci.core.voice.skill.skills.QueryScheduleSkill
import com.wsy.ci.core.voice.skill.skills.QueryShopSkill
import com.wsy.ci.core.voice.skill.skills.QueryWeeklyStatsSkill
import com.wsy.ci.core.voice.skill.skills.RestoreQuestSkill
import com.wsy.ci.core.voice.skill.skills.SkipTaskSkill
import com.wsy.ci.core.voice.skill.skills.SetQuestDeadlineSkill
import com.wsy.ci.core.voice.skill.skills.SetTaskDifficultySkill
import com.wsy.ci.core.voice.skill.skills.SetTaskNoteSkill
import com.wsy.ci.core.voice.skill.skills.StartTimerSkill
import com.wsy.ci.core.voice.skill.skills.StopTimerSkill
import com.wsy.ci.core.voice.skill.skills.UndoRescheduleSkill
import com.wsy.ci.core.voice.skill.skills.UnlockTaskSkill
import com.wsy.ci.feature.settings.AppLocalModelController
import com.wsy.ci.feature.settings.AppDataBackupController
import com.wsy.ci.llm.LlmRouter
import com.wsy.ci.llm.LlmService
import com.wsy.ci.llm.LlmSettings
import com.wsy.ci.llm.MnnLlmGateway
import com.wsy.ci.llm.OpenAiCompatClient
import com.wsy.ci.localmodel.download.LocalModelDownloadManager
import com.wsy.ci.localmodel.download.LocalModelSpecs
import com.wsy.ci.localmodel.download.LocalModelVerifier
import com.wsy.ci.localmodel.download.Qwen35ModelManifest
import com.wsy.ci.localmodel.runtime.JniMnnNativeBridge
import com.wsy.ci.localmodel.runtime.LocalModelController
import com.wsy.ci.feature.schedule.RescheduleFlow
import com.wsy.ci.voice.SherpaSpeechEngine
import com.wsy.ci.voice.SherpaKeywordSpotter
import com.wsy.ci.voice.SpeechEngine
import com.wsy.ci.voice.AndroidSpeechOutput
import com.wsy.ci.voice.SpeechOutput
import com.wsy.ci.voice.VoiceCommandBus
import com.wsy.ci.voice.VoiceCorrectionStore
import com.wsy.ci.voice.VoiceMicrophoneArbiter
import com.wsy.ci.voice.VoiceWakeRuntime
import com.wsy.ci.widget.CiWidgetUpdater
import com.wsy.ci.work.DailyRefreshWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext

/** 手工 DI 容器：单模块小应用不引入 Hilt。 */
class AppContainer(app: Application) {
    val db: CiDatabase = CiDatabase.get(app)
    val timerRepository = TimerRepository(db)
    val shopRepository = ShopRepository(db)
    val questRepository = QuestRepository(db)
    val appSettings = AppSettings(app)
    private val dataBackupManager = DataBackupManager(app, db, appSettings::reload)
    val dataBackupController = AppDataBackupController(app, dataBackupManager)
    val llmSettings = LlmSettings(app)
    val localModelDownloads = LocalModelDownloadManager.get(app, LocalModelSpecs.QWEN35)
    val asrDownloads = LocalModelDownloadManager.get(app, LocalModelSpecs.SENSE_VOICE)
    val kwsDownloads = LocalModelDownloadManager.get(app, LocalModelSpecs.KWS)
    private val voiceMicrophoneArbiter = VoiceMicrophoneArbiter()
    val speechEngine: SpeechEngine = SherpaSpeechEngine(app, asrDownloads, voiceMicrophoneArbiter)
    val speechOutput: SpeechOutput by lazy { AndroidSpeechOutput(app) }
    val voiceCommandBus = VoiceCommandBus()
    val voiceCorrectionStore = VoiceCorrectionStore(app)
    private val keywordSpotter = SherpaKeywordSpotter(app, kwsDownloads, voiceMicrophoneArbiter)
    val voiceWakeRuntime = VoiceWakeRuntime(appSettings, keywordSpotter, speechEngine, voiceCommandBus)
    private val localRuntime = LocalModelController(
        bridge = JniMnnNativeBridge(),
        modelPath = localModelDownloads.activeDirectory.resolve("config.json").absolutePath,
        preflight = {
            withContext(Dispatchers.IO) {
                Qwen35ModelManifest.manifest.files.forEach { expected ->
                    val file = localModelDownloads.activeDirectory.resolve(expected.path)
                    check(LocalModelVerifier.verify(file, expected.size, expected.sha256)) {
                        "${expected.path} 大小或 SHA-256 不匹配"
                    }
                }
            }
        },
    )
    val localModelGateway = MnnLlmGateway(localRuntime)
    val localModelController = AppLocalModelController(app, localModelDownloads, localModelGateway)
    private val cloudGateway = OpenAiCompatClient(llmSettings)
    val llmService = LlmService(LlmRouter(llmSettings, cloudGateway, localModelGateway))
    val scheduleRepository = ScheduleRepository(db)
    val voiceStatsRepository = VoiceStatsRepository(db)

    /** 应用全局的协程作用域，托管不属于任何单个 ViewModel 生命周期的服务。 */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /**
     * 容器级单例：今日页的「一句话调整」和语音指令共用同一条重排 diff / 撤销状态，
     * 这样语音触发的 diff 预览也能在今日页原有的弹窗里看到，不用另建一套 UI。
     */
    val rescheduleFlow = RescheduleFlow(
        schedule = scheduleRepository,
        scope = appScope,
        onApplied = { CiWidgetUpdater.updateAll(app) },
    )

    /**
     * 语音技能注册表：登记顺序即规则匹配优先级。执行依赖（Context/repo）不在这里注入，
     * 由 `VoiceViewModel` 组装成 [com.wsy.ci.core.voice.skill.SkillExecutionContext]。
     * 加新语音能力 = 写一个 AppSkill + 在这里登记一行。
     */
    val voiceSkillRegistry = SkillRegistry(
        listOf(
            StartTimerSkill,
            CompleteTaskSkill,
            CompleteQuestSkill,
            StopTimerSkill,
            AbandonTimerSkill,
            SkipTaskSkill,
            DeleteTaskSkill,
            ArchiveQuestSkill,
            RestoreQuestSkill,
            DeleteQuestSkill,
            QueryShopSkill,
            PurchaseItemSkill,
            QueryDomainSkill,
            QueryScheduleSkill,
            QueryCurrentFocusSkill,
            QueryBalanceSkill,
            QueryCheckinSkill,
            QueryWeeklyStatsSkill,
            CreateTaskSkill,
            MoveTaskSkill,
            LockTaskSkill,
            UnlockTaskSkill,
            SetTaskDifficultySkill,
            SetTaskNoteSkill,
            SetQuestDeadlineSkill,
            UndoRescheduleSkill,
            BlockTimeSkill,
            NavigateSkill,
        )
    )
    val voiceSkillRouter = VoiceSkillRouter(voiceSkillRegistry)
}

class CiApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        DailyRefreshWorker.schedule(this)
    }
}
