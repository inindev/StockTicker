package com.github.premnirmal.ticker.network.data

import kotlinx.serialization.Serializable

/**
 * One symbol's entry in the Yahoo Finance batch spark response
 * ('v8/finance/spark?symbols=A,B,...'). The response body is a flat 'Map<String, SparkResult>' keyed
 * by symbol; [timestamp] and [close] are parallel arrays (close entries can be null for missing
 * candles), verified against the live endpoint 2026-07-04.
 */
@Serializable
data class SparkResult(
    val symbol: String = "",
    val timestamp: List<Long>? = null,
    val close: List<Double?>? = null,
    val previousClose: Double? = null,
    val chartPreviousClose: Double? = null,
    val dataGranularity: Long? = null,
    val start: Long? = null,
    val end: Long? = null,
)