package io.github.adgjmptw2.diet.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.adgjmptw2.diet.BuildConfig
import io.github.adgjmptw2.diet.data.DietDatabase
import io.github.adgjmptw2.diet.data.auth.TokenStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import androidx.room.InvalidationTracker
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted parameters: WorkerParameters,
    private val tokens: TokenStore,
    private val sync: SyncRepository,
    private val scheduler: SyncScheduler,
) : CoroutineWorker(appContext, parameters) {
    override suspend fun doWork(): Result {
        if (!tokens.snapshot().state.canSync || BuildConfig.API_BASE_URL.isBlank()) return Result.success()
        return try {
            if (sync.sync()) {
                scheduler.afterSync()
                Result.success()
            } else Result.retry()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: LoginRequiredException) {
            Result.success()
        } catch (failure: ApiException) {
            if (failure.status == 429 || failure.status >= 500) Result.retry() else Result.failure()
        } catch (_: IOException) {
            Result.retry()
        } catch (_: Exception) {
            Result.failure()
        }
    }
}

@Singleton
class SyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tokens: TokenStore,
    private val db: DietDatabase,
    private val sync: SyncRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val work get() = WorkManager.getInstance(context)
    private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
    private var started = false
    private val observer = object : InvalidationTracker.Observer("profile", "weight_entry", "meal_entry") {
        override fun onInvalidated(tables: Set<String>) {
            scope.launch {
                if (!sync.isSyncing.value && tokens.snapshot().state.canSync && sync.hasPendingLocalChanges()) {
                    enqueueChanges()
                }
            }
        }
    }

    @Synchronized
    fun start() {
        if (started) return
        started = true
        scope.launch {
            db.invalidationTracker.addObserver(observer)
            tokens.state.map { it.userId to it.canSync }.distinctUntilChanged().collect { (_, enabled) ->
                if (enabled && BuildConfig.API_BASE_URL.isNotBlank()) {
                    work.enqueueUniquePeriodicWork(DAILY, ExistingPeriodicWorkPolicy.KEEP,
                        PeriodicWorkRequestBuilder<SyncWorker>(24, TimeUnit.HOURS)
                            .setInitialDelay(24, TimeUnit.HOURS).setConstraints(network).addTag(TAG).build())
                } else cancel()
            }
        }
    }

    fun onAppOpened() {
        scope.launch {
            if (tokens.snapshot().state.canSync && BuildConfig.API_BASE_URL.isNotBlank()) {
                work.enqueueUniqueWork(OPEN, ExistingWorkPolicy.KEEP,
                    OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(network)
                        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).addTag(TAG).build())
            }
        }
    }

    suspend fun afterSync() {
        if (tokens.snapshot().state.canSync && sync.hasPendingLocalChanges()) enqueueChanges()
    }

    private fun enqueueChanges() {
        work.enqueueUniqueWork(CHANGES, ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<SyncWorker>().setInitialDelay(5, TimeUnit.SECONDS)
                .setConstraints(network).setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .addTag(TAG).build())
    }

    fun cancel() { work.cancelAllWorkByTag(TAG) }

    private companion object {
        const val TAG = "account-sync"
        const val OPEN = "sync-on-open"
        const val CHANGES = "sync-after-change"
        const val DAILY = "sync-daily"
    }
}
