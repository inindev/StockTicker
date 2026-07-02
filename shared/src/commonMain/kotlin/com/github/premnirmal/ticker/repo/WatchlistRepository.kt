package com.github.premnirmal.ticker.repo

import com.github.premnirmal.ticker.repo.data.Watchlist
import com.github.premnirmal.ticker.repo.data.WatchlistMembershipRow
import com.github.premnirmal.ticker.repo.data.WatchlistRow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Shared (commonMain) facade over [WatchlistDao] that speaks in domain [Watchlist] objects and
 * enforces these watchlist invariants:
 *
 * - **All Symbols** is the master list. It is created on demand, is always ordered first, and every
 *   tracked symbol lives in it. Every other watchlist is a **subset**: [addSymbol] also adds the
 *   symbol to All Symbols, so the invariant 'watchlist symbols are all in AllSymbols' always holds.
 * - Removing a symbol from a subset leaves it in All Symbols; removing it from All Symbols removes it
 *   from every list (the DAO purges it from all lists).
 * - Watchlists are ordered with All Symbols pinned on top, then by name.
 *
 * The app, widgets, and [com.github.premnirmal.ticker.model.IStocksProvider] all read and write
 * watchlists through this repository rather than touching [WatchlistDao] directly.
 */
class WatchlistRepository(
    private val dao: WatchlistDao,
) {

    companion object {
        /** Name of the non-deletable, non-renameable master watchlist. */
        const val ALL_SYMBOLS_NAME: String = "All Symbols"
    }

    /** All watchlists, All Symbols first, then alphabetical (case-insensitive). */
    suspend fun getWatchlists(): List<Watchlist> {
        return dao.getWatchlists()
            .map { it.toWatchlist() }
            .sortedForDisplay()
    }

    /**
     * Reactive stream of all watchlists (each with its ordered symbols), ordered All Symbols first
     * then alphabetically. Re-emits whenever a watchlist row or any membership changes, so the UI
     * updates automatically after add/remove/rename/reorder.
     */
    fun watchlistsFlow(): Flow<List<Watchlist>> =
        combine(dao.watchlistRowsFlow(), dao.allMembershipsFlow()) { rows, memberships ->
            val bySymbol = memberships.groupBy { it.watchlistId }
            rows.map { row ->
                Watchlist(
                    id = row.id,
                    name = row.name,
                    symbols = bySymbol[row.id].orEmpty().sortedBy { it.position }.map { it.symbol },
                )
            }.sortedForDisplay()
        }

    /**
     * Reactive stream of the **All Symbols** master list's ordered symbols - i.e. the global fetch
     * set. [com.github.premnirmal.ticker.model.IStocksProvider] collects this so its 'tickerSet' is a
     * pure derived cache of All Symbols rather than a separately-persisted set. Emits 'emptyList()'
     * until All Symbols exists.
     */
    fun allSymbolsFlow(): Flow<List<String>> =
        watchlistsFlow()
            .map { lists -> lists.firstOrNull { it.name == ALL_SYMBOLS_NAME }?.symbols.orEmpty() }
            .distinctUntilChanged()

    private fun List<Watchlist>.sortedForDisplay(): List<Watchlist> =
        sortedWith(
            compareByDescending<Watchlist> { it.name == ALL_SYMBOLS_NAME }
                .thenBy { it.name.lowercase() }
        )

    suspend fun getWatchlist(id: Long): Watchlist? = dao.getWatchlist(id)?.toWatchlist()

    /** The master All Symbols watchlist, creating its (empty) row if it does not exist yet. */
    suspend fun getOrCreateAllSymbols(): Watchlist {
        val existing = dao.getWatchlistByName(ALL_SYMBOLS_NAME)
        val id = existing?.id ?: dao.insertWatchlist(WatchlistRow(name = ALL_SYMBOLS_NAME, position = 0))
        return getWatchlist(id)!!
    }

    /**
     * One-time seed of a fresh store. If the All Symbols master doesn't exist yet, creates it and
     * fills it with [tickers] (the app's global fetch set, which on a fresh install is the default
     * stocks). Runs exactly once: All Symbols can never be deleted, so once seeded this is a no-op.
     */
    suspend fun seedFromTickersIfNeeded(tickers: Collection<String>) {
        if (dao.getWatchlistByName(ALL_SYMBOLS_NAME) != null) return
        val id = dao.insertWatchlist(WatchlistRow(name = ALL_SYMBOLS_NAME, position = 0))
        if (tickers.isNotEmpty()) {
            dao.replaceMemberships(id, tickers.toList())
        }
    }

    fun isAllSymbols(watchlist: Watchlist): Boolean = watchlist.name == ALL_SYMBOLS_NAME

    /**
     * Creates a new (subset) watchlist named [name], appended after existing lists. Callers are
     * expected to have validated [name] as non-blank and unique. Returns the new watchlist's id.
     */
    suspend fun createWatchlist(name: String): Long {
        val nextPosition = (dao.getWatchlists().maxOfOrNull { it.position } ?: 0) + 1
        return dao.insertWatchlist(WatchlistRow(name = name, position = nextPosition))
    }

    suspend fun renameWatchlist(id: Long, name: String) = dao.renameWatchlist(id, name)

    suspend fun deleteWatchlist(id: Long) = dao.deleteWatchlist(id)

    /** True if a watchlist named [name] already exists (case-insensitive). */
    suspend fun nameExists(name: String): Boolean =
        dao.getWatchlists().any { it.name.equals(name, ignoreCase = true) }

    /**
     * Adds [symbol] to the watchlist [watchlistId] (appended) and guarantees it is also in All
     * Symbols, upholding the subset invariant. No-op if the symbol is already a member.
     */
    suspend fun addSymbol(watchlistId: Long, symbol: String) {
        val allSymbolsId = getOrCreateAllSymbols().id
        appendSymbol(allSymbolsId, symbol)
        if (watchlistId != allSymbolsId) {
            appendSymbol(watchlistId, symbol)
        }
    }

    /**
     * Adds [symbols] to the All Symbols master list (used by import). Symbols already present are
     * skipped; new ones are appended in order.
     */
    suspend fun addSymbolsToAllSymbols(symbols: Collection<String>) {
        val allSymbols = getOrCreateAllSymbols()
        val existing = allSymbols.symbols.toSet()
        var position = dao.maxMemberPosition(allSymbols.id) ?: -1
        val rows = symbols
            .filter { it.isNotBlank() }
            .distinct()
            .filterNot { it in existing }
            .map { WatchlistMembershipRow(watchlistId = allSymbols.id, symbol = it, position = ++position) }
        dao.insertMemberships(rows)
    }

    private suspend fun appendSymbol(watchlistId: Long, symbol: String) {
        val nextPosition = (dao.maxMemberPosition(watchlistId) ?: -1) + 1
        dao.insertMembership(
            WatchlistMembershipRow(
                watchlistId = watchlistId,
                symbol = symbol,
                position = nextPosition,
            )
        )
    }

    /**
     * Removes [symbol] from [watchlistId]. Removing from All Symbols removes it everywhere (it can no
     * longer be a subset member); removing from a subset leaves it in All Symbols.
     */
    suspend fun removeSymbol(watchlistId: Long, symbol: String) {
        val allSymbolsId = getOrCreateAllSymbols().id
        if (watchlistId == allSymbolsId) {
            dao.getWatchlistIdsForSymbol(symbol).forEach { dao.removeMembership(it, symbol) }
        } else {
            dao.removeMembership(watchlistId, symbol)
        }
    }

    /**
     * Stops tracking [symbol] entirely by removing it from All Symbols, which cascades to every
     * subset. Used by [com.github.premnirmal.ticker.model.IStocksProvider.removeStock] so untracking a
     * symbol and dropping it from All Symbols are the same operation.
     */
    suspend fun untrack(symbol: String) = removeSymbol(getOrCreateAllSymbols().id, symbol)

    /** Stops tracking every symbol in [symbols] (see [untrack]). */
    suspend fun untrack(symbols: Collection<String>) {
        val allSymbolsId = getOrCreateAllSymbols().id
        symbols.forEach { removeSymbol(allSymbolsId, it) }
    }

    /** Replaces the ordered members of [watchlistId] with [symbols]. */
    suspend fun setSymbols(watchlistId: Long, symbols: List<String>) =
        dao.replaceMemberships(watchlistId, symbols)

    /** Ids of every watchlist that currently contains [symbol]. Drives the add-to-watchlist dialog. */
    suspend fun watchlistIdsContaining(symbol: String): List<Long> =
        dao.getWatchlistIdsForSymbol(symbol)

    private suspend fun WatchlistRow.toWatchlist(): Watchlist =
        Watchlist(id = id, name = name, symbols = dao.getSymbols(id))
}