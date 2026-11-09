package io.github.adgjmptw2.diet.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.supplementSettingsDataStore by preferencesDataStore(name = "supplements")

/** 영양제 표시 방식. 기기 안에만 저장하고 서버로 보내지 않는다. */
@Singleton
class SupplementSettings @Inject constructor(@ApplicationContext context: Context) {
    private val store = context.applicationContext.supplementSettingsDataStore
    private val splitByTimeKey = booleanPreferencesKey("split_by_time")

    val splitByTime: Flow<Boolean> = store.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { preferences -> preferences[splitByTimeKey] ?: true }
        .distinctUntilChanged()

    suspend fun setSplitByTime(split: Boolean) {
        store.edit { it[splitByTimeKey] = split }
    }

    suspend fun clear() {
        store.edit { it.clear() }
    }
}
