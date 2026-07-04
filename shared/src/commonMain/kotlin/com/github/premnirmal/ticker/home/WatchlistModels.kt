package com.github.premnirmal.ticker.home

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Modifier
import com.github.premnirmal.ticker.network.data.Quote
import kotlinx.coroutines.flow.StateFlow

/**
 * Platform-agnostic view of a single watchlist/widget shown as a tab in [WatchlistContent]. The
 * Android 'WidgetData' is adapted to this interface by the ':app' host so the shared screen does not
 * depend on the Glance/'SharedPreferences'-backed widget model.
 */
interface WatchlistWidget {
    val name: String
    val stocks: StateFlow<List<Quote>>
    fun rearrange(tickers: List<String>)
    fun setAutoSort(autoSort: Boolean)
    fun removeStock(ticker: String)
}

/**
 * Pre-formatted total holdings / gain / loss strings rendered by the total-holdings popup. The
 * locale-aware number formatting is done by the host (which owns the platform 'NumberFormat').
 */
data class TotalGainLoss(
    val holdings: String,
    val gain: String,
    val loss: String
)

/**
 * Everything [WatchlistContent] hands the hosts' quote-row slot for one row. Bundled into one
 * object (rather than a wide positional lambda) because the row needs two modifiers and several
 * callbacks that would otherwise be swap-prone: [modifier] styles the row itself while
 * [dragHandleModifier] is the reorder library's handle modifier (only meaningful while
 * [isEditing]); [onLongClick] enters edit mode and [onRemove] deletes the quote from the list.
 */
class QuoteRowSlot(
    val quote: Quote,
    val modifier: Modifier,
    val dragHandleModifier: Modifier,
    val interactionSource: MutableInteractionSource,
    val isEditing: Boolean,
    val displayMode: ChangeDisplayMode,
    val onClick: () -> Unit,
    val onLongClick: () -> Unit,
    val onRemove: (Quote) -> Unit,
    val onPillClick: () -> Unit,
)
