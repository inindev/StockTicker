import AppIntents
import WidgetKit
import Shared

/// Supplies the watchlist names the user can pick for a widget, read from the shared App Group
/// snapshot ('WidgetSnapshotStore') the app writes after every refresh - so the picker always offers
/// exactly the user's current watchlists without the widget extension needing the app's Koin graph /
/// Room database. This is the iOS counterpart of Android's per-widget watchlist selection.
///
/// We deliberately use a plain 'String' parameter + 'DynamicOptionsProvider' rather than an
/// 'AppEntity' + 'EntityQuery'. On current iOS, WidgetKit fails to rehydrate a selected 'AppEntity'
/// for a configuration intent ("Failed to build EntityIdentifier ... is not a registered AppEntity
/// identifier"), so the selection silently resolves back to nil and the widget is stuck on the
/// default. A 'String' parameter is persisted and passed back verbatim - no 'EntityIdentifier'
/// round-trip - so the selection actually sticks.
struct WatchlistOptionsProvider: DynamicOptionsProvider {

    /// The options shown in the picker: one entry per watchlist, by name. "All Symbols" is written
    /// first in the snapshot, so it heads the list.
    func results() async throws -> [String] {
        currentWatchlistNames()
    }

    /// A freshly placed widget defaults to "All Symbols" (the first snapshot entry) instead of
    /// showing an empty "Choose".
    func defaultResult() async -> String? {
        currentWatchlistNames().first
    }

    private func currentWatchlistNames() -> [String] {
        let snapshot = WidgetSnapshotStore.companion.create().read()
        return (snapshot?.watchlists ?? []).map { $0.name }
    }
}

/// Per-widget configuration for the StocksWidget home-screen widget.
///
/// This is the iOS counterpart of Android's per-widget Glance options: each placed widget instance
/// is a **view onto a watchlist** and keeps its own appearance. Editing it (touch & hold the widget ->
/// *Edit Widget*) presents these parameters; WidgetKit then rebuilds that instance's timeline with the
/// new configuration. The watchlist selection picks one list out of the shared snapshot by name, while
/// appearance is applied purely on the render side, so no extra data crosses the App Group boundary.
struct StockTickerConfigurationIntent: WidgetConfigurationIntent {
    static var title: LocalizedStringResource = "Stocks Widget"
    static var description = IntentDescription("Choose which watchlist to show and how this widget looks.")

    /// The name of the watchlist this widget shows. 'nil'/unknown falls back to All Symbols.
    /// Watchlist names are effectively unique in the app, so matching the snapshot by name is safe
    /// and avoids the buggy 'AppEntity' identifier round-trip (see 'WatchlistOptionsProvider').
    @Parameter(title: "Watchlist", description: "Which watchlist this widget shows. Defaults to All Symbols.",
               optionsProvider: WatchlistOptionsProvider())
    var watchlistName: String?

    /// Sort the rows by the largest movers first instead of the watchlist order.
    @Parameter(title: "Sort by change", default: false)
    var sortByChange: Bool

    /// Show each symbol's absolute change amount under the price.
    @Parameter(title: "Show change amount", default: false)
    var showChangeAmount: Bool

    /// Render the change percentage in a bold weight.
    @Parameter(title: "Bold change", default: true)
    var boldChange: Bool

    /// Show the "last fetch" timestamp header (used by the compact widget).
    @Parameter(title: "Show header", default: true)
    var showHeader: Bool
}