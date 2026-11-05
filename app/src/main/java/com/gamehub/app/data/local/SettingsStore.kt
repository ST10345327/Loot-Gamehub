package com.gamehub.app.data.local

import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.gamehub.app.data.model.AppLanguage
import com.gamehub.app.data.model.AppSettings
import com.gamehub.app.data.model.ThemeMode
import com.gamehub.app.utils.AppLogger
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/**
 * Persists appearance, language, and notification choices in the same DataStore file as [SessionStore].
 */
class SettingsStore(private val dataStore: DataStore<Preferences>) {

    private object Keys {
        val THEME = stringPreferencesKey("theme_mode")
        val LANGUAGE = stringPreferencesKey("app_language")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val WISHLIST_ALERTS = booleanPreferencesKey("wishlist_alerts")
        val RELEASE_ALERTS = booleanPreferencesKey("release_alerts")
    }

    val settings: Flow<AppSettings> = dataStore.data
        .catch { error ->
            if (error is IOException) {
                AppLogger.error("Could not read saved settings", error)
                emit(emptyPreferences())
            } else {
                throw error
            }
        }
        .map { prefs ->
            AppSettings(
                themeMode = ThemeMode.fromApi(prefs[Keys.THEME]),
                language = AppLanguage.fromTag(prefs[Keys.LANGUAGE]),
                notificationsEnabled = prefs[Keys.NOTIFICATIONS_ENABLED] ?: true,
                wishlistAlerts = prefs[Keys.WISHLIST_ALERTS] ?: true,
                releaseAlerts = prefs[Keys.RELEASE_ALERTS] ?: true
            )
        }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[Keys.THEME] = mode.apiValue }
    }

    suspend fun setLanguage(language: AppLanguage) {
        dataStore.edit { it[Keys.LANGUAGE] = language.tag }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.NOTIFICATIONS_ENABLED] = enabled }
    }

    suspend fun setWishlistAlerts(enabled: Boolean) {
        dataStore.edit { it[Keys.WISHLIST_ALERTS] = enabled }
    }

    suspend fun setReleaseAlerts(enabled: Boolean) {
        dataStore.edit { it[Keys.RELEASE_ALERTS] = enabled }
    }
}
