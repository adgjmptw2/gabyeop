package io.github.adgjmptw2.diet.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.adgjmptw2.diet.domain.WaterSettings
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.trackingDataStore by preferencesDataStore(name = "tracking")

@Singleton
class TrackingStore @Inject constructor(@ApplicationContext context: Context) {
    private val store = context.trackingDataStore
    private val glass = intPreferencesKey("water_glass_ml")
    private val target = intPreferencesKey("water_target_ml")
    private val fastHours = intPreferencesKey("fast_target_hours")
    private val data = store.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }
    val water = data.map { WaterSettings(it[glass] ?: 250, it[target]) }.distinctUntilChanged()
    val fastingHours = data.map { it[fastHours] ?: 16 }.distinctUntilChanged()
    suspend fun setWater(settings: WaterSettings) {
        require(settings.glassMl in listOf(150, 200, 250, 300, 500))
        require(settings.targetMl == null || settings.targetMl in 250..10_000)
        store.edit {
            it[glass] = settings.glassMl
            if (settings.targetMl == null) it.remove(target) else it[target] = settings.targetMl
        }
    }
    suspend fun setFastingHours(hours: Int) {
        require(hours in listOf(14, 16, 18, 20))
        store.edit { it[fastHours] = hours }
    }
    suspend fun clear() { store.edit { it.clear() } }
}
