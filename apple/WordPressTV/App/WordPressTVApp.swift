import SwiftUI
import WordPressTVCore

@main
struct WordPressTVApp: App {
    // Composition root: the one real repository, the one registered source.
    // Swap `WPComContentRepository` for a fake here to develop UI offline.
    private let repository: ContentRepository = WPComContentRepository()

    var body: some Scene {
        WindowGroup {
            LatestView(repository: repository, source: Sources.wordpressTV)
        }
    }
}
