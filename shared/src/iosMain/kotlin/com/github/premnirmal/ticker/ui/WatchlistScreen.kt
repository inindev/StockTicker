package com.github.premnirmal.ticker.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import com.github.premnirmal.shared.resources.Res
import com.github.premnirmal.shared.resources.ic_arrow_down
import com.github.premnirmal.shared.resources.ic_money
import com.github.premnirmal.shared.resources.updated_at
import com.github.premnirmal.ticker.UserPreferences
import com.github.premnirmal.ticker.components.AppNumberFormat
import com.github.premnirmal.ticker.home.ManageWatchlistItem
import com.github.premnirmal.ticker.home.ManageWatchlistsDialogContent
import com.github.premnirmal.ticker.home.NewWatchlistPrompt
import com.github.premnirmal.ticker.home.QuoteRow
import com.github.premnirmal.ticker.home.TotalGainLoss
import com.github.premnirmal.ticker.home.TotalHoldingsPopup
import com.github.premnirmal.ticker.home.WatchlistContent
import com.github.premnirmal.ticker.home.WatchlistWidget
import com.github.premnirmal.ticker.model.FetchState
import com.github.premnirmal.ticker.model.SparkProvider
import com.github.premnirmal.ticker.model.StocksProvider
import com.github.premnirmal.ticker.navigation.HomeRoute
import com.github.premnirmal.ticker.navigation.rememberScrollToTopAction
import com.github.premnirmal.ticker.network.data.Quote
import com.github.premnirmal.ticker.portfolio.search.CreateWatchlistError
import com.github.premnirmal.ticker.repo.WatchlistRepository
import com.github.premnirmal.ticker.repo.data.Watchlist
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private object WatchlistKoin : KoinComponent {
    val stocksProvider: StocksProvider by inject()
    val userPreferences: UserPreferences by inject()
    val watchlistRepository: WatchlistRepository by inject()
    val sparkProvider: SparkProvider by inject()
}

/**
 * iOS watchlist, rendered with the same shared [WatchlistContent] as Android so the compact top bar
 * (the watchlist dropdown selector and the "Updated ..." fetch time) matches across platforms.
 *
 * Watchlists are first-class: the dropdown lists **every** watchlist from [WatchlistRepository]
 * (All Symbols first, then the user's lists), each as a [WatchlistWidget] whose quotes are that
 * list's symbols resolved against the shared portfolio. The selector's "Manage Watchlists" / "New
 * Watchlist" actions are wired to the shared [ManageWatchlistsDialogContent] / [NewWatchlistPrompt]
 * dialogs (identical to Android). Removing/reordering within a list and creating/renaming/deleting
 * lists all go through the repository; the fetch set derives from All Symbols, so an optimistic
 * [StocksProvider] nudge keeps the UI immediate when All Symbols itself changes.
 */
