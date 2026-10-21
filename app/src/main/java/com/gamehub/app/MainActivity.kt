package com.gamehub.app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gamehub.app.ui.lootViewModelFactory
import com.gamehub.app.ui.navigation.LootNavHost
import com.gamehub.app.ui.settings.SettingsViewModel
import com.gamehub.app.ui.theme.LootTheme
import com.gamehub.app.utils.AppLogger

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AppLogger.debug("MainActivity created")

        val container = (application as LootApplication).container
        val viewModelFactory = lootViewModelFactory(container)

        setContent {
            // Shared instance so the theme reacts the instant Settings changes it, from anywhere in the app.
            val settingsViewModel: SettingsViewModel = viewModel(factory = viewModelFactory)
            val settings by settingsViewModel.uiState.collectAsStateWithLifecycle()

            LootTheme(themeMode = settings.themeMode) {
                LootNavHost(container = container)
            }
        }
    }
}
