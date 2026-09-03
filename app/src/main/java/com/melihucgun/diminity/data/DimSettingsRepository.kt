package com.melihucgun.diminity.data

import android.annotation.SuppressLint
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "diminity_settings")

enum class AppThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

data class DimSettings(
    val dimLevel: Float,
    val blueFilterLevel: Float,
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
)

class DimSettingsRepository private constructor(private val context: Context) {

    companion object {
        private val KEY_DIM_LEVEL = floatPreferencesKey("dim_level")
        private val KEY_BLUE_FILTER_LEVEL = floatPreferencesKey("blue_filter_level")
        private val KEY_THEME_MODE = intPreferencesKey("theme_mode")

        private const val DEFAULT_DIM_LEVEL = 0.35f
        private const val DEFAULT_BLUE_FILTER_LEVEL = 0.0f

        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var INSTANCE: DimSettingsRepository? = null

        fun getInstance(context: Context): DimSettingsRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DimSettingsRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    val settingsFlow: Flow<DimSettings> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val dim = (preferences[KEY_DIM_LEVEL] ?: DEFAULT_DIM_LEVEL).coerceIn(0.0f, 0.80f)
            val blue = (preferences[KEY_BLUE_FILTER_LEVEL] ?: DEFAULT_BLUE_FILTER_LEVEL).coerceIn(0.0f, 1.0f)
            val modeOrdinal = preferences[KEY_THEME_MODE] ?: AppThemeMode.SYSTEM.ordinal
            val mode = AppThemeMode.entries.getOrNull(modeOrdinal) ?: AppThemeMode.SYSTEM
            DimSettings(dimLevel = dim, blueFilterLevel = blue, themeMode = mode)
        }

    suspend fun setDimLevel(dimLevel: Float) {
        val clampedDim = dimLevel.coerceIn(0.0f, 0.80f)
        context.dataStore.edit { preferences ->
            preferences[KEY_DIM_LEVEL] = clampedDim
        }
    }

    suspend fun setBlueFilterLevel(blueFilterLevel: Float) {
        val clampedBlue = blueFilterLevel.coerceIn(0.0f, 1.0f)
        context.dataStore.edit { preferences ->
            preferences[KEY_BLUE_FILTER_LEVEL] = clampedBlue
        }
    }

    suspend fun setSettings(dimLevel: Float, blueFilterLevel: Float) {
        val clampedDim = dimLevel.coerceIn(0.0f, 0.80f)
        val clampedBlue = blueFilterLevel.coerceIn(0.0f, 1.0f)
        context.dataStore.edit { preferences ->
            preferences[KEY_DIM_LEVEL] = clampedDim
            preferences[KEY_BLUE_FILTER_LEVEL] = clampedBlue
        }
    }

    suspend fun setThemeMode(mode: AppThemeMode) {
        context.dataStore.edit { preferences ->
            preferences[KEY_THEME_MODE] = mode.ordinal
        }
    }

    suspend fun getSettingsOnce(): DimSettings {
        return settingsFlow.first()
    }
}
