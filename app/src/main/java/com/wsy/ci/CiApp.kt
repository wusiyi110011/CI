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
import com.wsy.ci.localmodel.download.LocalModelVerifier
import com.wsy.ci.localmodel.download.Qwen35ModelManifest
import com.wsy.ci.localmodel.runtime.JniMnnNativeBridge
import com.wsy.ci.localmodel.runtime.LocalModelController
import com.wsy.ci.work.DailyRefreshWorker
import kotlinx.coroutines.Dispatchers
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
    val localModelDownloads = LocalModelDownloadManager.get(app)
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
