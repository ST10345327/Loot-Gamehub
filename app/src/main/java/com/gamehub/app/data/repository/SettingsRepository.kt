package com.gamehub.app.data.repository

import com.gamehub.app.data.local.SettingsStore
import com.gamehub.app.data.model.AppLanguage
import com.gamehub.app.data.model.AppSettings
import com.gamehub.app.data.model.ThemeMode
import com.gamehub.app.data.remote.ApiCaller
import com.gamehub.app.data.remote.LootApi
import com.gamehub.app.data.remote.dto.SettingsRequest
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setLanguage(language: AppLanguage)
    suspend fun setNotificationsEnabled(enabled: Boolean)
    suspend fun setWishlistAlerts(enabled: Boolean)
    suspend fun setReleaseAlerts(enabled: Boolean)
}

class SettingsRepositoryImpl(
    private val store: SettingsStore,
    private val api: LootApi,
    private val apiCaller: ApiCaller
) : SettingsRepository {
    override val settings: Flow<AppSettings> = store.settings

    override suspend fun setThemeMode(mode: ThemeMode) {
        store.setThemeMode(mode)
        try {
            apiCaller.call { api.updateSettings(SettingsRequest(themePreference = mode.apiValue)) }
        } catch (_: Exception) {}
    }

    override suspend fun setLanguage(language: AppLanguage) {
        store.setLanguage(language)
        try {
            apiCaller.call { api.updateSettings(SettingsRequest(preferredLanguage = language.tag)) }
        } catch (_: Exception) {}
    }

    override suspend fun setNotificationsEnabled(enabled: Boolean) {
        store.setNotificationsEnabled(enabled)
        try {
            apiCaller.call { api.updateSettings(SettingsRequest(notificationsEnabled = enabled)) }
        } catch (_: Exception) {}
    }

    override suspend fun setWishlistAlerts(enabled: Boolean) {
        store.setWishlistAlerts(enabled)
        try {
            apiCaller.call { api.updateSettings(SettingsRequest(wishlistAlerts = enabled)) }
        } catch (_: Exception) {}
    }

    override suspend fun setReleaseAlerts(enabled: Boolean) {
        store.setReleaseAlerts(enabled)
        try {
            apiCaller.call { api.updateSettings(SettingsRequest(releaseAlerts = enabled)) }
        } catch (_: Exception) {}
    }
}
