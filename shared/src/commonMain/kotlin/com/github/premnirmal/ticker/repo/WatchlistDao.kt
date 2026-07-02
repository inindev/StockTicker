package com.github.premnirmal.ticker.repo

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.github.premnirmal.ticker.repo.data.WatchlistMembershipRow
import com.github.premnirmal.ticker.repo.data.WatchlistRow
import kotlinx.coroutines.flow.Flow

/**
 * Room DAO for watchlist rows ([WatchlistRow]) and their ordered members
 * ([WatchlistMembershipRow]). Callers should go through [WatchlistRepository], which enforces the
 * All Symbols master-list invariants on top of these primitives.
 */
@Dao
interface WatchlistDao {

    @Query("SELECT * FROM WatchlistRow ORDER BY position")
    suspend fun getWatchlists(): List<WatchlistRow>

    /** Re-emits whenever any watchlist row changes (insert/rename/delete/reorder). */
    @Query("SELECT * FROM WatchlistRow")
    fun watchlistRowsFlow(): Flow<List<WatchlistRow>>

    /** Re-emits whenever any membership changes; drives reactive list rebuilds alongside the rows. */
    @Query("SELECT * FROM WatchlistMembershipRow ORDER BY position")
    fun allMembershipsFlow(): Flow<List<WatchlistMembershipRow>>

    @Query("SELECT * FROM WatchlistRow WHERE id = :watchlistId")
    suspend fun getWatchlist(watchlistId: Long): WatchlistRow?

    @Query("SELECT * FROM WatchlistRow WHERE name = :name LIMIT 1")
    suspend fun getWatchlistByName(name: String): WatchlistRow?

    /** Returns the new watchlist's id. */
    @Insert
    suspend fun insertWatchlist(watchlist: WatchlistRow): Long

    @Query("UPDATE WatchlistRow SET name = :name WHERE id = :watchlistId")
    suspend fun renameWatchlist(watchlistId: Long, name: String)

    @Query("UPDATE WatchlistRow SET position = :position WHERE id = :watchlistId")
    suspend fun setWatchlistPosition(watchlistId: Long, position: Int)

    /** Deleting a watchlist cascades its [WatchlistMembershipRow] rows away. */
    @Query("DELETE FROM WatchlistRow WHERE id = :watchlistId")
    suspend fun deleteWatchlist(watchlistId: Long)

    @Query("SELECT symbol FROM WatchlistMembershipRow WHERE watchlist_id = :watchlistId ORDER BY position")
    suspend fun getSymbols(watchlistId: Long): List<String>

    @Query("SELECT watchlist_id FROM WatchlistMembershipRow WHERE symbol = :symbol")
    suspend fun getWatchlistIdsForSymbol(symbol: String): List<Long>

    @Query("SELECT MAX(position) FROM WatchlistMembershipRow WHERE watchlist_id = :watchlistId")
    suspend fun maxMemberPosition(watchlistId: Long): Int?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMembership(membership: WatchlistMembershipRow)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMemberships(memberships: List<WatchlistMembershipRow>)

    @Query("DELETE FROM WatchlistMembershipRow WHERE watchlist_id = :watchlistId AND symbol = :symbol")
    suspend fun removeMembership(watchlistId: Long, symbol: String)

    @Query("DELETE FROM WatchlistMembershipRow WHERE watchlist_id = :watchlistId")
    suspend fun clearMemberships(watchlistId: Long)

    /** Replaces a watchlist's members with [symbols] in the given order (0-based positions). */
    @Transaction
    suspend fun replaceMemberships(watchlistId: Long, symbols: List<String>) {
        clearMemberships(watchlistId)
        insertMemberships(
            symbols.mapIndexed { index, symbol ->
                WatchlistMembershipRow(watchlistId = watchlistId, symbol = symbol, position = index)
            }
        )
    }
}