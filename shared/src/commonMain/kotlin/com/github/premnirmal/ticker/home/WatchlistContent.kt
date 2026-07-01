package com.github.premnirmal.ticker.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.premnirmal.ticker.navigation.LocalContentBottomPadding
import com.github.premnirmal.ticker.network.data.Quote
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyStaggeredGridState

/**
 * Home watchlist screen, shared by Android and iOS. The screen is stateless: the state it renders
 * and the events it raises are hoisted as parameters so it has no Android, navigation or
 * dependency-injection dependencies.
 *
 * The top bar is a single compact row — a [WatchlistWidget] dropdown selector on the left and the
 * "Updated …" fetch time ([updatedTime]) on the right — sitting directly above the swipeable pager of
 * per-widget grids. Selecting a widget from the dropdown scrolls the pager to that page, and swiping
 * the pager updates the selected entry, so the two stay in sync.
 *  - the watchlist pages come from [widgets] and the dropdown chevron from [dropdownArrow],
 *  - the refresh state/event is [isRefreshing]/[onRefresh] and the quote tap is [onQuoteClick],
 *  - the quote card is a composable [quoteCard] slot (it still pulls in the not-yet-shared theme),
 *  - the navigation scroll-to-top registrations are [registerResetScroll]/[registerWidgetScroll].
 *
 * The Android `WatchlistContent` host in `:app` supplies them.
 *
 * TODO(bottom-bar-refactor): the total-holdings button that previously lived in this screen's top bar
 *  is moving into the home bottom navigation bar. The [hasHoldings]/[totalGainLoss]/[totalHoldingsIcon]
 *  inputs and the [totalHoldingsPopup] slot are kept here so that relocation can reuse them; the popup
 *  is retained but currently has no trigger.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchlistContent(
    updatedTime: String,
    isRefreshing: Boolean,
    widgets: List<WatchlistWidget>,
    dropdownArrow: Painter,
    hasHoldings: Boolean,
    totalGainLoss: TotalGainLoss?,
    totalHoldingsIcon: Painter,
    onRefresh: () -> Unit,
    onQuoteClick: (Quote) -> Unit,
    quoteCard: @Composable (
        quote: Quote,
        modifier: Modifier,
        interactionSource: MutableInteractionSource,
        onClick: () -> Unit,
        onRemoveClick: (Quote) -> Unit,
    ) -> Unit,
    totalHoldingsPopup: @Composable (totalHoldings: TotalGainLoss, onDismiss: () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
    listFadingEdges: (ScrollableState) -> Modifier = { Modifier },
    registerResetScroll: @Composable (reset: suspend () -> Unit) -> Unit = {},
    registerWidgetScroll: @Composable (index: Int, scroll: suspend () -> Unit) -> Unit = { _, _ -> },
) {
    val density = LocalDensity.current
    var showTotalHoldingsPopup by remember { mutableStateOf(false) }
    BoxWithConstraints(modifier = modifier) {
        val constraints = this.constraints
        val rowState = rememberLazyListState()
        val coroutineScope = rememberCoroutineScope()
        val hapticFeedback = LocalHapticFeedback.current
        val windowInfo = LocalWindowInfo.current
        val gridSize = remember(windowInfo.containerSize, constraints.maxWidth, constraints.maxHeight) {
            // Cap the grid to the window when its size is known, but fall back to the available
            // layout constraints when the window size is reported as zero. On iOS the window's
            // containerSize is momentarily 0 after the app is backgrounded and reopened, and taking
            // min(..., 0) would size the grid to 0 width and leave the watchlist entirely blank.
            val containerWidth = windowInfo.containerSize.width
            val containerHeight = windowInfo.containerSize.height
            val effectiveWidth =
                if (containerWidth > 0) min(constraints.maxWidth, containerWidth) else constraints.maxWidth
            val effectiveHeight =
                if (containerHeight > 0) containerHeight else constraints.maxHeight
            val widthDp = with(density) { effectiveWidth.toDp() }
            val heightDp = with(density) { effectiveHeight.toDp() }
            DpSize(widthDp, heightDp)
        }
        val selectedItemIndex by remember {
            derivedStateOf {
                val layoutInfo = rowState.layoutInfo
                val visibleItems = layoutInfo.visibleItemsInfo
                val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
                visibleItems.minByOrNull { abs((it.offset + it.size / 2) - viewportCenter) }?.index ?: 0
            }
        }
        // The header no longer collapses, so re-selecting the tab only scrolls the grids to the top
        // (handled per-page by registerWidgetScroll); there is nothing to reset here.
        registerResetScroll { }
        Column(modifier = Modifier.fillMaxSize()) {
            WatchlistTopBar(
                widgets = widgets,
                selectedItemIndex = selectedItemIndex,
                updatedTime = updatedTime,
                dropdownArrow = dropdownArrow,
                onWidgetSelected = { index ->
                    coroutineScope.launch { rowState.animateScrollToItem(index) }
                },
            )
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                Content(
                    widgets = widgets,
                    gridSize = gridSize,
                    rowState = rowState,
                    hapticFeedback = hapticFeedback,
                    isRefreshing = isRefreshing,
                    onRefresh = onRefresh,
                    onQuoteClick = onQuoteClick,
                    quoteCard = quoteCard,
                    listFadingEdges = listFadingEdges,
                    registerWidgetScroll = registerWidgetScroll,
                )
            }
        }
        if (showTotalHoldingsPopup && totalGainLoss != null) {
            totalHoldingsPopup(totalGainLoss) {
                showTotalHoldingsPopup = false
            }
        }
    }
}

/**
 * Compact watchlist top bar: the watchlist [WatchlistWidget] dropdown selector on the left and the
 * "Updated …" fetch time on the right, on a single row that clears the status bar.
 */
