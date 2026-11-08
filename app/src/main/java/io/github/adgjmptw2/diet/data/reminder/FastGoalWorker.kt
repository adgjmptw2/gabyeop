package io.github.adgjmptw2.diet.data.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.*
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.adgjmptw2.diet.MainActivity
import io.github.adgjmptw2.diet.R
import io.github.adgjmptw2.diet.data.DietDatabase
import io.github.adgjmptw2.diet.data.FastEntity
import io.github.adgjmptw2.diet.domain.Fasting
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@HiltWorker
class FastGoalWorker @AssistedInject constructor(
    @Assisted context: Context, @Assisted parameters: WorkerParameters,
    private val db: DietDatabase, private val clock: Clock,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val row = db.fastDao().active() ?: return Result.success()
        if (row.id != inputData.getString("id") || row.startAt != inputData.getLong("startAt", -1) ||
            row.targetHours != inputData.getInt("targetHours", 0)) return Result.success()
        val key = "${row.id}:${row.startAt}:${row.targetHours}"
        val prefs = applicationContext.getSharedPreferences("fast_notifications", Context.MODE_PRIVATE)
        if (prefs.getString("notified", null) == key) return Result.success()
        if (clock.millis() < row.startAt + row.targetHours * Fasting.HOUR_MS) return Result.retry()
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(applicationContext,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return Result.success()
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "단식 알림", NotificationManager.IMPORTANCE_DEFAULT))
        val intent = Intent(applicationContext, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_MEALS, true)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending = PendingIntent.getActivity(applicationContext, 11, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        if (isStopped) return Result.success()
        val latest = db.fastDao().active() ?: return Result.success()
        if (latest.id != row.id || latest.startAt != row.startAt || latest.targetHours != row.targetHours)
            return Result.success()
        try {
            NotificationManagerCompat.from(applicationContext).notify(ID,
                NotificationCompat.Builder(applicationContext, CHANNEL).setSmallIcon(R.drawable.ic_reminder)
                    .setContentTitle("가볍게").setContentText("${row.targetHours}시간 단식을 채웠어요")
                    .setContentIntent(pending).setAutoCancel(true).build())
            prefs.edit().putString("notified", key).commit()
        } catch (_: SecurityException) { }
        return Result.success()
    }
    companion object { const val CHANNEL = "fast-goal"; const val ID = 101 }
}

@Singleton
class FastGoalScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: DietDatabase, private val clock: Clock,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var started = false
    @Synchronized fun start() {
        if (started) return
        started = true
        scope.launch {
            db.fastDao().observeAll().map { rows ->
                rows.filter { it.endAt == null }.maxWithOrNull(compareBy<FastEntity> { it.updatedAt }.thenBy { it.id })
                    ?.let { Triple(it.id, it.startAt, it.targetHours) }
            }.distinctUntilChanged().collect { active ->
                val work = WorkManager.getInstance(context)
                if (active == null) {
                    work.cancelUniqueWork(NAME)
                    NotificationManagerCompat.from(context).cancel(FastGoalWorker.ID)
                } else {
                    val (id, startAt, hours) = active
                    work.enqueueUniqueWork(NAME, ExistingWorkPolicy.REPLACE,
                        OneTimeWorkRequestBuilder<FastGoalWorker>()
                            .setInputData(workDataOf("id" to id, "startAt" to startAt, "targetHours" to hours))
                            .setInitialDelay((startAt + hours * Fasting.HOUR_MS - clock.millis()).coerceAtLeast(0), TimeUnit.MILLISECONDS)
                            .build())
                }
            }
        }
    }
    fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(NAME)
        NotificationManagerCompat.from(context).cancel(FastGoalWorker.ID)
        context.getSharedPreferences("fast_notifications", Context.MODE_PRIVATE).edit().clear().apply()
    }
    private companion object { const val NAME = "fast-goal" }
}
