package io.github.adgjmptw2.diet.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.adgjmptw2.diet.ui.theme.AppTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.appSettings by preferencesDataStore(name = "app_settings")

@Singleton
class ThemeStore @Inject constructor(@ApplicationContext context: Context) {
    private val store = context.applicationContext.appSettings
    private val themeKey = stringPreferencesKey("theme")

    val theme: Flow<AppTheme> = store.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { preferences ->
            AppTheme.entries.firstOrNull { it.name == preferences[themeKey] } ?: AppTheme.LIGHT
        }
        .distinctUntilChanged()

    suspend fun set(theme: AppTheme) {
        store.edit { it[themeKey] = theme.name }
    }
}
