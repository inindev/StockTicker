package com.github.premnirmal.ticker.widget

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.window.layout.DisplayFeature
import com.github.premnirmal.ticker.model.FetchState
import com.github.premnirmal.ticker.navigation.HomeRoute
import com.github.premnirmal.ticker.navigation.calculateContentAndNavigationType
import com.github.premnirmal.ticker.navigation.rememberScrollToTopAction
import com.github.premnirmal.ticker.ui.ContentType.SINGLE_PANE
import com.github.premnirmal.ticker.ui.fadingEdges
import com.github.premnirmal.tickerwidget.R
import com.github.premnirmal.tickerwidget.ui.Divider
import com.google.accompanist.adaptive.FoldAwareConfiguration
import com.google.accompanist.adaptive.HorizontalTwoPaneStrategy
import com.google.accompanist.adaptive.TwoPane
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.koin.androidx.compose.koinViewModel

/**
 * Android host for the shared [com.github.premnirmal.ticker.widget.WidgetsScreen]. Resolves the Koin
 * [WidgetsViewModel], collects the widget list / fetch state, adapts the Android Glance-backed
 * 'WidgetData' to the shared [WidgetSettings] abstraction, resolves the localised
 * strings/string-arrays and the 'ic_arrow_down'/'ic_done' icons, supplies the genuinely Android-only
 * Glance [WidgetPreview] as a slot and the adaptive Accompanist [TwoPane] layout as an optional slot,
 * the 'RuntimeShader'-based [fadingEdges], the [Divider] slot and the navigation
 * [rememberScrollToTopAction] registration, then delegates to the shared screen.
 */
@Composable
fun WidgetsScreen(
    modifier: Modifier = Modifier,
    widthSizeClass: WindowWidthSizeClass,
    displayFeatures: List<DisplayFeature>,
    selectedWidgetId: Int? = null,
    showSpinner: Boolean = true,
    topAppBarActions: @Composable RowScope.() -> Unit = {},
) {
    val viewModel = koinViewModel<WidgetsViewModel>()
    val scope = rememberCoroutineScope()
    val widgetDataList by viewModel.widgetDataList.collectAsState(emptyList())
    val fetchState by viewModel.fetchState.collectAsState()
    val watchlists by viewModel.watchlists.collectAsState(emptyList())
    val watchlistOptions = remember(watchlists) {
        watchlists.map { WatchlistOption(id = it.id, name = it.name) }
    }

    var widgetDataSelectedIndex by rememberSaveable { mutableIntStateOf(0) }
    val widgetData = remember(widgetDataSelectedIndex, selectedWidgetId, widgetDataList) {
        selectedWidgetId?.let { id ->
            widgetDataList.find { it.widgetId == id }
        } ?: widgetDataList.getOrNull(widgetDataSelectedIndex)
    }
    val settings = remember(widgetData, watchlistOptions, scope) {
        widgetData?.let { WidgetDataSettings(it, watchlistOptions, scope) }
    }
    val widgetNames = remember(widgetDataList) { widgetDataList.map { it.widgetName() } }

    val contentType = calculateContentAndNavigationType(
        widthSizeClass = widthSizeClass,
        displayFeatures = displayFeatures
    ).second

    WidgetsScreen(
        title = stringResource(id = R.string.stock_widget),
        widgetNames = widgetNames,
        selectedIndex = widgetDataSelectedIndex,
        onWidgetSelected = { index ->
            widgetDataSelectedIndex = index
            viewModel.refreshWidgets()
        },
        showSpinner = showSpinner,
        settings = settings,
        strings = WidgetSettingsStrings(
            watchlist = stringResource(id = R.string.watchlist),
            autoSort = stringResource(id = R.string.auto_sort),
            autoSortDesc = stringResource(id = R.string.auto_sort_desc),
            layoutType = stringResource(id = R.string.layout_type),
            layoutTypes = stringArrayResource(id = R.array.layout_types),
            chooseTextSize = stringResource(id = R.string.choose_text_size),
            fontSizes = stringArrayResource(id = R.array.font_sizes),
            widgetWidth = stringResource(id = R.string.widget_width),
            widgetWidthTypes = stringArrayResource(id = R.array.widget_width_types),
            background = stringResource(id = R.string.bg),
            backgrounds = stringArrayResource(id = R.array.backgrounds),
            textColor = stringResource(id = R.string.text_color),
            textColors = stringArrayResource(id = R.array.text_colors),
            boldChange = stringResource(id = R.string.bold_change),
            boldChangeDesc = stringResource(id = R.string.bold_change_desc),
            hideHeader = stringResource(id = R.string.hide_header),
            hideHeaderDesc = stringResource(id = R.string.hide_header_desc),
            showCurrency = stringResource(id = R.string.setting_currency),
            showCurrencyDesc = stringResource(id = R.string.setting_currency_desc),
            showRefresh = stringResource(id = R.string.show_refresh),
            showRefreshDesc = stringResource(id = R.string.show_refresh_desc),
        ),
        spinnerArrowIcon = painterResource(id = R.drawable.ic_arrow_down),
        widgetPreview = {
            widgetData?.let {
                WidgetPreview(
                    fetchState = fetchState,
                    widgetData = it,
                    state = it.toState(),
                    onRefreshClick = { viewModel.refresh() },
                )
            }
        },
        modifier = modifier,
        twoPane = if (contentType == SINGLE_PANE) {
            null
        } else {
            { first, second ->
                TwoPane(
                    strategy = HorizontalTwoPaneStrategy(
                        splitFraction = 1f / 2f,
                    ),
                    displayFeatures = displayFeatures,
                    foldAwareConfiguration = FoldAwareConfiguration.VerticalFoldsOnly,
                    first = first,
                    second = second,
                )
            }
        },
        divider = { Divider() },
        listFadingEdges = { state: ScrollableState -> Modifier.fadingEdges(state) },
        registerScrollToTop = { scrollToTop ->
            if (selectedWidgetId == null) {
                rememberScrollToTopAction(HomeRoute.Widgets, scrollToTop = scrollToTop)
            }
        },
        topAppBarActions = topAppBarActions,
    )
}

