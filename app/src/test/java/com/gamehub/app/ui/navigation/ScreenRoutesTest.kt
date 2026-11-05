package com.gamehub.app.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Guards the navigation table so a typo or duplicate route is caught by CI, not on a phone. */
class ScreenRoutesTest {

    @Test
    fun allRoutes_areUnique() {
        val routes = allScreens.map { it.route }
        assertEquals(routes.size, routes.toSet().size)
    }

    @Test
    fun allRoutes_areNotBlank() {
        assertTrue(allScreens.all { it.route.isNotBlank() })
    }

    @Test
    fun settings_keepsBottomNavigationAvailable() {
        assertTrue(Screen.Settings.route in bottomBarRoutes)
    }

    @Test
    fun authenticatedDestinations_keepBottomNavigationAvailable() {
        assertTrue(shouldShowBottomNavigation(Screen.Settings.route))
        assertTrue(shouldShowBottomNavigation(Screen.GameDetails.createRoute(12)))
        assertTrue(shouldShowBottomNavigation(Screen.Compare.createRoute(12)))
        assertTrue(shouldShowBottomNavigation(Screen.SurpriseMe.route))
    }

    @Test
    fun publicDestinations_hideBottomNavigation() {
        assertFalse(shouldShowBottomNavigation(Screen.Splash.route))
        assertFalse(shouldShowBottomNavigation(Screen.Onboarding.route))
        assertFalse(shouldShowBottomNavigation(Screen.Login.route))
        assertFalse(shouldShowBottomNavigation(Screen.Register.route))
        assertFalse(shouldShowBottomNavigation(null))
    }

    @Test
    fun bottomBar_routesAllExist() {
        val known = allScreens.map { it.route }.toSet()
        assertTrue(known.containsAll(bottomBarRoutes))
    }

    @Test
    fun authScreens_neverShowBottomBar() {
        val authScreens = listOf(Screen.Splash, Screen.Onboarding, Screen.Login, Screen.Register)
        authScreens.forEach { screen ->
            assertFalse("${screen.route} must hide the bottom bar", screen.route in bottomBarRoutes)
        }
    }

    @Test
    fun publicRoutes_areNeverBottomBarTabs() {
        assertTrue(publicRoutes.none { it in bottomBarRoutes })
        assertTrue(Screen.Home.route !in publicRoutes)
    }
}