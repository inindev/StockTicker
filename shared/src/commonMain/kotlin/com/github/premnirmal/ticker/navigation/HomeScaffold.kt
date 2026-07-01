package com.github.premnirmal.ticker.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.premnirmal.ticker.ui.NavigationContentPosition
import com.github.premnirmal.ticker.ui.NavigationType

/**
 * Multiplatform scaffold for the home screen. Chooses between bottom-navigation and rail layouts
 * depending on [navigationType]. All content (the nav host, bottom bar, and rail) is supplied via
 * composable slots or pre-built navigation components, keeping this free of Android resource
 * dependencies.
 */
@Composable
fun HomeScaffold(
    navigationType: NavigationType,
    selectedDestination: String,
    destinations: List<HomeBottomNavDestination>,
    navigationContentPosition: NavigationContentPosition,
    snackbarHostState: SnackbarHostState,
    navigateToTopLevelDestination: (HomeBottomNavDestination) -> Unit,
    navHost: @Composable (Modifier) -> Unit,
) {
    if (navigationType == NavigationType.BOTTOM_NAVIGATION) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
            // Draw edge-to-edge: each home screen's top bar applies its own status-bar inset, and the
            // NavigationBar applies its own navigation-bar inset.
            contentWindowInsets = WindowInsets(0.dp),
            snackbarHost = {
                SnackbarHost(hostState = snackbarHostState)
            }
        ) { _ ->
            // Solid, non-floating bar: lay the nav host above it in a column so content sits on top of
            // the bar rather than scrolling behind it (no glass backdrop, no bottom overlap padding).
            Column(modifier = Modifier.fillMaxSize()) {
                navHost(Modifier.weight(1f))
                BottomNavigationBar(
                    selectedDestination = selectedDestination,
                    navigateToTopLevelDestination = navigateToTopLevelDestination,
                    destinations = destinations,
                )
            }
        }
    } else {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
            // Draw edge-to-edge (like the bottom-navigation branch): the navigation rail and each
            // home screen's top bar apply their own status-bar insets. Padding the scaffold here
            // would leave a blank surface-coloured strip across the top in landscape (a white row).
            contentWindowInsets = WindowInsets(0.dp),
            snackbarHost = {
                SnackbarHost(hostState = snackbarHostState)
            }
        ) { _ ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                HomeNavigationRail(
                    selectedDestination = selectedDestination,
                    navigationContentPosition = navigationContentPosition,
                    navigateToTopLevelDestination = navigateToTopLevelDestination,
                    destinations = destinations
                )
                navHost(Modifier.weight(1f))
            }
        }
    }
}
