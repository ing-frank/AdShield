package com.adshield.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.adshield.domain.model.AppSettings
import com.adshield.domain.model.ProtectionMode
import com.adshield.domain.model.ThemeMode
import com.adshield.domain.model.UpdateFrequency
import com.adshield.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepositoryImpl(context: Context) : SettingsRepository {

    private val store = context.applicationContext.settingsStore

    override val settings: Flow<AppSettings> = store.data
        .catch { error ->
            // Archivo de preferencias ilegible: se usan los valores por defecto.
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { prefs ->
            AppSettings(
                mode = ProtectionMode.fromName(prefs[MODE]),
                startOnBoot = prefs[START_ON_BOOT] ?: false,
                updateFrequency = UpdateFrequency.fromName(prefs[UPDATE_FREQUENCY]),
                notificationCounter = prefs[NOTIFICATION_COUNTER] ?: true,
                theme = ThemeMode.fromName(prefs[THEME]),
                saveStats = prefs[SAVE_STATS] ?: true
            )
        }

    override suspend fun setMode(mode: ProtectionMode) {
        store.edit { it[MODE] = mode.name }
    }

    override suspend fun setStartOnBoot(enabled: Boolean) {
        store.edit { it[START_ON_BOOT] = enabled }
    }

    override suspend fun setUpdateFrequency(frequency: UpdateFrequency) {
        store.edit { it[UPDATE_FREQUENCY] = frequency.name }
    }

    override suspend fun setNotificationCounter(enabled: Boolean) {
        store.edit { it[NOTIFICATION_COUNTER] = enabled }
    }

    override suspend fun setTheme(theme: ThemeMode) {
        store.edit { it[THEME] = theme.name }
    }

    override suspend fun setSaveStats(enabled: Boolean) {
        store.edit { it[SAVE_STATS] = enabled }
    }

    override suspend fun reset() {
        store.edit { it.clear() }
    }

    private companion object {
        val MODE = stringPreferencesKey("mode")
        val START_ON_BOOT = booleanPreferencesKey("start_on_boot")
        val UPDATE_FREQUENCY = stringPreferencesKey("update_frequency")
        val NOTIFICATION_COUNTER = booleanPreferencesKey("notification_counter")
        val THEME = stringPreferencesKey("theme")
        val SAVE_STATS = booleanPreferencesKey("save_stats")
    }
}
