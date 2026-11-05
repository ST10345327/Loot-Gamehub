package com.gamehub.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.Fragment
import com.gamehub.app.ui.auth.LoginFragment
import com.gamehub.app.ui.auth.RegisterFragment
import com.gamehub.app.ui.onboarding.OnboardingFragment
import com.gamehub.app.ui.notifications.NotificationsFragment
import com.gamehub.app.ui.views.CompareFragment
import com.gamehub.app.ui.views.DiscoverFragment
import com.gamehub.app.ui.views.GameDetailsFragment
import com.gamehub.app.ui.views.LibraryFragment
import com.gamehub.app.ui.views.ProfileFragment
import com.gamehub.app.ui.views.SearchFragment
import com.gamehub.app.ui.views.SettingsFragment
import com.gamehub.app.ui.views.SplashFragment
import com.gamehub.app.ui.views.SurpriseFragment
import com.gamehub.app.ui.views.UnknownDestinationFragment
import com.gamehub.app.ui.views.WishlistFragment
import com.gamehub.app.ui.lootViewModelFactory
import com.gamehub.app.data.model.ThemeMode
import com.gamehub.app.databinding.ActivityMainBinding
import com.gamehub.app.ui.navigation.Screen
import com.gamehub.app.ui.navigation.shouldShowBottomNavigation
import com.gamehub.app.ui.session.SessionViewModel
import com.gamehub.app.ui.settings.SettingsViewModel
import com.gamehub.app.utils.AppLogger
import kotlinx.coroutines.launch

interface AppNavigator {
    fun navigateTo(route: String, clearBackStack: Boolean = false, addToBackStack: Boolean = true)
}

class MainActivity : AppCompatActivity(), AppNavigator {
    private lateinit var binding: ActivityMainBinding
    private lateinit var sessionViewModel: SessionViewModel
    private lateinit var settingsViewModel: SettingsViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        AppLogger.debug("MainActivity created")

        val container = (application as LootApplication).container
        val viewModelFactory = lootViewModelFactory(container)
        sessionViewModel = ViewModelProvider(this, viewModelFactory)[SessionViewModel::class.java]
        settingsViewModel = ViewModelProvider(this, viewModelFactory)[SettingsViewModel::class.java]

        binding.bottomNavigation.setOnItemSelectedListener { item ->
            val route = when (item.itemId) {
                R.id.navigation_search -> Screen.Search.route
                R.id.navigation_library -> Screen.Library.route
                R.id.navigation_wishlist -> Screen.Wishlist.route
                R.id.navigation_profile -> Screen.Profile.route
                else -> Screen.Home.route
            }
            navigateTo(route, clearBackStack = true, addToBackStack = false)
            true
        }

        if (savedInstanceState == null) {
            navigateTo(Screen.Splash.route, addToBackStack = false)
        } else {
            updateBottomNavigation()
        }

        handleIntentRoute(intent)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    settingsViewModel.uiState.collect { settings ->
                        AppCompatDelegate.setDefaultNightMode(
                            when (settings.themeMode) {
                                ThemeMode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                                ThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                                ThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
                            }
                        )
                    }
                }
                launch {
                    sessionViewModel.isLoggedIn.collect { isLoggedIn ->
                        val route = currentRoute()
                        if (isLoggedIn == false && route != null && route !in publicRoutes) {
                            AppLogger.debug("Session ended on '$route', returning to Login")
                            navigateTo(Screen.Login.route, clearBackStack = true, addToBackStack = false)
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntentRoute(intent)
    }

    private fun handleIntentRoute(intent: Intent?) {
        val route = intent?.getStringExtra("nav_route") ?: return
        if (route.isNotBlank()) {
            AppLogger.debug("Handling intent route: $route")
            navigateTo(route, addToBackStack = true)
            intent.removeExtra("nav_route")
        }
    }

    override fun navigateTo(route: String, clearBackStack: Boolean, addToBackStack: Boolean) {
        if (clearBackStack) {
            supportFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        }
        val current = supportFragmentManager.findFragmentById(R.id.fragment_container)
        if (current?.arguments?.getString(Screen.ARG_ROUTE) == route) {
            updateBottomNavigation(route)
            return
        }
        val transaction = supportFragmentManager.beginTransaction()
            .setReorderingAllowed(true)
            .replace(R.id.fragment_container, destinationFragment(route).apply {
                arguments = Bundle().apply { putString(Screen.ARG_ROUTE, route) }
            })
        if (addToBackStack) transaction.addToBackStack(route)
        transaction.commit()
        updateBottomNavigation(route)
    }

    fun sessionViewModel(): SessionViewModel = sessionViewModel

    private fun destinationFragment(route: String): Fragment = when (route) {
        Screen.Splash.route -> SplashFragment()
        Screen.Onboarding.route -> OnboardingFragment()
        Screen.Login.route -> LoginFragment()
        Screen.Register.route -> RegisterFragment()
        Screen.Home.route -> DiscoverFragment()
        Screen.Search.route -> SearchFragment()
        Screen.Library.route -> LibraryFragment()
        Screen.Wishlist.route -> WishlistFragment()
        Screen.Profile.route -> ProfileFragment()
        Screen.Settings.route -> SettingsFragment()
        Screen.Notifications.route -> NotificationsFragment()
        Screen.SurpriseMe.route -> SurpriseFragment()
        else -> when {
            route.startsWith("game/") -> GameDetailsFragment()
            route.startsWith("compare/") -> CompareFragment()
            else -> UnknownDestinationFragment()
        }
    }

    private fun currentRoute(): String? =
        supportFragmentManager.findFragmentById(R.id.fragment_container)
            ?.arguments?.getString(Screen.ARG_ROUTE)

    private fun updateBottomNavigation(route: String? = currentRoute()) {
        val itemId = when (route) {
            Screen.Home.route -> R.id.navigation_discover
            Screen.Search.route -> R.id.navigation_search
            Screen.Library.route -> R.id.navigation_library
            Screen.Wishlist.route -> R.id.navigation_wishlist
            Screen.Profile.route -> R.id.navigation_profile
            else -> null
        }
        if (itemId != null) binding.bottomNavigation.menu.findItem(itemId).isChecked = true
        binding.bottomNavigation.visibility =
            if (shouldShowBottomNavigation(route)) View.VISIBLE else View.GONE
    }

    private companion object {
        val publicRoutes = setOf(
            Screen.Splash.route, Screen.Onboarding.route, Screen.Login.route, Screen.Register.route
        )
    }
}