@Composable
private fun WidgetPreview(
    fetchState: FetchState,
    widgetData: WidgetData,
    state: WidgetData.Data,
    onRefreshClick: () -> Unit,
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
    ) {
        val quotes by widgetData.stocks.collectAsState()
        val data = remember(state) { SerializableWidgetState.from(state, fetchState, false) }
        GlanceWidgetPreview(
            modifier = Modifier.fillMaxWidth().height(220.dp).padding(16.dp),
            widgetData = data,
            quotes = quotes,
            onRefreshClick = onRefreshClick,
        )
    }
}

/**
 * Adapts the Android Glance/'SharedPreferences'-backed [WidgetData] to the shared [WidgetSettings]
 * contract rendered by the shared widgets screen.
 */
private class WidgetDataSettings(
    private val widgetData: WidgetData,
    override val watchlistOptions: List<WatchlistOption>,
    scope: CoroutineScope,
) : WidgetSettings {
    override val prefs: StateFlow<WidgetPrefs> = widgetData.prefsFlow
        .map { it.toWidgetPrefs() }
        .stateIn(scope, SharingStarted.Eagerly, widgetData.prefsFlow.value.toWidgetPrefs())

    override fun setWatchlistId(value: Long) = widgetData.setWatchlistId(value)
    override fun setAutoSort(value: Boolean) = widgetData.setAutoSort(value)
    override fun setLayoutPref(value: Int) = widgetData.setLayoutPref(value)

    @Deprecated("will be removed in future version")
    override fun setFontSize(value: Int) = widgetData.setFontSize(value)
    override fun setWidgetSizePref(value: Int) = widgetData.setWidgetSizePref(value)
    override fun setBgPref(value: Int) = widgetData.setBgPref(value)
    override fun setTextColorPref(value: Int) = widgetData.setTextColorPref(value)
    override fun setBoldEnabled(value: Boolean) = widgetData.setBoldEnabled(value)
    override fun setHideHeader(value: Boolean) = widgetData.setHideHeader(value)
    override fun setCurrencyEnabled(value: Boolean) = widgetData.setCurrencyEnabled(value)
    override fun setShowRefreshButton(value: Boolean) = widgetData.setShowRefreshButton(value)
}

@Suppress("DEPRECATION")
private fun WidgetData.Prefs.toWidgetPrefs() = WidgetPrefs(
    name = name,
    watchlistId = watchlistId,
    autoSort = autoSort,
    typePref = typePref,
    fontSizePref = fontSizePref,
    sizePref = sizePref,
    backgroundPref = backgroundPref,
    textColourPref = textColourPref,
    boldText = boldText,
    hideWidgetHeader = hideWidgetHeader,
    showCurrency = showCurrency,
    showRefreshButton = showRefreshButton,
)
