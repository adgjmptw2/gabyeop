package io.github.adgjmptw2.diet

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import io.github.adgjmptw2.diet.data.FoodRepository
import io.github.adgjmptw2.diet.data.steps.StepsRepository
import io.github.adgjmptw2.diet.data.reminder.ReminderScheduler
import io.github.adgjmptw2.diet.data.reminder.FastGoalScheduler
import io.github.adgjmptw2.diet.data.sync.SyncScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class DietApp : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var syncScheduler: SyncScheduler
    @Inject lateinit var reminderScheduler: ReminderScheduler
    @Inject lateinit var fastGoalScheduler: FastGoalScheduler
    @Inject lateinit var foods: FoodRepository
    @Inject lateinit var steps: StepsRepository

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        syncScheduler.start()
        fastGoalScheduler.start()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            reminderScheduler.restore()
            // 음식 정리와 catalog 복사는 서로 기다리지 않게 각각 시작한다.
            launch { runCatching { foods.cleanupLegacyFoods() } }
            launch { runCatching { foods.prepareCatalog() } }
            // 걸음 출처를 정하고, 센서 방식이면 15분 주기 기록도 함께 예약한다.
            steps.sample()
        }
    }
}
