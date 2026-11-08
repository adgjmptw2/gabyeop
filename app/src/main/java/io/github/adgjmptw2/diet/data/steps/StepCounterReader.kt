package io.github.adgjmptw2.diet.data.steps

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StepCounterReader @Inject constructor(@ApplicationContext private val context: Context) {
    private val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val sensor: Sensor? = manager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

    val available: Boolean get() = sensor != null

    /** 부팅 후 누적 걸음. 센서 이벤트를 기다리므로 짧게 제한한다. */
    suspend fun currentTotal(timeoutMillis: Long = 5_000L): Long? {
        val target = sensor ?: return null
        val event = withTimeoutOrNull(timeoutMillis) { events(target).firstOrNull() }
        return event?.toLong()
    }

    private fun events(target: Sensor): Flow<Float> = callbackFlow {
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type == Sensor.TYPE_STEP_COUNTER && event.values.isNotEmpty()) {
                    trySend(event.values[0])
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        val registered = manager?.registerListener(listener, target, SensorManager.SENSOR_DELAY_NORMAL) == true
        if (!registered) close()
        awaitClose { manager?.unregisterListener(listener) }
    }.flowOn(Dispatchers.Default)

    /**
     * 부팅 후 누적값의 차이를 날짜별 걸음에 더한다.
     * 날짜가 바뀐 뒤의 차이는 오늘에, 재부팅으로 누적값이 줄면 지금 값부터 다시 센다.
     */
    suspend fun recordSample(store: StepsStore, repository: StepDayRepository, today: LocalDate) {
        val total = currentTotal() ?: return
        val previous = store.sensorBaseline()
        if (previous == null) {
            store.setSensorBaseline(SensorBaseline(total, today.toEpochDay()))
            return
        }
        if (total < previous.total) {
            // 재부팅. 끊긴 구간은 복구할 수 없으니 지금 값부터 다시 센다.
            store.setSensorBaseline(SensorBaseline(total, today.toEpochDay()))
            return
        }
        val baselineDay = LocalDate.ofEpochDay(previous.epochDay).takeIf { !it.isAfter(today) } ?: today
        val delta = (total - previous.total).coerceAtMost(MAX_DELTA).toInt()
        if (delta <= 0) {
            if (baselineDay != today) store.setSensorBaseline(SensorBaseline(total, today.toEpochDay()))
            return
        }
        // 날짜가 바뀐 뒤 처음 읽은 차이는 오늘로 센다(밤새 작업이 미뤄지면 아침 걸음이 어제로 가는 것을 막음)
        val target = today
        val existing = repository.range(target, target).firstOrNull()
        // Health Connect로 읽은 날에는 센서 차이를 더하지 않는다. 출처가 섞이면 수치가 부풀어 오른다.
        if (existing != null && existing.source != SOURCE) {
            store.setSensorBaseline(SensorBaseline(total, today.toEpochDay()))
            return
        }
        repository.save(target, (existing?.steps ?: 0) + delta, SOURCE)
        store.setSensorBaseline(SensorBaseline(total, today.toEpochDay()))
    }

    companion object {
        const val SOURCE = "sensor"
        private const val MAX_DELTA = 200_000L
    }
}
