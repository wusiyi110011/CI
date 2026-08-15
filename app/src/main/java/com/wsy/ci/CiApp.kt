package com.wsy.ci

import android.app.Application
import com.wsy.ci.core.data.ScheduleRepository
import com.wsy.ci.core.data.ShopRepository
import com.wsy.ci.core.data.TimerRepository
import com.wsy.ci.core.backup.DataBackupManager
import com.wsy.ci.core.db.CiDatabase
import com.wsy.ci.core.settings.AppSettings
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
import com.wsy.ci.voice.SpeechEngine
import com.wsy.ci.voice.VoiceCommandExecutor
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
    val appSettings = AppSettings(app)
    private val dataBackupManager = DataBackupManager(app, db, appSettings::reload)
    val dataBackupController = AppDataBackupController(app, dataBackupManager)
    val llmSettings = LlmSettings(app)
    val localModelDownloads = LocalModelDownloadManager.get(app, LocalModelSpecs.QWEN35)
    val asrDownloads = LocalModelDownloadManager.get(app, LocalModelSpecs.SENSE_VOICE)
    val speechEngine: SpeechEngine = SherpaSpeechEngine(app, asrDownloads)
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
    val voiceCommandExecutor = VoiceCommandExecutor(db, timerRepository)
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
