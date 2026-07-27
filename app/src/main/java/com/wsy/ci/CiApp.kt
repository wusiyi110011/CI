package com.wsy.ci

import android.app.Application
import com.wsy.ci.core.data.ShopRepository
import com.wsy.ci.core.data.TimerRepository
import com.wsy.ci.core.db.CiDatabase
import com.wsy.ci.work.DailyRefreshWorker

/** 手工 DI 容器：单模块小应用不引入 Hilt。 */
class AppContainer(app: Application) {
    val db: CiDatabase = CiDatabase.get(app)
    val timerRepository = TimerRepository(db)
    val shopRepository = ShopRepository(db)
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
