package com.github.premnirmal.ticker.home

import android.app.Application
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.github.premnirmal.ticker.AppPreferences
import com.github.premnirmal.ticker.createTimeString
import com.github.premnirmal.ticker.model.AlarmScheduler
import com.github.premnirmal.ticker.model.FetchState
import com.github.premnirmal.ticker.model.StocksProvider
import com.github.premnirmal.ticker.network.CommitsProvider
import com.github.premnirmal.ticker.network.NewsProvider
import com.github.premnirmal.ticker.network.data.Quote
import com.github.premnirmal.ticker.notifications.NotificationsHandler
import com.github.premnirmal.ticker.portfolio.search.CreateWatchlistError
import com.github.premnirmal.ticker.repo.WatchlistRepository
import com.github.premnirmal.ticker.repo.data.Watchlist
import com.github.premnirmal.ticker.ui.AppMessaging
import com.github.premnirmal.ticker.widget.WidgetDataProvider
import com.github.premnirmal.tickerwidget.BuildConfig
import com.github.premnirmal.tickerwidget.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

class HomeViewModel constructor(
    application: Application,
    private val stocksProvider: StocksProvider,
    private val appPreferences: AppPreferences,
    private val newsProvider: NewsProvider,
    private val widgetDataProvider: WidgetDataProvider,
    private val watchlistRepository: WatchlistRepository,
    private val notificationsHandler: NotificationsHandler,
    private val appMessaging: AppMessaging,
    private val alarmScheduler: AlarmScheduler,
    private val commitsProvider: CommitsProvider,
) : AndroidViewModel(application) {

    val fetchState: StateFlow<FetchState>
        get() = stocksProvider.fetchState
    val nextFetch: Flow<String>
        get() = stocksProvider.nextFetchMs.map {
            val instant = Instant.ofEpochMilli(it)
            val time = ZonedDateTime.ofInstant(instant, ZoneId.systemDefault())
            time.createTimeString()
        }

    val isRefreshing: StateFlow<Boolean>
        get() = _isRefreshing
    private val _isRefreshing = MutableStateFlow(false)

    /**
     * The in-app watchlists, sourced from [WatchlistRepository]. Reactively combines each
     * watchlist's ordered symbols with the live [StocksProvider.portfolio] quotes and the view's
     * global auto-sort setting, so the list updates automatically after a fetch or any add/remove/reorder.
     * Symbols not yet fetched render as a placeholder [Quote].
     */
    val widgets: StateFlow<List<WatchlistWidget>> =
        combine(
            watchlistRepository.watchlistsFlow(),
            stocksProvider.portfolio,
            appPreferences.autoSortFlow,
        ) { watchlists, portfolio, autoSort ->
            val bySymbol = portfolio.associateBy { it.symbol }
            watchlists.map { wl ->
                val quotes = wl.symbols.map { bySymbol[it] ?: Quote(symbol = it) }
                    .let { list ->
                        if (autoSort) list.sortedByDescending { it.changeInPercent } else list
                    }
                HomeWatchlist(
                    id = wl.id,
                    name = wl.name,
                    isAllSymbols = wl.name == WatchlistRepository.ALL_SYMBOLS_NAME,
                    quotes = quotes,
                    onRearrange = ::rearrangeWatchlist,
                    onSetAutoSort = appPreferences::setAutoSort,
                    onRemove = ::removeFromWatchlist,
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val hasWidget: Flow<Boolean>
        get() = widgetDataProvider.hasWidget

    val hasHoldings: Boolean
        get() = stocksProvider.hasPositions()

    val showAlarmPermissionRequest: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmScheduler.canScheduleExactAlarm()

    private var fetchJob: Job? = null

    /** The manageable watchlists (All Symbols excluded - it can't be renamed/deleted). */
    val manageableWatchlists: Flow<List<ManageWatchlistItem>> =
        watchlistRepository.watchlistsFlow().map { lists ->
            lists.filterNot { it.name == WatchlistRepository.ALL_SYMBOLS_NAME }
                .map { ManageWatchlistItem(id = it.id, name = it.name) }
        }

    // Latest snapshot for synchronous name validation in create/rename.
    private var watchlistsCache: List<Watchlist> = emptyList()

    init {
        initCaches()
        viewModelScope.launch {
            watchlistRepository.watchlistsFlow().collect { watchlistsCache = it }
        }
    }

    /** Creates a watchlist; returns an error for blank/duplicate names, or 'null' on success. */
    fun createWatchlist(name: String): CreateWatchlistError? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return CreateWatchlistError.BLANK
        if (watchlistsCache.any { it.name.equals(trimmed, ignoreCase = true) }) {
            return CreateWatchlistError.DUPLICATE
        }
        viewModelScope.launch { watchlistRepository.createWatchlist(trimmed) }
        return null
    }

    /** Renames watchlist [id]; returns an error for blank/duplicate names, or 'null' on success. */
    fun renameWatchlist(id: Long, name: String): CreateWatchlistError? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return CreateWatchlistError.BLANK
        if (watchlistsCache.any { it.id != id && it.name.equals(trimmed, ignoreCase = true) }) {
            return CreateWatchlistError.DUPLICATE
        }
        viewModelScope.launch { watchlistRepository.renameWatchlist(id, trimmed) }
        return null
    }

    /** Deletes watchlist [id]. Its symbols remain in All Symbols; any widget on it falls back to All Symbols. */
    fun deleteWatchlist(id: Long) {
        viewModelScope.launch { watchlistRepository.deleteWatchlist(id) }
    }

    /** Reorder persists to the watchlist store; the reactive [widgets] flow re-emits the new order. */
    private fun rearrangeWatchlist(id: Long, tickers: List<String>) {
        viewModelScope.launch { watchlistRepository.setSymbols(id, tickers) }
    }

    /**
     * Removes [ticker] from the watchlist [id]. Removing from a subset just drops the membership;
     * removing from the master All Symbols list stops tracking the symbol entirely. The fetch set is
     * derived from All Symbols, so the [stocksProvider] call is a redundant optimistic nudge - the
     * repository removal alone untracks the symbol.
     */
    private fun removeFromWatchlist(id: Long, ticker: String, isAllSymbols: Boolean) {
        viewModelScope.launch {
            watchlistRepository.removeSymbol(id, ticker)
            if (isAllSymbols) {
                stocksProvider.removeStock(ticker)
            }
        }
    }

    private fun initCaches() {
        newsProvider.initCache()
    }

    fun initNotifications() {
        notificationsHandler.initialize()
    }

    val totalGainLoss: Flow<TotalGainLoss>
        get() = stocksProvider.portfolio.map { portfolio ->
            val totalHoldings = portfolio.filter { it.hasPositions() }.sumOf { quote ->
                quote.holdings().toDouble()
            }
            val totalHoldingsStr = appPreferences.selectedDecimalFormat.format(totalHoldings)
            var totalGain = 0.0f
            var totalLoss = 0.0f
            val quotesWithPositions = portfolio.filter { it.hasPositions() }
            for (quote in quotesWithPositions) {
                val gainLoss = quote.gainLoss()
                if (gainLoss > 0.0f) {
                    totalGain += gainLoss
                } else {
                    totalLoss += gainLoss
                }
            }
            val totalGainStr = "+" + appPreferences.selectedDecimalFormat.format(totalGain)
            val totalLossStr = if (totalLoss != 0.0f) {
                appPreferences.selectedDecimalFormat.format(totalLoss)
            } else {
                ""
            }
            TotalGainLoss(totalHoldingsStr, totalGainStr, totalLossStr)
        }

    fun checkShowTutorial() {
        val tutorialShown = appPreferences.tutorialShown()
        if (!tutorialShown) {
            showTutorial()
        }
    }

    fun showTutorial() {
        viewModelScope.launch {
            val title = getApplication<Application>().getString(R.string.how_to_title)
            val message = getApplication<Application>().getString(R.string.how_to)
            appMessaging.sendBottomSheet(title = title, message = message)
            appPreferences.setTutorialShown(true)
        }
    }

    fun checkShowWhatsNew() {
        if (appPreferences.getLastSavedVersionCode() < BuildConfig.VERSION_CODE) {
            showWhatsNew()
        }
    }

    fun showWhatsNew() {
        viewModelScope.launch {
            val whatsNewResult = commitsProvider.loadWhatsNew()
            val title = getApplication<Application>().getString(R.string.whats_new_in, BuildConfig.VERSION_NAME)
            val message = with(whatsNewResult) {
                if (wasSuccessful) {
                    appPreferences.saveVersionCode(BuildConfig.VERSION_CODE)
                    data.joinToString("\n\u25CF ", "\u25CF ")
                } else {
                    "${getApplication<Application>().getString(
                        R.string.error_fetching_whats_new
                    )}\n\n :( ${error.message.orEmpty()}"
                }
            }
            appMessaging.sendBottomSheet(title = title, message = message)
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            stocksProvider.fetch()
        }.invokeOnCompletion {
            _isRefreshing.value = false
        }
    }

    fun fetchPortfolioInRealTime() {
        fetchJob = viewModelScope.launch(Dispatchers.Default) {
            do {
                var isMarketOpen = false
                val result = stocksProvider.fetch(false)
                if (result.wasSuccessful) {
                    isMarketOpen = result.data.any { it.isMarketOpen }
                }
                delay(StocksProvider.DEFAULT_INTERVAL_MS)
            } while (result.wasSuccessful && isMarketOpen)
        }
    }

    fun stopRealTimeFetch() {
        fetchJob?.cancel()
    }
}

/**
 * Repository-backed [WatchlistWidget] for the in-app watchlist screen. A lightweight, immutable
 * snapshot rebuilt by [HomeViewModel.widgets] whenever the underlying data changes; its [stocks] is a
 * plain state holder (no per-widget coroutine), and its mutations delegate back to the view model,
 * which persists them through the [WatchlistRepository].
 */
private class HomeWatchlist(
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
