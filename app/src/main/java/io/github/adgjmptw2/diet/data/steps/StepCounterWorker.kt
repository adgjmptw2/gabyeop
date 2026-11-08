package io.github.adgjmptw2.diet.data.steps

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@HiltWorker
class StepCounterWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted parameters: WorkerParameters,
    private val steps: StepsRepository,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = try {
        steps.sample()
        Result.success()
    } catch (failure: CancellationException) {
        throw failure
    } catch (_: Exception) {
        Result.retry()
    }
}

/** 센서 방식일 때만 15분마다 누적값 차이를 기록한다. Health Connect면 일하지 않는다. */
@Singleton
class StepCounterScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val work get() = WorkManager.getInstance(context)

    fun start() {
        work.enqueueUniquePeriodicWork(
            NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<StepCounterWorker>(15, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                        .build(),
                )
                .build(),
        )
    }

    fun cancel() = work.cancelUniqueWork(NAME)

    private companion object {
        const val NAME = "step-counter"
    }
}
