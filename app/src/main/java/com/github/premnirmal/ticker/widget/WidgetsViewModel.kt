package com.github.premnirmal.ticker.widget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.premnirmal.ticker.model.FetchState
import com.github.premnirmal.ticker.model.StocksProvider
import com.github.premnirmal.ticker.repo.WatchlistRepository
import com.github.premnirmal.ticker.repo.data.Watchlist
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class WidgetsViewModel constructor(
    private val stocksProvider: StocksProvider,
    private val widgetDataProvider: WidgetDataProvider,
    private val watchlistRepository: WatchlistRepository,
) : ViewModel() {

    val widgetDataList: Flow<List<WidgetData>>
        get() = widgetDataProvider.widgetData

    /** The watchlists a widget can be pointed at, for the config screen's "Watchlist" dropdown. */
    val watchlists: Flow<List<Watchlist>>
        get() = watchlistRepository.watchlistsFlow()

    val fetchState: StateFlow<FetchState>
        get() = stocksProvider.fetchState

    fun refreshWidgets() {
        viewModelScope.launch {
            widgetDataProvider.refreshWidgetDataList()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            stocksProvider.fetch()
        }
    }
}
