package com.github.premnirmal.ticker.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import timber.log.Timber

class WidgetDataProvider constructor(
    private val context: Context,
) {

    private val glanceAppWidgetManager: GlanceAppWidgetManager by lazy {
        GlanceAppWidgetManager(context)
    }

    private val widgets: MutableMap<Int, WidgetData> by lazy {
        HashMap()
    }

    val widgetData: StateFlow<List<WidgetData>>
        get() = _widgetData

    private val _widgetData = MutableStateFlow<List<WidgetData>>(emptyList())

    fun getAppWidgetIds(): IntArray {
        return runBlocking {
            glanceAppWidgetManager.getGlanceIds(GlanceStocksWidget::class.java).map {
                glanceAppWidgetManager.getAppWidgetId(it)
            }.toIntArray()
        }
    }

    suspend fun refreshWidgetDataList(): List<WidgetData> {
        val widgetDataList = allIds().distinct().map { dataForWidgetId(it) }
        // Each widget renders its associated watchlist, so refresh its symbols from the store before
        // publishing.
        widgetDataList.forEach { it.refreshFromWatchlist() }
        val sorted = widgetDataList.sortedBy { it.widgetName() }
        _widgetData.emit(sorted)
        return sorted
    }

    private fun allIds(): List<Int> {
        val appWidgetIds = getAppWidgetIds().toMutableSet()
        if (appWidgetIds.isEmpty()) {
            appWidgetIds.add(AppWidgetManager.INVALID_APPWIDGET_ID)
        }
        return appWidgetIds.toList()
    }

    /** Returns the widget's data with its symbols freshly loaded from its watchlist. */
    suspend fun refreshWidgetData(widgetId: Int): WidgetData {
        val widgetData = dataForWidgetId(widgetId)
        widgetData.refreshFromWatchlist()
        return widgetData
    }

    fun dataForWidgetId(widgetId: Int): WidgetData {
        // Symbols are loaded from the associated watchlist via [WidgetData.refreshFromWatchlist],
        // not seeded from the stocks provider.
        val widgetData = synchronized(widgets) {
            widgets.getOrPut(widgetId) {
                WidgetData(widgetId)
            }
        }
        widgetData.refreshStocksList()
        return widgetData
    }

    suspend fun broadcastUpdateWidget(widgetId: Int) {
        refreshWidgetDataList()
        // Only real home-screen widgets (positive app-widget ids) have a Glance widget to update; the
        // default in-app list (0) has none.
        if (widgetId > 0) {
            try {
                dataForWidgetId(widgetId).emitWidgetChanges()
                GlanceStocksWidget().update(context, glanceAppWidgetManager.getGlanceIdBy(widgetId))
            } catch (e: Exception) {
                Timber.e(e)
            }
        }
    }

    suspend fun broadcastUpdateAllWidgets() {
        refreshWidgetDataList()
        _widgetData.value.forEach {
            it.emitWidgetChanges()
        }
        GlanceStocksWidget().updateAll(context)
    }

    fun hasWidget(): Boolean = getAppWidgetIds().isNotEmpty()

    val hasWidget: Flow<Boolean> by lazy {
        widgetData.map {
            hasWidget()
        }
    }
}
