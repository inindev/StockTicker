package com.github.premnirmal.ticker.widget

import android.content.Context
import android.content.SharedPreferences
import android.os.Parcelable
import android.util.TypedValue
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.Keep
import androidx.core.content.edit
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import com.github.premnirmal.ticker.AppPreferences
import com.github.premnirmal.ticker.NightMode
import com.github.premnirmal.ticker.model.StocksProvider
import com.github.premnirmal.ticker.network.data.Quote
import com.github.premnirmal.ticker.repo.WatchlistRepository
import com.github.premnirmal.ticker.ui.AppMessaging
import com.github.premnirmal.ticker.widget.IWidgetData.LayoutType
import com.github.premnirmal.tickerwidget.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class WidgetData : IWidgetData, KoinComponent {

    companion object {
        private const val PREFS_NAME_PREFIX = "stocks_widget_"
        // The watchlist this widget displays. 0 = unset -> resolves to All Symbols.
        private const val WATCHLIST_ID = "WATCHLIST_ID"
        const val NO_WATCHLIST = 0L
        private const val LAYOUT_TYPE = AppPreferences.LAYOUT_TYPE
        private const val WIDGET_SIZE = AppPreferences.WIDGET_SIZE

        @Deprecated("will be removed in future version")
        private const val FONT_SIZE = AppPreferences.FONT_SIZE
        private const val BOLD_CHANGE = AppPreferences.BOLD_CHANGE
        private const val SHOW_CURRENCY = AppPreferences.SHOW_CURRENCY
        private const val SHOW_REFRESH = AppPreferences.SHOW_REFRESH
        private const val PERCENT = AppPreferences.PERCENT
        private const val AUTOSORT = AppPreferences.SETTING_AUTOSORT
        private const val HIDE_HEADER = AppPreferences.SETTING_HIDE_HEADER
        private const val WIDGET_BG = AppPreferences.WIDGET_BG
        private const val TEXT_COLOR = AppPreferences.TEXT_COLOR
        private const val TRANSPARENT = AppPreferences.TRANSPARENT
        private const val TRANSLUCENT = AppPreferences.TRANSLUCENT
        private const val SYSTEM = AppPreferences.SYSTEM
        private const val DARK = AppPreferences.DARK
        private const val LIGHT = AppPreferences.LIGHT
    }

    private val stocksProvider: StocksProvider by inject()

    private val watchlistRepository: WatchlistRepository by inject()

    private val context: Context by inject()

    private val widgetDataProvider: WidgetDataProvider by inject()

    private val appPreferences: AppPreferences by inject()

    private val coroutineScope: CoroutineScope by inject()

    private val appMessaging: AppMessaging by inject()

    override val widgetId: Int
    // In-memory cache of the associated watchlist's symbols, (re)loaded by [refreshFromWatchlist].
    // Never persisted - the watchlist store is the source of truth.
    private val tickerList: MutableList<String> = ArrayList()
    // Cached name of the associated watchlist, used to label the widget in the settings selector.
    private var associatedWatchlistName: String = "Watchlist"
    val tickers: StateFlow<List<String>>
        get() = _tickerList
    private val _tickerList = MutableStateFlow<List<String>>(emptyList())
    private val _stocks = MutableStateFlow<List<Quote>>(emptyList())
    override val stocks: StateFlow<List<Quote>> = _stocks.asStateFlow()
    private val preferences: SharedPreferences
    private val _autoSortEnabled = MutableStateFlow(false)
    val prefsFlow: StateFlow<Prefs>
        get() = _prefsFlow
    private val _prefsFlow by lazy { MutableStateFlow(toPrefs()) }

    override val data: StateFlow<Data>
        get() = _data
    private val _data by lazy { MutableStateFlow(toState()) }

    constructor(
        widgetId: Int
    ) {
        this.widgetId = widgetId
        val prefsName = "$PREFS_NAME_PREFIX$widgetId"
        preferences = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        save()
        _autoSortEnabled.value = autoSortEnabled()
    }

    private val nightMode: Boolean
        get() = appPreferences.nightMode == NightMode.DARK

    @get:ColorRes
    val positiveTextColor: Int
        get() {
            return when (textColorPref()) {
                SYSTEM -> {
                    if (nightMode) {
                        R.color.text_widget_positive_light
                    } else {
                        R.color.text_widget_positive
                    }
                }

                DARK -> {
                    R.color.text_widget_positive_dark
                }

                LIGHT -> {
                    R.color.text_widget_positive_light
                }

                else -> {
                    R.color.text_widget_positive
                }
            }
        }

    @get:ColorRes
    val negativeTextColor: Int
        get() {
            return R.color.text_widget_negative
        }

    // A widget is a view onto a watchlist, so its label is the associated watchlist's name (cached
    // by [refreshFromWatchlist]) rather than a separately-stored widget name.
    override val widgetName: String
        get() = associatedWatchlistName

    fun widgetName(): String = widgetName

    /** The id of the watchlist this widget-view displays; [NO_WATCHLIST] until configured. */
    val watchlistId: Long
        get() = preferences.getLong(WATCHLIST_ID, NO_WATCHLIST)

    /** Points this widget-view at watchlist [id] and re-renders it with that list's symbols. */
    fun setWatchlistId(id: Long) {
        preferences.edit {
            putLong(WATCHLIST_ID, id)
        }
        _prefsFlow.value = toPrefs()
        _data.value = toState()
        coroutineScope.launch {
            refreshFromWatchlist()
            emitWidgetChanges()
            widgetDataProvider.refreshWidgetDataList()
        }
    }

    /**
     * Loads this widget's ticker list from its associated watchlist. The widget is a *view* onto a
     * watchlist - it does not own its symbols. An unset [watchlistId] resolves to the All Symbols
     * master, so a freshly-added widget shows everything until the user picks a list.
     */
    suspend fun refreshFromWatchlist() {
        // Resolve the associated watchlist; fall back to All Symbols when unset or when the watchlist
        // was deleted (so a widget pointing at a removed list re-points to the master rather than
        // showing nothing).
        val watchlist = watchlistId.takeIf { it != NO_WATCHLIST }
            ?.let { watchlistRepository.getWatchlist(it) }
            ?: watchlistRepository.getOrCreateAllSymbols()
        associatedWatchlistName = watchlist.name
        synchronized(tickerList) {
            tickerList.clear()
            tickerList.addAll(watchlist.symbols)
        }
        _prefsFlow.value = toPrefs()
        _data.value = toState()
        refreshStocksList()
    }

    override val changeType: IWidgetData.ChangeType
        get() = changeType()

    fun changeType(): IWidgetData.ChangeType {
        val state = preferences.getBoolean(PERCENT, false)
        return if (state) IWidgetData.ChangeType.Percent else IWidgetData.ChangeType.Value
    }

    fun setChange(percent: Boolean) {
        preferences.edit {
            putBoolean(PERCENT, percent)
        }
        _prefsFlow.value = toPrefs()
        _data.value = toState()
        emitWidgetChanges()
    }

    fun widgetSizePref(): Int = preferences.getInt(WIDGET_SIZE, 0)

    fun setWidgetSizePref(value: Int) {
        preferences.edit {
            putInt(WIDGET_SIZE, value)
        }
        _prefsFlow.value = toPrefs()
        _data.value = toState()
        emitWidgetChanges()
    }

    @Deprecated("will be removed in future version")
    fun fontSizePref(): Int = preferences.getInt(FONT_SIZE, 3)

    @Deprecated("will be removed in future version")
    fun readFontSize(): Float {
        val size = fontSizePref()
        val resId = when (size) {
            0 -> R.dimen.text_size_nano
            1 -> R.dimen.text_size_mini
            2 -> R.dimen.text_size_small
            3 -> R.dimen.text_size_medium
            4 -> R.dimen.text_size_large
            5 -> R.dimen.text_size_huge
            6 -> R.dimen.text_size_giant
            else -> R.dimen.text_size_medium
        }
        val typedValue = TypedValue()
        context.resources.getValue(resId, typedValue, true)
        return typedValue.float
    }

    @Deprecated("will be removed in future version")
    fun setFontSize(value: Int) {
        appMessaging.sendSnackbar("Font size is a deprecated setting, FYI this will be removed in a future version!")
        val validValue = value.coerceIn(0, 6)
        preferences.edit {
            putInt(FONT_SIZE, validValue)
        }
        _prefsFlow.value = toPrefs()
        _data.value = toState()
        emitWidgetChanges()
    }

    fun layoutPref(): Int = preferences.getInt(LAYOUT_TYPE, 0)

    fun setLayoutPref(value: Int) {
        preferences.edit {
            putInt(LAYOUT_TYPE, value)
        }
        _prefsFlow.value = toPrefs()
        _data.value = toState()
        emitWidgetChanges()
    }

    @ColorRes fun textColorRes(): Int {
        val pref = textColorPref()
        return if (pref == SYSTEM) {
            if (nightMode) {
                R.color.dark_widget_text
            } else {
                R.color.widget_text
            }
        } else if (pref == DARK) {
            R.color.widget_text_black
        } else if (pref == LIGHT) {
            R.color.widget_text_white
        } else {
            R.color.widget_text
        }
    }

    override val layoutType: LayoutType
        get() = LayoutType.fromInt(layoutPref())

    @DrawableRes
    fun backgroundResource(): Int {
        return when {
            bgPref() == TRANSPARENT -> {
                R.drawable.transparent_widget_bg
            }
            bgPref() == TRANSLUCENT -> {
                if (nightMode) {
                    R.drawable.translucent_widget_bg_dark
                } else {
                    R.drawable.translucent_widget_bg
                }
            }
            nightMode -> {
                R.drawable.app_widget_background_dark
            }
            else -> {
                R.drawable.app_widget_background
            }
        }
    }

    fun textColorPref(): Int = preferences.getInt(TEXT_COLOR, SYSTEM)

    fun setTextColorPref(pref: Int) {
        preferences.edit {
            putInt(TEXT_COLOR, pref)
        }
        _prefsFlow.value = toPrefs()
        _data.value = toState()
        emitWidgetChanges()
    }

    fun bgPref(): Int {
        var pref = preferences.getInt(WIDGET_BG, SYSTEM)
        if (pref > TRANSLUCENT) {
            pref = SYSTEM
            setBgPref(pref)
        }
        return pref
    }

    fun bgType(): IWidgetData.BackgroundType {
        val pref = bgPref()
        return when (pref) {
            TRANSPARENT -> IWidgetData.BackgroundType.Transparent
            TRANSLUCENT -> IWidgetData.BackgroundType.Translucent
            SYSTEM -> IWidgetData.BackgroundType.System
            else -> IWidgetData.BackgroundType.System
        }
    }

    fun textColorType(): IWidgetData.TextColorType {
        val pref = textColorPref()
        return when (pref) {
            SYSTEM -> IWidgetData.TextColorType.System
            LIGHT -> IWidgetData.TextColorType.Light
            DARK -> IWidgetData.TextColorType.Dark
            else -> IWidgetData.TextColorType.System
        }
    }

    fun setBgPref(value: Int) {
        preferences.edit {
            putInt(WIDGET_BG, value)
        }
        _prefsFlow.value = toPrefs()
        _data.value = toState()
        emitWidgetChanges()
    }

    fun autoSortEnabled(): Boolean = preferences.getBoolean(AUTOSORT, false)

    fun setAutoSort(autoSort: Boolean) {
        preferences.edit {
            putBoolean(AUTOSORT, autoSort)
        }
        _autoSortEnabled.value = autoSort
        save()
    }

    fun readHideHeader(): Boolean = preferences.getBoolean(HIDE_HEADER, false)

    fun setHideHeader(hide: Boolean) {
        preferences.edit {
            putBoolean(HIDE_HEADER, hide)
        }
        _prefsFlow.value = toPrefs()
        _data.value = toState()
        emitWidgetChanges()
    }

    fun readIsBoldEnabled(): Boolean = preferences.getBoolean(BOLD_CHANGE, false)

    fun setBoldEnabled(value: Boolean) {
        preferences.edit {
            putBoolean(BOLD_CHANGE, value)
        }
        _prefsFlow.value = toPrefs()
        _data.value = toState()
        emitWidgetChanges()
    }

    fun readIsCurrencyEnabled(): Boolean = preferences.getBoolean(SHOW_CURRENCY, false)

    fun setCurrencyEnabled(value: Boolean) {
        preferences.edit {
            putBoolean(SHOW_CURRENCY, value)
        }
        _prefsFlow.value = toPrefs()
        _data.value = toState()
        emitWidgetChanges()
    }

    fun showRefreshButton(): Boolean = preferences.getBoolean(SHOW_REFRESH, false)

    fun setShowRefreshButton(show: Boolean) {
        preferences.edit {
            putBoolean(SHOW_REFRESH, show)
        }
        _prefsFlow.value = toPrefs()
        _data.value = toState()
        emitWidgetChanges()
    }

    fun toPrefs(): Prefs {
        return Prefs(
            id = widgetId,
            name = widgetName(),
            watchlistId = watchlistId,
            autoSort = autoSortEnabled(),
            boldText = readIsBoldEnabled(),
            hideWidgetHeader = readHideHeader(),
            showCurrency = readIsCurrencyEnabled(),
            isDarkMode = nightMode,
            typePref = layoutPref(),
            sizePref = widgetSizePref(),
            backgroundPref = bgPref(),
            textColourPref = textColorPref(),
            changeType = changeType(),
            layoutType = layoutType,
            fontSizePref = fontSizePref(),
            fontSize = readFontSize(),
            backgroundResource = backgroundResource(),
            positiveTextColor = positiveTextColor,
            negativeTextColor = negativeTextColor,
            textColor = textColorRes(),
            showRefreshButton = showRefreshButton(),
        )
    }

    fun toState(): Data {
        return Data(
            id = widgetId,
            name = widgetName,
            boldText = readIsBoldEnabled(),
            changeType = changeType(),
            layoutType = layoutType,
            fontSizePref = fontSizePref(),
            fontSize = readFontSize(),
            showCurrency = readIsCurrencyEnabled(),
            isDarkMode = nightMode,
            sizePref = widgetSizePref(),
            hideWidgetHeader = readHideHeader(),
            backgroundResource = backgroundResource(),
            bgPref = bgType(),
            textColorPref = textColorType(),
            positiveTextColor = positiveTextColor,
            negativeTextColor = negativeTextColor,
            textColor = textColorRes(),
            showRefreshButton = showRefreshButton(),
        )
    }

    private fun save() {
        _prefsFlow.value = toPrefs()
        _data.value = toState()
        _tickerList.value = tickerList
        _stocks.value = tickerList.mapNotNull {
            stocksProvider.getStock(it)
        }.let { quotes ->
            if (autoSortEnabled()) {
                quotes.toMutableList().sortedByDescending { it.changeInPercent }
            } else {
                quotes
            }
        }
        emitWidgetChanges()
    }

    /**
     * Builds the persisted Glance render state from the live sources of truth: this widget's prefs
     * and quotes plus the provider's fetch state. Every Glance-state write goes through here so a
     * stale or interrupted writer can never leave the widget rendering state that disagrees with
     * the app - in particular the refresh spinner is *derived* from [StocksProvider.isFetching]
     * rather than toggled on/off by whoever triggered the refresh.
     */
    fun glanceStateSnapshot(current: WidgetGlanceState): WidgetGlanceState {
        return current.copy(
            widgetState = SerializableWidgetState.from(
                state = data.value,
                fetchState = stocksProvider.fetchState.value,
                isRefreshing = stocksProvider.isFetching.value,
            ),
            quotes = stocks.value,
        )
    }

    fun emitWidgetChanges() {
        // Only real home-screen widgets have a Glance id (always a positive app-widget id). The
        // default in-app list (INVALID_APPWIDGET_ID == 0) has no home-screen widget, so pushing a
        // Glance update would throw "Invalid AppWidget ID"; it still updates its in-app state above,
        // only the Glance push is skipped.
        if (widgetId > 0) {
            coroutineScope.launch {
                val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(widgetId)
                updateAppWidgetState(
                    context = context,
                    definition = WidgetGlanceStateDefinition,
                    glanceId = glanceId,
                ) { state -> glanceStateSnapshot(state) }
                GlanceStocksWidget().update(context, glanceId)
            }
        }
    }

    fun refreshStocksList() {
        _tickerList.value = tickerList
        val quotes = tickerList.map {
            stocksProvider.getStock(it) ?: Quote(symbol = it)
        }.let { quotes ->
            return@let if (autoSortEnabled()) {
                quotes.toMutableList().sortedByDescending { it.changeInPercent }
            } else {
                quotes
            }
        }
        _stocks.update {
            quotes
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is WidgetData) return false

        if (toPrefs() != other.toPrefs()) return false
        if (toState() != other.toState()) return false
        if (tickerList != other.tickerList) return false
        if (_stocks.value != other._stocks.value) return false
        return true
    }

    override fun hashCode(): Int {
        return toPrefs().hashCode() * 31 + toState().hashCode() * 31 + tickerList.hashCode() * 31 + _stocks.value.hashCode()
    }

    @Parcelize
    @Serializable
    @Keep
    data class Data(
        val id: Int,
        val name: String,
        val boldText: Boolean,
        val changeType: IWidgetData.ChangeType,
        val layoutType: LayoutType,
        @Deprecated("will be removed in future version")
        val fontSizePref: Int,
        @Deprecated("will be removed in future version")
        val fontSize: Float,
        val showCurrency: Boolean,
        val isDarkMode: Boolean,
        val sizePref: Int,
        val hideWidgetHeader: Boolean,
        @param:DrawableRes
        @get:DrawableRes
        val backgroundResource: Int,
        val bgPref: IWidgetData.BackgroundType,
        val textColorPref: IWidgetData.TextColorType,
        @param:ColorRes
        @get:ColorRes
        val positiveTextColor: Int,
        @param:ColorRes
        @get:ColorRes
        val negativeTextColor: Int,
        @param:ColorRes
        @get:ColorRes
        val textColor: Int,
        val showRefreshButton: Boolean,
    ) : Parcelable

    @Parcelize
    data class Prefs(
        val id: Int,
        val name: String,
        val watchlistId: Long,
        val autoSort: Boolean,
        val boldText: Boolean,
        val hideWidgetHeader: Boolean,
        val showCurrency: Boolean,
        val isDarkMode: Boolean,
        val typePref: Int,
        val sizePref: Int,
        val backgroundPref: Int,
        val textColourPref: Int,
        val changeType: IWidgetData.ChangeType,
        val layoutType: LayoutType,
        @Deprecated("will be removed in future version")
        val fontSizePref: Int,
        @Deprecated("will be removed in future version")
        val fontSize: Float,
        @param:DrawableRes
        @get:DrawableRes
        val backgroundResource: Int,
        @param:ColorRes
        @get:ColorRes
        val positiveTextColor: Int,
        @param:ColorRes
        @get:ColorRes
        val negativeTextColor: Int,
        @param:ColorRes
        @get:ColorRes
        val textColor: Int,
        val showRefreshButton: Boolean,
    ) : Parcelable
}
