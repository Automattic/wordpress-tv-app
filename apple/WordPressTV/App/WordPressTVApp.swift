import SwiftUI
import WordPressTVCore

@main
struct WordPressTVApp: App {
    // Composition root: the one real repository, the one registered source.
    // Swap `WPComContentRepository` for a fake here to develop UI offline.
    private let repository: ContentRepository = WPComContentRepository()

    /// Splash plays once per cold launch, then hands off to the content grid.
    @State private var showSplash = true

    var body: some Scene {
        WindowGroup {
            if showSplash {
                SplashView { showSplash = false }
            } else {
                LatestView(repository: repository, source: Sources.wordpressTV)
            }
        }
    }
}
