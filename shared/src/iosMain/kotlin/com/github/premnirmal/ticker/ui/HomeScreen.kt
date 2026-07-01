package com.github.premnirmal.ticker.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.github.premnirmal.shared.resources.Res
import com.github.premnirmal.shared.resources.ic_home
import com.github.premnirmal.shared.resources.ic_home_outline
import com.github.premnirmal.shared.resources.ic_news_filled
import com.github.premnirmal.shared.resources.ic_news_outline
import com.github.premnirmal.shared.resources.ic_search
import com.github.premnirmal.shared.resources.ic_settings
import com.github.premnirmal.shared.resources.ic_settings_outline
import com.github.premnirmal.ticker.navigation.Graph
import com.github.premnirmal.ticker.navigation.HomeBottomNavDestination
import com.github.premnirmal.ticker.navigation.HomeNavHost
import com.github.premnirmal.ticker.navigation.HomeNavigationActions
import com.github.premnirmal.ticker.navigation.HomeRoute
import com.github.premnirmal.ticker.navigation.HomeScaffold
import com.github.premnirmal.ticker.navigation.LocalNavGraphViewModelStoreOwner
import com.github.premnirmal.ticker.navigation.NavigationViewModel
import com.github.premnirmal.ticker.navigation.RootNavigationGraph
import com.github.premnirmal.ticker.model.IStocksProvider
import org.jetbrains.compose.resources.painterResource
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private object HomeKoin : KoinComponent {
    val stocksProvider: IStocksProvider by inject()
}

/**
 * iOS entry screen hosting the full shared multiplatform [RootNavigationGraph].
 *
 * A root [NavHostController] drives the shared [RootNavigationGraph], whose `homeContent` slot is the
 * home navigation chrome ([HomeContent]) and whose `quoteDetailContent` slot is the iOS
 * [QuoteDetailScreen]. The home tabs navigate to the quote-detail destination through the same root
 * controller, so the iOS app shares the Android navigation structure. The Watchlist tab renders the
 * shared [WatchlistScreen]; the remaining tabs are lightweight placeholders until their view models
 * can be resolved on iOS.
 */
@Composable
fun HomeScreen() {
    val rootNavController = rememberNavController()
    val onboardingController = rememberOnboardingController()
    LaunchedEffect(Unit) {
        // Mirror Android's HomeActivity, which calls stocksProvider.schedule() on first launch to
        // enqueue the periodic background refresh + cleanup (the iOS BGTaskScheduler requests) and
        // arm the next update.
        HomeKoin.stocksProvider.schedule()
        onboardingController.showIfFirstRun()
    }
    // The home navigation chrome (bottom bar / rail) wraps the root graph inside [HomeContent], so it
    // stays visible when a stock detail opens as a root destination.
    HomeContent(rootNavController, onboardingController)
    OnboardingTutorial(onboardingController)
}

