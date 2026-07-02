package com.github.premnirmal.ticker.portfolio.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.premnirmal.ticker.model.IStocksProvider
import com.github.premnirmal.ticker.repo.WatchlistRepository
import com.github.premnirmal.ticker.repo.data.Watchlist
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Backs the shared "Add to Watchlist" dialog. Reads watchlists from [WatchlistRepository] and mutates
 * membership through it. The fetch set ([IStocksProvider]) is derived from the **All Symbols** master,
 * so the repository write alone is enough to track/untrack; the [IStocksProvider] calls here are a
 * redundant optimistic nudge for immediacy:
 *  - adding a symbol to any list also tracks it (so quotes are fetched);
 *  - removing it from the **All Symbols** master untracks it entirely; removing it from a subset
 *    leaves it tracked and in All Symbols.
 */
class SuggestionViewModel constructor(
    private val symbol: String,
    private val watchlistRepository: WatchlistRepository,
    private val stocksProvider: IStocksProvider,
) : ViewModel() {

    val suggestionState: Flow<SuggestionState>
        get() = _suggestionState
    private val _suggestionState = MutableStateFlow(SuggestionState(symbol, emptyList()))

    // Latest watchlist snapshot, used for synchronous duplicate-name validation in createWatchlist.
    private var watchlists: List<Watchlist> = emptyList()

    init {
        viewModelScope.launch {
            watchlistRepository.watchlistsFlow().collect { lists ->
                watchlists = lists
                _suggestionState.value = SuggestionState(
                    symbol = symbol,
                    widgetDataList = lists.map { watchlist ->
                        SuggestionWidgetDataState(
                            symbol = symbol,
                            watchlistName = watchlist.name,
                            watchlistId = watchlist.id,
                            exists = watchlist.symbols.contains(symbol),
                        )
                    },
                )
            }
        }
    }

    fun removeFromWatchlist(state: SuggestionWidgetDataState) {
        viewModelScope.launch {
            watchlistRepository.removeSymbol(state.watchlistId, symbol)
            if (state.watchlistName == WatchlistRepository.ALL_SYMBOLS_NAME) {
                stocksProvider.removeStock(symbol)
            }
        }
    }

    fun addToWatchlist(state: SuggestionWidgetDataState) {
        viewModelScope.launch {
            watchlistRepository.addSymbol(state.watchlistId, symbol)
            stocksProvider.addStock(symbol)
        }
    }

    /**
     * Creates a new watchlist named [name] and adds the current [symbol] to it. Returns a
     * [CreateWatchlistError] when [name] is blank or already used, or 'null' on success. The dialog's
     * checkbox list refreshes automatically via the reactive watchlists flow.
     */
    fun createWatchlist(name: String): CreateWatchlistError? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return CreateWatchlistError.BLANK
        val duplicate = watchlists.any { it.name.equals(trimmed, ignoreCase = true) }
        if (duplicate) return CreateWatchlistError.DUPLICATE

        viewModelScope.launch {
            val newId = watchlistRepository.createWatchlist(trimmed)
            watchlistRepository.addSymbol(newId, symbol)
            stocksProvider.addStock(symbol)
        }
        return null
    }
}