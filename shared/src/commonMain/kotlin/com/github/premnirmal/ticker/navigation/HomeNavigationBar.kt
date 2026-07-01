package com.github.premnirmal.ticker.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import com.github.premnirmal.ticker.ui.NavigationContentPosition
import com.github.premnirmal.ticker.ui.NavigationContentPosition.CENTER
import com.github.premnirmal.ticker.ui.NavigationContentPosition.TOP

/**
 * Vertical space reserved for content sitting behind a floating bottom bar. Retained so screens that
 * read it unconditionally keep compiling; the bar is now a solid, non-floating [NavigationBar] laid
 * out beneath the content (not overlaying it), so no extra bottom padding is required and this stays
 * at its `0.dp` default.
 */
val LocalContentBottomPadding = compositionLocalOf { 0.dp }

/**
 * Layout-id enum for positioning content in the [HomeNavigationRail] custom layout.
 */
enum class LayoutType {
    HEADER,
    CONTENT
}

/**
 * Multiplatform data class describing a home bottom-navigation/rail destination. Uses resolved
 * [Painter] icons and [String] labels so that it is free of Android resource IDs. [selectedIcon] is
 * shown for the active destination (filled) and [unselectedIcon] for the rest (outline).
 */
data class HomeBottomNavDestination(
    val route: HomeRoute,
    val selectedIcon: Painter,
    val unselectedIcon: Painter,
    val label: String,
    val contentDescription: String = label,
    val enabled: Boolean = true
)

/**
 * Multiplatform bottom navigation bar for the home screen: a flat, full-width Material
 * [NavigationBar] identical across platforms and window sizes. Each item shows its icon above its
 * label; the active item swaps to its filled [HomeBottomNavDestination.selectedIcon] and takes the
 * primary tint, while the rest use their outline icon. The selection indicator pill is made
 * transparent so selection reads purely as the tint change (matching the reference design).
 */
@Composable
fun BottomNavigationBar(
    selectedDestination: String,
    destinations: List<HomeBottomNavDestination>,
    navigateToTopLevelDestination: (HomeBottomNavDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBar(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        NavigationBarItems(selectedDestination, destinations, navigateToTopLevelDestination)
    }
}

@Composable
private fun RowScope.NavigationBarItems(
    selectedDestination: String,
    destinations: List<HomeBottomNavDestination>,
    navigateToTopLevelDestination: (HomeBottomNavDestination) -> Unit,
) {
    destinations.forEach { destination ->
        val selected = selectedDestination == destination.route.route
        NavigationBarItem(
            selected = selected,
            onClick = { navigateToTopLevelDestination(destination) },
            enabled = destination.enabled,
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                indicatorColor = Color.Transparent,
            ),
            icon = {
                Icon(
                    painter = if (selected) destination.selectedIcon else destination.unselectedIcon,
                    contentDescription = destination.contentDescription,
                )
            },
            label = {
                Text(
                    text = destination.label,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        )
    }
}

/**
 * Multiplatform navigation rail for the home screen (used on larger/expanded widths). Mirrors the
 * bottom bar's filled-when-selected / outline-otherwise icon treatment.
 */
@Composable
fun HomeNavigationRail(
    selectedDestination: String,
    destinations: List<HomeBottomNavDestination>,
    navigationContentPosition: NavigationContentPosition,
    navigateToTopLevelDestination: (HomeBottomNavDestination) -> Unit
) {
    NavigationRail(
        modifier = Modifier.fillMaxHeight(),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Layout(
            modifier = Modifier.widthIn(max = 80.dp),
            content = {
                Column(
                    modifier = Modifier.layoutId(LayoutType.HEADER),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Spacer(Modifier.height(8.dp))
                    Spacer(Modifier.height(4.dp))
                }

                Column(
                    modifier = Modifier.layoutId(LayoutType.CONTENT),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    destinations.forEach { destination ->
                        val selected = selectedDestination == destination.route.route
                        NavigationRailItem(
                            selected = selected,
                            onClick = { navigateToTopLevelDestination(destination) },
                            enabled = destination.enabled,
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                indicatorColor = Color.Transparent,
                            ),
                            icon = {
                                Icon(
                                    painter = if (selected) {
                                        destination.selectedIcon
                                    } else {
                                        destination.unselectedIcon
                                    },
                                    contentDescription = destination.contentDescription,
                                    tint = if (!destination.enabled) {
                                        LocalContentColor.current.copy(alpha = 0.2f)
                                    } else {
                                        LocalContentColor.current
                                    }
                                )
                            }
                        )
                    }
                }
            },
            measurePolicy = { measurables, constraints ->
                lateinit var headerMeasurable: Measurable
                lateinit var contentMeasurable: Measurable
                measurables.forEach {
                    when (it.layoutId) {
                        LayoutType.HEADER -> headerMeasurable = it
                        LayoutType.CONTENT -> contentMeasurable = it
                        else -> error("Unknown layoutId encountered!")
                    }
                }

                val headerPlaceable = headerMeasurable.measure(constraints)
                val contentPlaceable = contentMeasurable.measure(
                    constraints.offset(vertical = -headerPlaceable.height)
                )
                layout(constraints.maxWidth, constraints.maxHeight) {
                    headerPlaceable.placeRelative(0, 0)

                    val nonContentVerticalSpace = constraints.maxHeight - contentPlaceable.height

                    val contentPlaceableY = when (navigationContentPosition) {
                        TOP -> 0
                        CENTER -> nonContentVerticalSpace / 2
                    }
                        .coerceAtLeast(headerPlaceable.height)

                    contentPlaceable.placeRelative(0, contentPlaceableY)
                }
            }
        )
    }
}