import SwiftUI
import Shared

/// Root SwiftUI view for the iOS app.
///
/// Hosts the shared Compose Multiplatform UI ('ComposeView') edge-to-edge. All UI is rendered by the
/// shared Kotlin Compose screens; the SwiftUI shell only provides the hosting window and the platform
/// plumbing (Koin start-up, background scheduling, analytics, WidgetKit reloads).
struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea() // let the shared Compose UI draw edge-to-edge
    }
}
