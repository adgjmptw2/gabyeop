package io.github.adgjmptw2.diet.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.adgjmptw2.diet.domain.WaterSettings
import io.github.adgjmptw2.diet.domain.waterSettingsFromStored
import io.github.adgjmptw2.diet.domain.MacroGoal
import io.github.adgjmptw2.diet.domain.Fasting
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

private val Context.trackingDataStore by preferencesDataStore(name = "tracking")

@Singleton
class TrackingStore @Inject constructor(@ApplicationContext context: Context) {
    private val store = context.trackingDataStore
    private val glass = intPreferencesKey("water_glass_ml")
    private val target = intPreferencesKey("water_target_ml")
    private val fastHours = intPreferencesKey("fast_target_hours")
    private val carb = intPreferencesKey("macro_carb_g")
    private val protein = intPreferencesKey("macro_protein_g")
    private val fat = intPreferencesKey("macro_fat_g")
    private val waterDays = stringSetPreferencesKey("water_recorded_epoch_days")
    private val data = store.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }
    val water = data.map { waterSettingsFromStored(it[glass], it[target]) }.distinctUntilChanged()
    val macroGoals = data.map { MacroGoal(it[carb], it[protein], it[fat]) }.distinctUntilChanged()
    // 기존 동기화 프로토콜은 빈 0 ml 줄을 삭제로 돌려준다. 명시한 0의 날짜를 기기에 남긴다.
    val waterRecordedDays = data.map { prefs -> prefs[waterDays].orEmpty().mapNotNull { raw ->
        raw.toLongOrNull()?.let { runCatching { LocalDate.ofEpochDay(it) }.getOrNull() }
    }.toSet() }.distinctUntilChanged()
    suspend fun markWaterRecorded(date: LocalDate) {
        store.edit { it[waterDays] = it[waterDays].orEmpty() + date.toEpochDay().toString() }
    }
    val fastingHours = data.map { it[fastHours] ?: 16 }.distinctUntilChanged()
    suspend fun setWater(settings: WaterSettings) {
        require(settings.glassMl in 50..2000)
        require(settings.targetMl == null || settings.targetMl in 250..10_000)
        store.edit {
            it[glass] = settings.glassMl
            if (settings.targetMl == null) it.remove(target) else it[target] = settings.targetMl
        }
    }
    suspend fun setGlassMl(ml: Int) {
        require(ml in 50..2000)
        store.edit { it[glass] = ml }
    }
    suspend fun setFastingHours(hours: Int) {
        require(hours in Fasting.TARGET_HOURS)
        store.edit { it[fastHours] = hours }
    }
    suspend fun setMacroGoals(goal: MacroGoal) {
        require(listOf(goal.carbGrams, goal.proteinGrams, goal.fatGrams).all { it == null || it in 0..1000 })
        store.edit { prefs ->
            listOf(carb to goal.carbGrams, protein to goal.proteinGrams, fat to goal.fatGrams).forEach { (key, value) ->
                if (value == null) prefs.remove(key) else prefs[key] = value
            }
        }
    }
    suspend fun clear() { store.edit { it.clear() } }
}
