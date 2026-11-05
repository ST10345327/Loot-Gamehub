package com.gamehub.app.ui.settings

import com.gamehub.app.utils.LocaleManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gamehub.app.data.model.AppLanguage
import com.gamehub.app.data.model.AppSettings
import com.gamehub.app.data.model.ThemeMode
import com.gamehub.app.data.repository.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Backs the Settings screen. [uiState] also drives the live theme in [com.gamehub.app.MainActivity]. */
class SettingsViewModel(private val repository: SettingsRepository) : ViewModel() {

    val uiState: StateFlow<AppSettings> = repository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { repository.setThemeMode(mode) }
    }

    fun setLanguage(language: AppLanguage) {
        viewModelScope.launch { repository.setLanguage(language) }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setNotificationsEnabled(enabled) }
    }

    fun setWishlistAlerts(enabled: Boolean) {
        viewModelScope.launch { repository.setWishlistAlerts(enabled) }
    }

    fun setReleaseAlerts(enabled: Boolean) {
        viewModelScope.launch { repository.setReleaseAlerts(enabled) }
    }

    init {
        viewModelScope.launch {
            repository.settings.collect { settings ->
                LocaleManager.applyLanguage(settings.language)
            }
        }
    }
}
