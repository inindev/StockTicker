package com.github.premnirmal.ticker.navigation

import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController

/**
 * Encapsulates navigation actions for the home tabs (navigate-to-tab, scroll-to-top on reselect).
 */
class HomeNavigationActions(
    private val navController: NavHostController,
    private val viewModel: NavigationViewModel,
) {

    fun navigateTo(destination: HomeBottomNavDestination) {
        navController.navigate(destination.route.route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
        navController.currentBackStackEntry?.let {
            if (it.destination.route == destination.route.route) {
                viewModel.scrollToTop(destination.route)
            }
        }
    }
}
