package io.github.adgjmptw2.diet.data.steps

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

enum class StepsAvailability { HEALTH_CONNECT, SENSOR, NONE }

/** Health Connect를 먼저, 안 되면 폰 센서를 쓴다. 저장은 step_day 한 곳에만 한다. */
@Singleton
class StepsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: StepsStore,
    private val stepDays: StepDayRepository,
    private val health: HealthConnectSteps,
    private val sensor: StepCounterReader,
    private val scheduler: StepCounterScheduler,
    private val clock: Clock,
) : StepsSource {

    private val _source = MutableStateFlow(StepsAvailability.NONE)
    val source = _source.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()

    override fun daily(from: LocalDate, to: LocalDate): Flow<Map<LocalDate, Int>> =
        stepDays.observe(from, to)

    override val goal: Flow<Int> = store.goal

    /** 두 권한을 모두 거부한 적이 있는지. 설정 안내를 띄울 때만 쓴다. */
    val denied: Flow<Boolean> = store.denied

    fun canUseHealthConnect(): Boolean = health.available()

    fun canUseSensor(): Boolean = sensor.available

    /** Android 9 이하에는 ACTIVITY_RECOGNITION 권한이 없다. */
    fun hasSensorPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) ==
            PackageManager.PERMISSION_GRANTED


    suspend fun useHealthConnect(): Boolean = withContext(Dispatchers.IO) {
        val client = health.client() ?: return@withContext false
        if (!health.hasPermission(client)) return@withContext false
        store.setDenied(false)
        store.setMode(StepMode.HEALTH_CONNECT)
        val today = LocalDate.now(clock)
        pull(client, today.minusDays(HealthConnectSteps.LOOKBACK_DAYS), today)
        scheduler.cancel()
        _source.value = StepsAvailability.HEALTH_CONNECT
        true
    }

    suspend fun useSensor(): Boolean = withContext(Dispatchers.IO) {
        if (!sensor.available || !hasSensorPermission()) return@withContext false
        store.setDenied(false)
        store.setMode(StepMode.SENSOR)
        sensor.recordSample(store, stepDays, LocalDate.now(clock))
        scheduler.start()
        _source.value = StepsAvailability.SENSOR
        true
    }

    /** 앱이 열릴 때와 화면이 보일 때. 권한이 있으면 Health Connect, 없으면 센서만 읽는다. */
    suspend fun sample(): Unit = withContext(Dispatchers.IO) {
        if (!_busy.compareAndSet(false, true)) return@withContext
        try {
            val today = LocalDate.now(clock)
            val client = health.client()
            if (client != null && health.available() && health.hasPermission(client)) {
                store.setMode(StepMode.HEALTH_CONNECT)
                store.setDenied(false)
                pull(client, today.minusDays(HealthConnectSteps.LOOKBACK_DAYS), today)
                scheduler.cancel()
                _source.value = StepsAvailability.HEALTH_CONNECT
            } else if (sensor.available && hasSensorPermission()) {
                // 권한이 막 철회되면 센서 등록이 SecurityException을 던질 수 있다
                runCatching { sensor.recordSample(store, stepDays, today) }
                store.setMode(StepMode.SENSOR)
                store.setDenied(false)
                scheduler.start()
                _source.value = StepsAvailability.SENSOR
            } else {
                scheduler.cancel()
                if (_source.value != StepsAvailability.NONE) _source.value = StepsAvailability.NONE
            }
        } finally {
            _busy.value = false
        }
    }

    /** 앱을 처음 연 순간처럼 저장된 출처를 아직 모를 때만 이전 값을 읽는다. */
    suspend fun restoreSource() {
        if (_source.value != StepsAvailability.NONE) return
        val client = health.client()
        if (client != null && health.available() && health.hasPermission(client)) {
            _source.value = StepsAvailability.HEALTH_CONNECT
            return
        }
        if (sensor.available && hasSensorPermission()) _source.value = StepsAvailability.SENSOR
    }

    suspend fun setGoal(value: Int) = store.setGoal(value)

    suspend fun markDenied() = store.setDenied(true)

    suspend fun clearDenied() = store.setDenied(false)

    /** Health Connect가 없고 센서 권한까지 거부된 경우. 설정 안내에만 쓴다. */
    suspend fun markDeniedIfBlocked() {
        val client = health.client()
        if (client != null && health.hasPermission(client)) return
        if (sensor.available && hasSensorPermission()) return
        store.setDenied(true)
    }

    fun sourceLabel(): String = when (_source.value) {
        StepsAvailability.HEALTH_CONNECT -> "Health Connect에서 가져와요"
        StepsAvailability.SENSOR -> "폰 걸음 센서로 세요"
        StepsAvailability.NONE -> "걸음 수를 보려면 권한이 필요해요"
    }

    private suspend fun pull(client: androidx.health.connect.client.HealthConnectClient, from: LocalDate, to: LocalDate) {
        val values = health.read(client, from, to)
        values.forEach { (day, steps) -> stepDays.merge(day, steps, SOURCE_HEALTH) }
    }

    companion object {
        const val SOURCE_HEALTH = "health"
    }
}
