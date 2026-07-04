package com.github.premnirmal.ticker.model

import com.github.premnirmal.ticker.components.AppLogger
import com.github.premnirmal.ticker.components.elapsedRealtimeMillis
import com.github.premnirmal.ticker.network.ChartApi
import com.github.premnirmal.ticker.network.data.DataPoint
import com.github.premnirmal.ticker.network.data.SparkResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Provides the 1-day mini-chart ("sparkline") data for the watchlist rows (see reference/plan.md).
 *
 * Symbols are fetched in batches through Yahoo's spark endpoint ([ChartApi.fetchSparkData]) -- one
 * request covers a whole watchlist -- and cached in memory per symbol with a [CacheTtlMillis] TTL so
 * scrolling is free. The watchlist calls [prefetch] with a page's symbols whenever they change;
 * concurrent calls for the same symbols are de-duplicated. Failed fetches leave the cache untouched
 * (the row keeps showing nothing) and are retried on the next call.
 */
class SparkProvider(
    private val chartApi: ChartApi
) {

    private val mutex = Mutex()
    private val cache = mutableMapOf<String, MutableStateFlow<ChartData?>>()
    private val fetchedAtMillis = mutableMapOf<String, Long>()
    private val inFlight = mutableSetOf<String>()

    /** The spark data for [symbol]; emits null until a fetch succeeds. Main-thread only. */
    fun spark(symbol: String): StateFlow<ChartData?> = flowFor(symbol).asStateFlow()

    /** Batch-fetches 1-day charts for the stale subset of [symbols], [BatchSize] per request. */
    suspend fun prefetch(symbols: List<String>) {
        val now = elapsedRealtimeMillis()
        val toFetch = mutex.withLock {
            symbols.distinct().filter { symbol ->
                val last = fetchedAtMillis[symbol]
                val fresh = last != null && now - last < CacheTtlMillis && flowFor(symbol).value != null
                !fresh && symbol !in inFlight
            }.also { inFlight += it }
        }
        if (toFetch.isEmpty()) return
        try {
            toFetch.chunked(BatchSize).forEach { chunk ->
                val results = try {
                    chartApi.fetchSparkData(symbols = chunk, interval = "5m", range = "1d")
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    AppLogger.w(e, "spark fetch failed for ${chunk.joinToString(",")}")
                    emptyMap()
                }
                if (results.isEmpty()) return@forEach
                val fetchedAt = elapsedRealtimeMillis()
                for ((symbol, result) in results) {
                    val chartData = result.toChartData() ?: continue
                    flowFor(symbol).value = chartData
                    mutex.withLock { fetchedAtMillis[symbol] = fetchedAt }
                }
            }
        } finally {
            mutex.withLock { inFlight -= toFetch.toSet() }
        }
    }

    private fun flowFor(symbol: String): MutableStateFlow<ChartData?> =
        cache.getOrPut(symbol) { MutableStateFlow(null) }

    companion object {
        private const val CacheTtlMillis = 5 * 60 * 1000L
        private const val BatchSize = 20
    }
}

/**
 * Maps a spark entry to the row's [ChartData]: the parallel timestamp/close arrays become
 * close-only [DataPoint]s (the sparkline only draws closes). Null when the entry has no usable
 * line (fewer than two candles or no previous close).
 */
private fun SparkResult.toChartData(): ChartData? {
    val timestamps = timestamp ?: return null
    val closes = close ?: return null
    val points = timestamps.zip(closes).mapNotNull { (stamp, closeValue) ->
        closeValue?.toFloat()?.let { DataPoint(stamp.toFloat(), it, it, it, it) }
    }
    if (points.size < 2) return null
    val prevClose = (chartPreviousClose ?: previousClose)?.toFloat() ?: return null
    return ChartData(
        chartPreviousClose = prevClose,
        regularMarketPrice = points.last().closeVal,
        dataPoints = points,
    )
}