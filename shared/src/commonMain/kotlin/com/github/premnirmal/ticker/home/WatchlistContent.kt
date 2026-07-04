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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.Color
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
import com.github.premnirmal.shared.resources.Res
import com.github.premnirmal.shared.resources.done
import com.github.premnirmal.shared.resources.edit_watchlist
import com.github.premnirmal.shared.resources.ic_add
import com.github.premnirmal.shared.resources.ic_done
import com.github.premnirmal.shared.resources.ic_drag_handle
import com.github.premnirmal.shared.resources.ic_remove
import com.github.premnirmal.shared.resources.ic_settings_outline
import com.github.premnirmal.shared.resources.manage_watchlists
import com.github.premnirmal.shared.resources.new_watchlist
import com.github.premnirmal.ticker.navigation.LocalContentBottomPadding
import com.github.premnirmal.ticker.network.data.Quote
import com.github.premnirmal.tickerwidget.ui.theme.SharedColours
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs
import kotlin.math.min
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * Home watchlist screen, shared by Android and iOS. The screen is stateless: the state it renders
 * and the events it raises are hoisted as parameters so it has no Android, navigation or
 * dependency-injection dependencies.
 *
 * The top bar is a single compact row - a [WatchlistWidget] dropdown selector on the left and the
 * "Updated ..." fetch time ([updatedTime]) on the right - sitting directly above the swipeable pager of
 * per-widget grids. Selecting a widget from the dropdown scrolls the pager to that page, and swiping
 * the pager updates the selected entry, so the two stay in sync.
 *  - the watchlist pages come from [widgets] and the dropdown chevron from [dropdownArrow],
 *  - the refresh state/event is [isRefreshing]/[onRefresh] and the quote tap is [onQuoteClick],
 *  - the quote card is a composable [quoteCard] slot (it still pulls in the not-yet-shared theme),
 *  - the navigation scroll-to-top registrations are [registerResetScroll]/[registerWidgetScroll].
 *
 * The Android 'WatchlistContent' host in ':app' supplies them.
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
    quoteCard: @Composable (QuoteRowSlot) -> Unit,
    totalHoldingsPopup: @Composable (totalHoldings: TotalGainLoss, onDismiss: () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
    onManageWatchlists: (() -> Unit)? = null,
    onNewWatchlist: (() -> Unit)? = null,
    listFadingEdges: (ScrollableState) -> Modifier = { Modifier },
    registerResetScroll: @Composable (reset: suspend () -> Unit) -> Unit = {},
    registerWidgetScroll: @Composable (index: Int, scroll: suspend () -> Unit) -> Unit = { _, _ -> },
    prefetchSparks: suspend (symbols: List<String>) -> Unit = {},
) {
    val density = LocalDensity.current
    var showTotalHoldingsPopup by remember { mutableStateOf(false) }
    var isEditing by remember { mutableStateOf(false) }
    var changeDisplayMode by remember { mutableStateOf(ChangeDisplayMode.CHANGE_AMOUNT) }
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
                isEditing = isEditing,
                onDoneEditing = { isEditing = false },
                onEditList = { isEditing = true },
                onWidgetSelected = { index ->
                    coroutineScope.launch { rowState.animateScrollToItem(index) }
                },
                onManageWatchlists = onManageWatchlists,
                onNewWatchlist = onNewWatchlist,
            )
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                Content(
                    widgets = widgets,
                    gridSize = gridSize,
                    rowState = rowState,
                    hapticFeedback = hapticFeedback,
                    isRefreshing = isRefreshing,
                    isEditing = isEditing,
                    onEnterEdit = { isEditing = true },
                    displayMode = changeDisplayMode,
                    onCyclePill = {
                        // Without any positions, holdings-gain renders as change % on every row,
                        // making two consecutive modes look identical - skip it.
                        var next = changeDisplayMode.next()
                        if (next == ChangeDisplayMode.HOLDINGS_GAIN && !hasHoldings) {
                            next = next.next()
                        }
                        changeDisplayMode = next
                    },
                    onRefresh = onRefresh,
                    onQuoteClick = onQuoteClick,
                    quoteCard = quoteCard,
                    listFadingEdges = listFadingEdges,
                    registerWidgetScroll = registerWidgetScroll,
                    prefetchSparks = prefetchSparks,
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
 * "Updated ..." fetch time on the right, on a single row that clears the status bar.
 */
@Composable
private fun WatchlistTopBar(
    widgets: List<WatchlistWidget>,
    selectedItemIndex: Int,
    updatedTime: String,
    dropdownArrow: Painter,
    isEditing: Boolean,
    onDoneEditing: () -> Unit,
    onEditList: () -> Unit,
    onWidgetSelected: (Int) -> Unit,
    onManageWatchlists: (() -> Unit)?,
    onNewWatchlist: (() -> Unit)?,
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
                    onEditList = onEditList,
                    onManageWatchlists = onManageWatchlists,
                    onNewWatchlist = onNewWatchlist,
                )
            }
        }
        // While editing, the checkmark "done" button (same idiom as the manage-watchlists and
        // add-symbol dialogs) takes the updated-time's slot.
        if (isEditing) {
            FilledIconButton(
                modifier = Modifier.size(32.dp),
                onClick = onDoneEditing,
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_done),
                    contentDescription = stringResource(Res.string.done),
                )
            }
        } else if (updatedTime.isNotEmpty()) {
            Text(
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
    onEditList: () -> Unit,
    onManageWatchlists: (() -> Unit)?,
    onNewWatchlist: (() -> Unit)?,
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
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    // Selection is shown with a leading checkmark (macOS style); unselected rows get
                    // an equal-width spacer so all names align in the same gutter.
                    leadingIcon = {
                        if (index == selectedItemIndex) {
                            Icon(
                                painter = painterResource(Res.drawable.ic_done),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        } else {
                            Spacer(Modifier.size(24.dp))
                        }
                    },
                    onClick = {
                        onWidgetSelected(index)
                        expanded = false
                    },
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(text = stringResource(Res.string.edit_watchlist)) },
                leadingIcon = {
                    Icon(painter = painterResource(Res.drawable.ic_drag_handle), contentDescription = null)
                },
                onClick = {
                    expanded = false
                    onEditList()
                },
            )
            onManageWatchlists?.let { manage ->
                DropdownMenuItem(
                    text = { Text(text = stringResource(Res.string.manage_watchlists)) },
                    leadingIcon = {
                        Icon(painter = painterResource(Res.drawable.ic_settings_outline), contentDescription = null)
                    },
                    onClick = {
                        expanded = false
                        manage()
                    },
                )
            }
            onNewWatchlist?.let { new ->
                DropdownMenuItem(
                    text = { Text(text = stringResource(Res.string.new_watchlist)) },
                    leadingIcon = {
                        Icon(painter = painterResource(Res.drawable.ic_add), contentDescription = null)
                    },
                    onClick = {
                        expanded = false
                        new()
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
    isEditing: Boolean,
    onEnterEdit: () -> Unit,
    displayMode: ChangeDisplayMode,
    onCyclePill: () -> Unit,
    onRefresh: () -> Unit,
    onQuoteClick: (Quote) -> Unit,
    quoteCard: @Composable (QuoteRowSlot) -> Unit,
    listFadingEdges: (ScrollableState) -> Modifier,
    registerWidgetScroll: @Composable (index: Int, scroll: suspend () -> Unit) -> Unit,
    prefetchSparks: suspend (symbols: List<String>) -> Unit,
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
        // Lock watchlist page swiping while editing so horizontal gestures belong to
        // swipe-to-delete.
        userScrollEnabled = !isEditing,
    ) {
        items(widgets.size) { index ->
            val widget = widgets[index]
            val quotesList by widget.stocks.collectAsState()
            var quotes by remember { mutableStateOf(quotesList) }
            // While a drag is in progress the local order buffer is frozen: an incoming price
            // update must not reset it mid-gesture (that would discard or scramble the reorder).
            // The buffer resyncs from upstream on the next emission once the drag ends - keyed on
            // quotesList only, so ending a drag never snaps back to a stale pre-persist order.
            var isReordering by remember { mutableStateOf(false) }
            LaunchedEffect(quotesList) {
                if (!isReordering) {
                    quotes = quotesList
                }
            }
            // One batch spark request per page of quotes (the provider TTL-guards re-fetches).
            LaunchedEffect(quotesList) {
                prefetchSparks(quotesList.map { it.symbol })
            }
            val lazyListState = rememberLazyListState()
            val reorderableLazyListState = rememberReorderableLazyListState(
                lazyListState
            ) { from, to ->
                quotes = quotes.toMutableList().apply {
                    add(to.index, removeAt(from.index))
                }
                hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
            }
            registerWidgetScroll(index) {
                lazyListState.animateScrollToItem(0)
            }
            PullToRefreshBox(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface),
                onRefresh = onRefresh,
                isRefreshing = isRefreshing
            ) {
                LazyColumn(
                    modifier = Modifier
                        .width(width)
                        .fillMaxHeight()
                        .then(listFadingEdges(lazyListState)),
                    state = lazyListState,
                    contentPadding = PaddingValues(bottom = LocalContentBottomPadding.current),
                ) {
                    itemsIndexed(
                        quotes,
                        key = { _, quote -> quote.symbol }
                    ) { _, quote ->
                        ReorderableItem(reorderableLazyListState, key = quote.symbol) {
                            val interactionSource = remember { MutableInteractionSource() }
                            // Dragging is only possible via the row's edit-mode handle; the row
                            // body long-press enters edit mode instead.
                            val dragHandleModifier = Modifier.draggableHandle(
                                onDragStarted = {
                                    // Freeze the buffer for the duration of the gesture.
                                    isReordering = true
                                    hapticFeedback.performHapticFeedback(
                                        HapticFeedbackType.GestureThresholdActivate
                                    )
                                },
                                onDragStopped = {
                                    val tickers = quotes.map { it.symbol }
                                    widget.rearrange(tickers)
                                    widget.setAutoSort(false)
                                    // Unfreeze; the buffer keeps the just-built order until the
                                    // persisted reorder round-trips back through quotesList.
                                    isReordering = false
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureEnd)
                                },
                                interactionSource = interactionSource,
                            )
                            val row: @Composable () -> Unit = {
                                quoteCard(
                                    QuoteRowSlot(
                                        quote = quote,
                                        // Opaque while editing: the row sits above the red
                                        // swipe-to-delete layer, which must only be exposed by the
                                        // swipe itself.
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(MaterialTheme.colorScheme.surface),
                                        dragHandleModifier = dragHandleModifier,
                                        interactionSource = interactionSource,
                                        isEditing = isEditing,
                                        displayMode = displayMode,
                                        onPillClick = onCyclePill,
                                        onClick = { onQuoteClick(quote) },
                                        onLongClick = {
                                            if (!isEditing) {
                                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                                onEnterEdit()
                                            }
                                        },
                                        onRemove = { q -> widget.removeStock(q.symbol) },
                                    )
                                )
                            }
                            if (isEditing) {
                                val dismissState = rememberSwipeToDismissBoxState(
                                    confirmValueChange = { value ->
                                        if (value == SwipeToDismissBoxValue.EndToStart) {
                                            widget.removeStock(quote.symbol)
                                            true
                                        } else {
                                            false
                                        }
                                    }
                                )
                                SwipeToDismissBox(
                                    state = dismissState,
                                    enableDismissFromStartToEnd = false,
                                    backgroundContent = {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(SharedColours.PillNegative),
                                            contentAlignment = Alignment.CenterEnd,
                                        ) {
                                            Icon(
                                                painter = painterResource(Res.drawable.ic_remove),
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.padding(end = 20.dp),
                                            )
                                        }
                                    },
                                ) {
                                    row()
                                }
                            } else {
                                row()
                            }
                        }
                    }
                }
            }
        }
    }
}