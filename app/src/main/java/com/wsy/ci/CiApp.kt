package com.wsy.ci

import android.app.Application
import com.wsy.ci.core.data.ScheduleRepository
import com.wsy.ci.core.data.ShopRepository
import com.wsy.ci.core.data.TimerRepository
import com.wsy.ci.core.db.CiDatabase
import com.wsy.ci.core.settings.AppSettings
import com.wsy.ci.llm.LlmService
import com.wsy.ci.llm.LlmSettings
import com.wsy.ci.llm.OpenAiCompatClient
import com.wsy.ci.work.DailyRefreshWorker

/** 手工 DI 容器：单模块小应用不引入 Hilt。 */
class AppContainer(app: Application) {
    val db: CiDatabase = CiDatabase.get(app)
    val timerRepository = TimerRepository(db)
    val shopRepository = ShopRepository(db)
    val appSettings = AppSettings(app)
    val llmSettings = LlmSettings(app)
    val llmService = LlmService(OpenAiCompatClient(llmSettings))
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
