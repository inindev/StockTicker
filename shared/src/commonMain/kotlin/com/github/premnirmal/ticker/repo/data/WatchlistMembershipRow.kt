package com.github.premnirmal.ticker.repo.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * One symbol's membership in one [WatchlistRow], plus its ordered [position] within that list. A
 * symbol that appears in several watchlists has one row per list; a '(watchlistId, symbol)' pair is
 * unique (the composite primary key), so a symbol can't be added to the same list twice.
 *
 * Chosen over an ordered-CSV column on [WatchlistRow] so reordering is a single-row update and
 * cross-list queries ("which lists is this symbol in?") are cheap - that query drives the
 * add-to-watchlist dialog. Deleting a watchlist cascades its memberships away.
 */
@Entity(
    primaryKeys = ["watchlist_id", "symbol"],
    foreignKeys = [
        ForeignKey(
            entity = WatchlistRow::class,
            parentColumns = ["id"],
            childColumns = ["watchlist_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("watchlist_id")],
)
data class WatchlistMembershipRow(
    @ColumnInfo(name = "watchlist_id") val watchlistId: Long,
    @ColumnInfo(name = "symbol") val symbol: String,
    @ColumnInfo(name = "position") val position: Int,
)