@Composable
private fun HomeContent(
    rootNavController: NavHostController,
    onboardingController: OnboardingController,
) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val whatsNewController = rememberWhatsNewController()

    val viewModelStoreOwner = checkNotNull(LocalViewModelStoreOwner.current) {
        "No ViewModelStoreOwner was provided via LocalViewModelStoreOwner"
    }
    val navigationViewModel = viewModel<NavigationViewModel>(viewModelStoreOwner) {
        NavigationViewModel()
    }
    val homeNavigationActions = remember(navController, navigationViewModel) {
        HomeNavigationActions(navController, navigationViewModel)
    }

    LaunchedEffect(Unit) {
        // Mirror Android's HomeViewModel.checkShowWhatsNew(): present the changelog automatically on
        // the first launch after the app has been updated, recording the installed build version.
        whatsNewController.checkShowOnLaunch(iosVersionCode())
    }

    val destinations = listOf(
        HomeBottomNavDestination(
            route = HomeRoute.Watchlist,
            selectedIcon = painterResource(Res.drawable.ic_home),
            unselectedIcon = painterResource(Res.drawable.ic_home_outline),
            label = "Home"
        ),
        HomeBottomNavDestination(
            route = HomeRoute.Trending,
            selectedIcon = painterResource(Res.drawable.ic_news_filled),
            unselectedIcon = painterResource(Res.drawable.ic_news_outline),
            label = "News"
        ),
        HomeBottomNavDestination(
            route = HomeRoute.Settings,
            selectedIcon = painterResource(Res.drawable.ic_settings),
            unselectedIcon = painterResource(Res.drawable.ic_settings_outline),
            label = "Settings"
        ),
        HomeBottomNavDestination(
            route = HomeRoute.Search,
            selectedIcon = painterResource(Res.drawable.ic_search),
            unselectedIcon = painterResource(Res.drawable.ic_search),
            label = "Search"
        )
    )

    // The bar wraps the *root* graph (home tabs + quote detail), so it stays visible when a stock
    // detail opens as a root destination. On detail no tab is highlighted; tapping a tab returns to
    // home and selects it.
    val rootBackStackEntry by rootNavController.currentBackStackEntryAsState()
    val onHome = rootBackStackEntry?.destination?.route == Graph.HOME
    val homeBackStackEntry by navController.currentBackStackEntryAsState()
    val selectedDestination = if (onHome) {
        homeBackStackEntry?.destination?.route ?: HomeRoute.Watchlist.route
    } else {
        ""
    }

    CompositionLocalProvider(LocalNavGraphViewModelStoreOwner provides viewModelStoreOwner) {
        BoxWithConstraints {
            // Drive navigation/content type off the measured width so iPad (and Split View / Slide
            // Over) gets the navigation rail and the dual-pane watchlist list/detail, while iPhone
            // (and a narrow Split View) keeps the bottom bar and full-screen detail navigation.
            val (navigationType, contentType) =
                iosContentAndNavigationType(maxWidth)
            val navigationContentPosition = if (navigationType == NavigationType.NAVIGATION_RAIL) {
                NavigationContentPosition.CENTER
            } else {
                NavigationContentPosition.TOP
            }
            HomeScaffold(
                navigationType = navigationType,
                selectedDestination = selectedDestination,
                destinations = destinations,
                navigationContentPosition = navigationContentPosition,
                snackbarHostState = snackbarHostState,
                navigateToTopLevelDestination = { destination ->
                    if (!onHome) {
                        rootNavController.popBackStack(Graph.HOME, inclusive = false)
                    }
                    homeNavigationActions.navigateTo(destination)
                },
                navHost = { modifier ->
                    RootNavigationGraph(
                        navHostController = rootNavController,
                        modifier = modifier,
                        disableTransitions = navigationType == NavigationType.NAVIGATION_RAIL,
                        homeContent = {
                            HomeNavHost(
                                navController = navController,
                                modifier = Modifier.fillMaxSize(),
                                disableTransitions = navigationType == NavigationType.NAVIGATION_RAIL,
                                watchlist = {
                                    WatchlistPane(
                                        contentType = contentType,
                                        onQuoteClick = { quote ->
                                            rootNavController.navigate("${Graph.QUOTE_DETAIL}/${quote.symbol}")
                                        }
                                    )
                                },
                                trending = {
                                    TrendingScreen(
                                        onQuoteClick = { quote ->
                                            rootNavController.navigate("${Graph.QUOTE_DETAIL}/${quote.symbol}")
                                        }
                                    )
                                },
                                search = {
                                    SearchScreen(
                                        onQuoteClick = { quote ->
                                            rootNavController.navigate("${Graph.QUOTE_DETAIL}/${quote.symbol}")
                                        }
                                    )
                                },
                                widgets = {},
                                settings = {
                                    SettingsScreen(
                                        onWhatsNew = { whatsNewController.show(iosVersionCode()) },
                                        onTutorial = { onboardingController.show() },
                                    )
                                }
                            )
                        },
                        quoteDetailContent = { symbol ->
                            QuoteDetailScreen(
                                symbol = symbol,
                                onBack = { rootNavController.popBackStack() }
                            )
                        },
                    )
                }
            )
        }
    }
    WhatsNewBottomSheet(controller = whatsNewController, versionName = iosVersionName())
}

/**
 * The Watchlist home tab. On a compact window it renders the plain [WatchlistScreen] and navigates to
 * a full-screen quote detail through [onQuoteClick] (the iPhone behaviour). On a wide window
 * ([ContentType.DUAL_PANE], i.e. iPad / wide Split View) it shows the shared [ListDetail] with the
 * watchlist on the left and the selected quote's detail on the right — mirroring Android's
 * `WatchlistScreen` list/detail. The detail pane forces `SINGLE_PANE` so its own internal layout
 * stays a single column.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun WatchlistPane(
    contentType: ContentType,
    onQuoteClick: (com.github.premnirmal.ticker.network.data.Quote) -> Unit,
) {
    if (contentType != ContentType.DUAL_PANE) {
        WatchlistScreen(onQuoteClick = onQuoteClick)
        return
    }

    // Track only the selected symbol (a saveable String) rather than the whole Quote so the
    // selection survives configuration changes without requiring Quote to be saveable.
    var selectedSymbol by rememberSaveable { mutableStateOf<String?>(null) }
    var isDetailOpen by rememberSaveable { mutableStateOf(true) }

    ListDetail(
        isDetailOpen = isDetailOpen,
        setIsDetailOpen = {
            if (!it) {
                selectedSymbol = null
            }
            isDetailOpen = it
        },
        showListAndDetail = true,
        detailKey = selectedSymbol ?: "",
        splitFraction = 1f / 2.25f,
        list = {
            WatchlistScreen(
                onQuoteClick = { quote ->
                    selectedSymbol = quote.symbol
                    isDetailOpen = true
                },
            )
        },
        detail = {
            val symbol = selectedSymbol
            if (symbol != null) {
                QuoteDetailScreen(
                    symbol = symbol,
                    onBack = {
                        selectedSymbol = null
                        isDetailOpen = false
                    },
                    contentType = ContentType.SINGLE_PANE
                )
            } else {
                EmptyState(text = "Select a stock from your watchlist")
            }
        },
        backHandler = { onBack -> BackHandler { onBack() } },
    )
}
