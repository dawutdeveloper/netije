package com.example.netije.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.netije.domain.model.AppLanguage
import com.example.netije.domain.model.AppSettings
import com.example.netije.domain.model.BackupUnit
import com.example.netije.domain.model.DarkModeConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "netije_settings")
private const val PREFS_NAME = "netije_settings_sync"
private const val PREF_KEY_LANG = "language_code_sync"
private const val PREF_KEY_CURRENCY = "currency_code_sync"

class SettingsDataStore @Inject constructor(private val context: Context) {

    private object Keys {
        val CURRENCY = stringPreferencesKey("currency_code")
        val LANGUAGE = stringPreferencesKey("language_code")
        val DARK_MODE = stringPreferencesKey("dark_mode")
        val LAST_BACKUP = longPreferencesKey("last_backup_at")
        val BACKUP_NUMBER = intPreferencesKey("backup_number")
        val BACKUP_UNIT = stringPreferencesKey("backup_unit")
        val BACKUP_NEXT_RUN = longPreferencesKey("backup_next_run_at")
    }

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        AppSettings(
            currencyCode = prefs[Keys.CURRENCY] ?: "TMT",
            language = AppLanguage.fromCode(prefs[Keys.LANGUAGE]),
            darkMode = DarkModeConfig.fromString(prefs[Keys.DARK_MODE]),
            lastBackupAt = prefs[Keys.LAST_BACKUP],
            backupNumber = prefs[Keys.BACKUP_NUMBER],
            backupUnit = BackupUnit.fromName(prefs[Keys.BACKUP_UNIT]),
            backupNextRunAt = prefs[Keys.BACKUP_NEXT_RUN]
        )
    }

    fun readLanguageSync(): AppLanguage {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return AppLanguage.fromCode(prefs.getString(PREF_KEY_LANG, null))
    }

    suspend fun setCurrency(code: String) {
        context.settingsDataStore.edit { it[Keys.CURRENCY] = code }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(PREF_KEY_CURRENCY, code).apply()
    }

    suspend fun setLanguage(language: AppLanguage) {
        context.settingsDataStore.edit { it[Keys.LANGUAGE] = language.code }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(PREF_KEY_LANG, language.code).apply()
    }

    suspend fun setDarkMode(mode: DarkModeConfig) {
        context.settingsDataStore.edit { it[Keys.DARK_MODE] = mode.name }
    }

    suspend fun setBackupFrequency(number: Int, unit: BackupUnit, nextRunAt: Long?) {
        context.settingsDataStore.edit { p ->
            p[Keys.BACKUP_NUMBER] = number
            p[Keys.BACKUP_UNIT] = unit.name
            if (nextRunAt != null) p[Keys.BACKUP_NEXT_RUN] = nextRunAt else p.remove(Keys.BACKUP_NEXT_RUN)
        }
    }

    suspend fun markBackupTaken() {
        context.settingsDataStore.edit { it[Keys.LAST_BACKUP] = System.currentTimeMillis() }
    }
}
