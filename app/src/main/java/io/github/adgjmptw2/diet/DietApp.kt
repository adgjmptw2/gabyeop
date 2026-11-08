package io.github.adgjmptw2.diet

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
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

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        syncScheduler.start()
        fastGoalScheduler.start()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { reminderScheduler.restore() }
    }
}
