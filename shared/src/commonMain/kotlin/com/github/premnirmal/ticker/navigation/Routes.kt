package com.github.premnirmal.ticker.navigation

import io.ktor.http.encodeURLPathPart

/**
 * Top-level navigation graph route constants.
 */
object Graph {
    const val ROOT = "root_graph"
    const val HOME = "home_graph"
    const val QUOTE_DETAIL = "quote_detail_graph"

    /**
     * Route to the quote-detail screen for [symbol], percent-encoded so symbols containing URI
     * characters (e.g. the index '^GSPC' or futures 'CL=F') survive the navigation library's
     * path-argument decoding. Both the Android and iOS navigation hosts decode path args, so the
     * receiving side ('RootNavGraph') reads the symbol back verbatim without decoding.
     */
    fun quoteDetail(symbol: String): String = "$QUOTE_DETAIL/${symbol.encodeURLPathPart()}"
}

/**
 * Routes for the home bottom/rail navigation destinations.
 */
enum class HomeRoute(val route: String) {
    Watchlist("Watchlist"),
    Trending("Trending"),
    Search("Search"),
    Widgets("Widgets"),
    Settings("Settings")
}
