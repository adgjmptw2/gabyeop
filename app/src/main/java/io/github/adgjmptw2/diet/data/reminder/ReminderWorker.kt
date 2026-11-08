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
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.adgjmptw2.diet.MainActivity
import io.github.adgjmptw2.diet.R
import io.github.adgjmptw2.diet.data.DietDatabase
import io.github.adgjmptw2.diet.data.auth.ReminderSettings
import io.github.adgjmptw2.diet.data.auth.TokenStore
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted parameters: WorkerParameters,
    private val db: DietDatabase,
    private val tokens: TokenStore,
    private val clock: Clock,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        if (!tokens.reminder.first().enabled) return Result.success()
        if (db.weightDao().findByDay(LocalDate.now(clock).toEpochDay())?.deleted == false) return Result.success()
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(applicationContext,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return Result.success()
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "기록 알림", NotificationManager.IMPORTANCE_DEFAULT))
        val intent = Intent(applicationContext, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_RECORD_TODAY, true)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pendingIntent = PendingIntent.getActivity(applicationContext, 10, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(R.drawable.ic_reminder)
            .setContentTitle("가볍게")
            .setContentText("오늘 체중을 기록할 시간이에요")
            .setContentIntent(pendingIntent).setAutoCancel(true).build()
        try { NotificationManagerCompat.from(applicationContext).notify(REMINDER_ID, notification) }
        catch (_: SecurityException) { /* Permission may be revoked between the check and notify. */ }
        return Result.success()
    }

    companion object {
        const val CHANNEL = "record-reminder"
        const val REMINDER_ID = 100
    }
}

@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tokens: TokenStore,
    private val clock: Clock,
) {
    private val work get() = WorkManager.getInstance(context)
    suspend fun restore() { schedule(tokens.reminder.first(), replace = false) }

    suspend fun update(settings: ReminderSettings) {
        tokens.setReminder(settings)
        schedule(settings, replace = true)
    }

    private fun schedule(settings: ReminderSettings, replace: Boolean) {
        if (!settings.enabled) { cancel(); return }
        val now = ZonedDateTime.now(clock)
        var next = now.withHour(settings.hour).withMinute(settings.minute).withSecond(0).withNano(0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        work.enqueueUniquePeriodicWork(NAME,
            if (replace) ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE else ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<ReminderWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(Duration.between(now, next).toMillis(), TimeUnit.MILLISECONDS).build())
    }

    fun cancel() {
        work.cancelUniqueWork(NAME)
        NotificationManagerCompat.from(context).cancel(ReminderWorker.REMINDER_ID)
    }

    private companion object { const val NAME = "daily-record-reminder" }
}
