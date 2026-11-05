package com.gamehub.app.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.gamehub.app.ui.settings.SettingsViewModel
import com.gamehub.app.di.AppContainer
import com.gamehub.app.ui.auth.LoginViewModel
import com.gamehub.app.ui.auth.RegisterViewModel
import com.gamehub.app.ui.home.HomeViewModel
import com.gamehub.app.ui.library.LibraryViewModel
import com.gamehub.app.ui.search.SearchViewModel
import com.gamehub.app.ui.session.SessionViewModel
import com.gamehub.app.ui.splash.SplashViewModel
import com.gamehub.app.ui.wishlist.WishlistViewModel
import com.gamehub.app.ui.surprise.SurpriseMeViewModel

/**
 * Tells Android how to build each ViewModel that doesn't need a navigation argument.
 * GameDetailsViewModel is built separately (see its own factory) because it needs the game id.
 */
fun lootViewModelFactory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
    initializer { SessionViewModel(container.authRepository, container.notificationRepository) }
    initializer { SplashViewModel(container.authRepository) }
    initializer { LoginViewModel(container.authRepository) }
    initializer { RegisterViewModel(container.authRepository) }
    initializer { HomeViewModel(container.gameRepository) }
    initializer { SearchViewModel(container.gameRepository) }
    initializer { LibraryViewModel(container.libraryRepository) }
    initializer { WishlistViewModel(container.libraryRepository) }
    initializer { SurpriseMeViewModel(container.gameRepository) }
    initializer { SettingsViewModel(container.settingsRepository) }
}
