package com.github.premnirmal.ticker.repo.data

/**
 * Domain view of a first-class watchlist: its identity plus its ordered symbols. Assembled by
 * [com.github.premnirmal.ticker.repo.WatchlistRepository] from [WatchlistRow] + its
 * [WatchlistMembershipRow]s so callers never touch the Room rows directly.
 */
data class Watchlist(
    val id: Long,
    val name: String,
    val symbols: List<String>,
)