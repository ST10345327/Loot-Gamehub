package com.gamehub.app.testing

import com.gamehub.app.data.model.AppLanguage
import com.gamehub.app.data.model.AppSettings
import com.gamehub.app.data.model.ThemeMode
import com.gamehub.app.data.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeSettingsRepository : SettingsRepository {
    private val _settings = MutableStateFlow(AppSettings())
    override val settings: Flow<AppSettings> = _settings

    override suspend fun setThemeMode(mode: ThemeMode) {
        _settings.value = _settings.value.copy(themeMode = mode)
    }

    override suspend fun setLanguage(language: AppLanguage) {
        _settings.value = _settings.value.copy(language = language)
    }

    override suspend fun setNotificationsEnabled(enabled: Boolean) {
        _settings.value = _settings.value.copy(notificationsEnabled = enabled)
    }

    override suspend fun setWishlistAlerts(enabled: Boolean) {
        _settings.value = _settings.value.copy(wishlistAlerts = enabled)
    }

    override suspend fun setReleaseAlerts(enabled: Boolean) {
        _settings.value = _settings.value.copy(releaseAlerts = enabled)
    }
}
