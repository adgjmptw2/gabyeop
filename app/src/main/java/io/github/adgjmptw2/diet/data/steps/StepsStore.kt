package io.github.adgjmptw2.diet.data.steps

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.stepsDataStore by preferencesDataStore(name = "steps")

@Singleton
class StepsStore @Inject constructor(@ApplicationContext context: Context) {
    private val store = context.applicationContext.stepsDataStore
    private val goalKey = intPreferencesKey("step_goal")
    private val modeKey = stringPreferencesKey("step_mode")
    private val sensorBaseKey = stringPreferencesKey("sensor_base")
    private val deniedKey = booleanPreferencesKey("permission_denied")

    private val data = store.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }

    val goal: Flow<Int> = data.map { (it[goalKey] ?: DEFAULT_GOAL).coerceIn(MIN_GOAL, MAX_GOAL) }
        .distinctUntilChanged()

    val mode: Flow<StepMode> = data.map { StepMode.from(it[modeKey]) }.distinctUntilChanged()

    /** 사용자가 한 번이라도 권한을 거부했는지. 안내 문구를 정할 때만 쓴다. */
    val denied: Flow<Boolean> = data.map { it[deniedKey] ?: false }.distinctUntilChanged()

    suspend fun setGoal(value: Int) {
        require(value in MIN_GOAL..MAX_GOAL)
        store.edit { it[goalKey] = value }
    }

    suspend fun setDenied(value: Boolean) {
        store.edit { it[deniedKey] = value }
    }

    suspend fun setMode(mode: StepMode) {
        store.edit { it[modeKey] = mode.name }
    }

    suspend fun sensorBaseline(): SensorBaseline? =
        data.first()[sensorBaseKey]?.let(SensorBaseline::decode)

    suspend fun setSensorBaseline(value: SensorBaseline?) {
        store.edit { if (value == null) it.remove(sensorBaseKey) else it[sensorBaseKey] = value.encode() }
    }

    suspend fun clear() = store.edit { it.clear() }

    companion object {
        const val DEFAULT_GOAL = 8000
        const val MIN_GOAL = 1000
        const val MAX_GOAL = 50000
    }
}

data class SensorBaseline(val total: Long, val epochDay: Long) {
    fun encode(): String = "$total:$epochDay"

    companion object {
        fun decode(value: String): SensorBaseline? {
            val parts = value.split(':', limit = 2)
            val total = parts.getOrNull(0)?.toLongOrNull() ?: return null
            val day = parts.getOrNull(1)?.toLongOrNull() ?: return null
            return SensorBaseline(total, day)
        }
    }
}

enum class StepMode {
    HEALTH_CONNECT,
    SENSOR,
    NONE;

    companion object {
        fun from(value: String?): StepMode = entries.firstOrNull { it.name == value } ?: NONE
    }
}
