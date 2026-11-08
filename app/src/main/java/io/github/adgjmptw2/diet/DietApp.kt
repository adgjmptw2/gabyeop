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
            // catalog 복사가 끝난 뒤에만 옛 기본 음식을 정리한다(복사 실패 시 옛 목록 유지).
            launch { if (runCatching { foods.prepareCatalog() }.isSuccess) runCatching { foods.cleanupLegacyFoods() } }
            // 걸음 출처를 정하고, 센서 방식이면 15분 주기 기록도 함께 예약한다.
            runCatching { steps.sample() }
        }
    }
}
