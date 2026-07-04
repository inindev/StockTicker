package com.github.premnirmal.ticker.home

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.premnirmal.ticker.model.FetchState
import com.github.premnirmal.ticker.model.SparkProvider
import com.github.premnirmal.ticker.navigation.HomeRoute
import com.github.premnirmal.ticker.navigation.rememberScrollToTopAction
import com.github.premnirmal.ticker.network.data.Quote
import com.github.premnirmal.ticker.ui.fadingEdges
import com.github.premnirmal.tickerwidget.R
import org.koin.compose.koinInject

/**
 * Android host for the shared [com.github.premnirmal.ticker.home.WatchlistContent]. Collects the
 * [HomeViewModel] flows (watchlists are sourced from [com.github.premnirmal.ticker.repo.WatchlistRepository]
 * as [WatchlistWidget]s), resolves the localised strings, the 'ic_money' icon, the theme-aware header
 * background, the Android 'QuoteCard'/'TotalHoldingsPopup' slots, the 'RuntimeShader'-based
 * [fadingEdges] and the navigation [rememberScrollToTopAction] registrations, then delegates to the
 * shared screen.
 */
@Composable
fun WatchlistContent(
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel,
    onQuoteClick: (Quote) -> Unit,
) {
    val widgets by viewModel.widgets.collectAsState()
    val fetchState by viewModel.fetchState.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val totalHoldings by viewModel.totalGainLoss.collectAsStateWithLifecycle(initialValue = null)
    val updatedTime = (fetchState as? FetchState.Success)?.let {
        stringResource(R.string.updated_at, it.updatedString)
    }.orEmpty()
    val manageableWatchlists by viewModel.manageableWatchlists.collectAsState(emptyList())
    val sparkProvider = koinInject<SparkProvider>()
    var showManage by remember { mutableStateOf(false) }
    var showNewWatchlist by remember { mutableStateOf(false) }
    WatchlistContent(
        updatedTime = updatedTime,
        hasHoldings = viewModel.hasHoldings,
        isRefreshing = isRefreshing,
        widgets = widgets,
        dropdownArrow = painterResource(R.drawable.ic_arrow_down),
        onManageWatchlists = { showManage = true },
        onNewWatchlist = { showNewWatchlist = true },
        totalGainLoss = totalHoldings,
        totalHoldingsIcon = painterResource(R.drawable.ic_money),
        onRefresh = viewModel::refresh,
        onQuoteClick = onQuoteClick,
        prefetchSparks = { symbols -> sparkProvider.prefetch(symbols) },
        quoteCard = { slot ->
            val sparkData by sparkProvider.spark(slot.quote.symbol).collectAsState()
            QuoteRow(
                quote = slot.quote,
                modifier = slot.modifier,
                dragHandleModifier = slot.dragHandleModifier,
                interactionSource = slot.interactionSource,
                isEditing = slot.isEditing,
                displayMode = slot.displayMode,
                onClick = { slot.onClick() },
                onLongClick = slot.onLongClick,
                onRemoveClick = slot.onRemove,
                onPillClick = slot.onPillClick,
                sparkData = sparkData,
            )
        },
        totalHoldingsPopup = { totals, onDismiss ->
            TotalHoldingsPopup(
                totalHoldings = totals,
                onDismiss = onDismiss,
            )
        },
        modifier = modifier,
        listFadingEdges = { state: ScrollableState -> Modifier.fadingEdges(state) },
        registerResetScroll = { reset ->
            rememberScrollToTopAction(HomeRoute.Watchlist, scrollToTop = reset)
        },
        registerWidgetScroll = { index, scroll ->
            rememberScrollToTopAction(HomeRoute.Watchlist, index, scrollToTop = scroll)
        },
    )

    if (showManage) {
        ManageWatchlistsDialogContent(
            watchlists = manageableWatchlists,
            onCreate = viewModel::createWatchlist,
            onRename = viewModel::renameWatchlist,
            onDelete = viewModel::deleteWatchlist,
            onDismissRequest = { showManage = false },
        )
    }
    if (showNewWatchlist) {
        NewWatchlistPrompt(
            onCreate = viewModel::createWatchlist,
            onDismissRequest = { showNewWatchlist = false },
        )
    }
}
