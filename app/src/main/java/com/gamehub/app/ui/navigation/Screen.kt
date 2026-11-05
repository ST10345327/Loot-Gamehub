package com.gamehub.app.ui.navigation

sealed class Screen(val route: String) {
    data object Splash : Screen("splash")
    data object Onboarding : Screen("onboarding")
    data object Login : Screen("login")
    data object Register : Screen("register")
    data object Home : Screen("home")
    data object Search : Screen("search")
    data object Library : Screen("library")
    data object Wishlist : Screen("wishlist")
    data object Profile : Screen("profile")
    data object Settings : Screen("settings")
    data object Notifications : Screen("notifications")
    data object SurpriseMe : Screen("surprise")
    data object GameDetails : Screen("game/{gameId}") {
        fun createRoute(gameId: Int) = "game/$gameId"
    }
    data object Compare : Screen("compare/{firstGameId}") {
        fun createRoute(firstGameId: Int) = "compare/$firstGameId"
    }

    companion object {
        const val ARG_ROUTE = "route"
    }
}

val allScreens: List<Screen> = listOf(
    Screen.Splash, Screen.Onboarding, Screen.Login, Screen.Register,
    Screen.Home, Screen.Search, Screen.Library, Screen.Wishlist,
    Screen.Profile, Screen.Settings, Screen.Notifications, Screen.SurpriseMe, Screen.GameDetails, Screen.Compare
)

val bottomBarRoutes: Set<String> = setOf(
    Screen.Home.route, Screen.Search.route, Screen.Library.route, Screen.Wishlist.route,
    Screen.Profile.route, Screen.Settings.route, Screen.Notifications.route
)

val publicRoutes: Set<String> = setOf(
    Screen.Splash.route, Screen.Onboarding.route, Screen.Login.route, Screen.Register.route
)

fun shouldShowBottomNavigation(route: String?): Boolean =
    route != null && route !in publicRoutes