@Composable
fun WatchlistScreen(
    onQuoteClick: (Quote) -> Unit = {},
) {
    val provider = remember { WatchlistKoin.stocksProvider }
    val userPreferences = remember { WatchlistKoin.userPreferences }
    val repository = remember { WatchlistKoin.watchlistRepository }
    val sparkProvider = remember { WatchlistKoin.sparkProvider }
    val scope = rememberCoroutineScope()

    val quotes by provider.portfolio.collectAsState()
    val fetchState by provider.fetchState.collectAsState()

    var isRefreshing by remember { mutableStateOf(false) }
    val onRefresh: () -> Unit = remember(provider) {
        {
            scope.launch {
                isRefreshing = true
                try {
                    provider.fetch()
                } finally {
                    isRefreshing = false
                }
            }
        }
    }

    // Per-watchlist reorder / remove handlers, mirroring Android's HomeViewModel.
    val onRearrange: (Long, List<String>) -> Unit = remember(repository) {
        { id, tickers -> scope.launch { repository.setSymbols(id, tickers) } }
    }
    val onSetAutoSort: (Boolean) -> Unit = remember(userPreferences) {
        { autoSort -> userPreferences.setAutoSort(autoSort) }
    }
    val onRemove: (Long, String, Boolean) -> Unit = remember(repository, provider) {
        { id, ticker, isAllSymbols ->
            scope.launch {
                repository.removeSymbol(id, ticker)
                if (isAllSymbols) provider.removeStock(ticker)
            }
        }
    }

    // One WatchlistWidget per watchlist (All Symbols first - watchlistsFlow already orders it),
    // each carrying that list's quotes resolved against the shared portfolio.
    val widgets by remember(repository, provider, userPreferences) {
        combine(
            repository.watchlistsFlow(),
            provider.portfolio,
            userPreferences.autoSortFlow,
        ) { watchlists, portfolio, autoSort ->
            val bySymbol = portfolio.associateBy { it.symbol }
            watchlists.map { wl ->
                val listQuotes = wl.symbols.map { bySymbol[it] ?: Quote(symbol = it) }
                    .let { list ->
                        if (autoSort) list.sortedByDescending { it.changeInPercent } else list
                    }
                IosWatchlist(
                    id = wl.id,
                    name = wl.name,
                    isAllSymbols = wl.name == WatchlistRepository.ALL_SYMBOLS_NAME,
                    quotes = listQuotes,
                    onRearrange = onRearrange,
                    onSetAutoSort = onSetAutoSort,
                    onRemove = onRemove,
                )
            }
        }
    }.collectAsState(initial = emptyList())

    // Latest snapshot: backs the manageable-lists dialog and synchronous name validation.
    var watchlists by remember { mutableStateOf<List<Watchlist>>(emptyList()) }
    LaunchedEffect(repository) {
        repository.watchlistsFlow().collect { watchlists = it }
    }
    val manageableWatchlists = remember(watchlists) {
        watchlists.filterNot { it.name == WatchlistRepository.ALL_SYMBOLS_NAME }
            .map { ManageWatchlistItem(id = it.id, name = it.name) }
    }

    val createWatchlist: (String) -> CreateWatchlistError? = { name ->
        val trimmed = name.trim()
        when {
            trimmed.isEmpty() -> CreateWatchlistError.BLANK
            watchlists.any { it.name.equals(trimmed, ignoreCase = true) } ->
                CreateWatchlistError.DUPLICATE
            else -> {
                scope.launch { repository.createWatchlist(trimmed) }
                null
            }
        }
    }
    val renameWatchlist: (Long, String) -> CreateWatchlistError? = { id, name ->
        val trimmed = name.trim()
        when {
            trimmed.isEmpty() -> CreateWatchlistError.BLANK
            watchlists.any { it.id != id && it.name.equals(trimmed, ignoreCase = true) } ->
                CreateWatchlistError.DUPLICATE
            else -> {
                scope.launch { repository.renameWatchlist(id, trimmed) }
                null
            }
        }
    }
    val deleteWatchlist: (Long) -> Unit = { id ->
        scope.launch { repository.deleteWatchlist(id) }
    }

    var showManage by remember { mutableStateOf(false) }
    var showNewWatchlist by remember { mutableStateOf(false) }

    val updatedTime = (fetchState as? FetchState.Success)?.let {
        stringResource(Res.string.updated_at, it.updatedString)
    }.orEmpty()

    val hasHoldings = remember(quotes) { quotes.any { it.hasPositions() } }
    val totalGainLoss = remember(quotes) { quotes.toTotalGainLoss() }

    WatchlistContent(
        updatedTime = updatedTime,
        hasHoldings = hasHoldings,
        isRefreshing = isRefreshing,
        widgets = widgets,
        dropdownArrow = painterResource(Res.drawable.ic_arrow_down),
        onManageWatchlists = { showManage = true },
        onNewWatchlist = { showNewWatchlist = true },
        totalGainLoss = totalGainLoss,
        totalHoldingsIcon = painterResource(Res.drawable.ic_money),
        onRefresh = onRefresh,
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
            onCreate = createWatchlist,
            onRename = renameWatchlist,
            onDelete = deleteWatchlist,
            onDismissRequest = { showManage = false },
        )
    }
    if (showNewWatchlist) {
        NewWatchlistPrompt(
            onCreate = createWatchlist,
            onDismissRequest = { showNewWatchlist = false },
        )
    }
}

/**
 * Builds the pre-formatted [TotalGainLoss] shown in the holdings popup, mirroring the Android
 * 'HomeViewModel.totalGainLoss' computation using the shared [AppNumberFormat].
 */
private fun List<Quote>.toTotalGainLoss(): TotalGainLoss {
    val withPositions = filter { it.hasPositions() }
    val totalHoldings = withPositions.fold(0.0f) { acc, quote -> acc + quote.holdings() }
    var totalGain = 0.0f
    var totalLoss = 0.0f
    for (quote in withPositions) {
        val gainLoss = quote.gainLoss()
        if (gainLoss > 0.0f) {
            totalGain += gainLoss
        } else {
            totalLoss += gainLoss
        }
    }
    val totalHoldingsStr = AppNumberFormat.selected.format(totalHoldings)
    val totalGainStr = "+" + AppNumberFormat.selected.format(totalGain)
    val totalLossStr = if (totalLoss != 0.0f) {
        AppNumberFormat.selected.format(totalLoss)
    } else {
        ""
    }
    return TotalGainLoss(totalHoldingsStr, totalGainStr, totalLossStr)
}

/**
 * Adapts one first-class [Watchlist] to the shared [WatchlistWidget] contract used by
 * [WatchlistContent] (the iOS counterpart of Android's 'HomeWatchlist'). Its [stocks] hold the
 * watchlist's quotes; reorder/auto-sort/remove delegate to the repository-backed handlers. Removing
 * from the master **All Symbols** list also untracks the symbol from the provider.
 */
private class IosWatchlist(
    val id: Long,
    override val name: String,
    private val isAllSymbols: Boolean,
    quotes: List<Quote>,
    private val onRearrange: (Long, List<String>) -> Unit,
    private val onSetAutoSort: (Boolean) -> Unit,
    private val onRemove: (Long, String, Boolean) -> Unit,
) : WatchlistWidget {
    private val _stocks = MutableStateFlow(quotes)
    override val stocks: StateFlow<List<Quote>> = _stocks.asStateFlow()
    override fun rearrange(tickers: List<String>) = onRearrange(id, tickers)
    override fun setAutoSort(autoSort: Boolean) = onSetAutoSort(autoSort)
    override fun removeStock(ticker: String) = onRemove(id, ticker, isAllSymbols)
}