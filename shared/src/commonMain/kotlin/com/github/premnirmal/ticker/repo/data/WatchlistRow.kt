package com.github.premnirmal.ticker.repo.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A first-class watchlist: '{ id, name, ordered symbols }'. The symbols live in
 * [WatchlistMembershipRow] (one row per symbol, ordered by its 'position'), so this row only holds
 * the list's identity and its own display order.
 *
 * The special **All Symbols** master list is just a [WatchlistRow] like any other - it is
 * distinguished by convention (pinned on top, non-deletable, non-renameable) rather than by a
 * separate type, and it physically holds every tracked symbol while every other watchlist is a subset
 * of it.
 */
@Entity
data class WatchlistRow(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Long = 0,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "position") val position: Int,
)