@Composable
private fun WatchlistTopBar(
    widgets: List<WatchlistWidget>,
    selectedItemIndex: Int,
    updatedTime: String,
    dropdownArrow: Painter,
    onWidgetSelected: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (widgets.isNotEmpty()) {
                WatchlistSelector(
                    widgets = widgets,
                    selectedItemIndex = selectedItemIndex.coerceIn(0, widgets.lastIndex),
                    dropdownArrow = dropdownArrow,
                    onWidgetSelected = onWidgetSelected,
                )
            }
        }
        if (updatedTime.isNotEmpty()) {
            Text(
                modifier = Modifier.offset(y = 10.dp),
                text = updatedTime,
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 14.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                textAlign = TextAlign.End,
            )
        }
    }
}

/** The watchlist name shown as a dropdown; selecting an entry scrolls the pager to that page. */
@Composable
private fun WatchlistSelector(
    widgets: List<WatchlistWidget>,
    selectedItemIndex: Int,
    dropdownArrow: Painter,
    onWidgetSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { expanded = true }
                .padding(vertical = 4.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = widgets[selectedItemIndex].name,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Icon(
                painter = dropdownArrow,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            widgets.forEachIndexed { index, widget ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = widget.name,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = if (index == selectedItemIndex) FontWeight.Bold else FontWeight.Normal,
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    onClick = {
                        onWidgetSelected(index)
                        expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Content(
    widgets: List<WatchlistWidget>,
    gridSize: DpSize,
    rowState: LazyListState,
    hapticFeedback: HapticFeedback,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onQuoteClick: (Quote) -> Unit,
    quoteCard: @Composable (
        quote: Quote,
        modifier: Modifier,
        interactionSource: MutableInteractionSource,
        onClick: () -> Unit,
        onRemoveClick: (Quote) -> Unit,
    ) -> Unit,
    listFadingEdges: (ScrollableState) -> Modifier,
    registerWidgetScroll: @Composable (index: Int, scroll: suspend () -> Unit) -> Unit,
) {
    if (widgets.isEmpty()) {
        return
    }
    val width = gridSize.width
    LazyRow(
        modifier = Modifier.fillMaxSize(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.Start,
        state = rowState,
        flingBehavior = rememberSnapFlingBehavior(lazyListState = rowState),
    ) {
        items(widgets.size) { index ->
            val widget = widgets[index]
            val quotesList by widget.stocks.collectAsState()
            var quotes by remember(quotesList) { mutableStateOf(quotesList) }
            val lazyStaggeredGridState = rememberLazyStaggeredGridState()
            val reorderableLazyStaggeredGridState = rememberReorderableLazyStaggeredGridState(
                lazyStaggeredGridState
            ) { from, to ->
                quotes = quotes.toMutableList().apply {
                    add(to.index, removeAt(from.index))
                }
                hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
            }
            registerWidgetScroll(index) {
                lazyStaggeredGridState.animateScrollToItem(0)
            }
            PullToRefreshBox(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface),
                onRefresh = onRefresh,
                isRefreshing = isRefreshing
            ) {
                LazyVerticalStaggeredGrid(
                    modifier = Modifier
                        .width(width)
                        .fillMaxHeight()
                        .then(listFadingEdges(lazyStaggeredGridState)),
                    state = lazyStaggeredGridState,
                    columns = StaggeredGridCells.Adaptive(minSize = 150.dp),
                    contentPadding = PaddingValues(start = 8.dp, top = 8.dp, end = 8.dp, bottom = 8.dp + LocalContentBottomPadding.current),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalItemSpacing = 8.dp,
                ) {
                    itemsIndexed(
                        quotes,
                        key = { _, quote -> quote.symbol }
                    ) { _, quote ->
                        ReorderableItem(reorderableLazyStaggeredGridState, key = quote.symbol) {
                            val interactionSource = remember { MutableInteractionSource() }
                            quoteCard(
                                quote,
                                Modifier
                                    .fillMaxWidth()
                                    .longPressDraggableHandle(
                                        onDragStarted = {
                                            hapticFeedback.performHapticFeedback(
                                                HapticFeedbackType.GestureThresholdActivate
                                            )
                                        },
                                        onDragStopped = {
                                            val tickers = quotes.map { it.symbol }
                                            widget.rearrange(tickers)
                                            widget.setAutoSort(false)
                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureEnd)
                                        },
                                        interactionSource = interactionSource,
                                    ),
                                interactionSource,
                                { onQuoteClick(quote) },
                                { q -> widget.removeStock(q.symbol) },
                            )
                        }
                    }
                }
            }
        }
    }
}