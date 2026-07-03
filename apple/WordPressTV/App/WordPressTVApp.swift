import SwiftUI
import WordPressTVCore

@main
struct WordPressTVApp: App {
    // Composition root. `auth` owns the a8c.tv token (Keychain + broker) and
    // feeds it to the repository, which sets `Authorization: Bearer` for the
    // private source. wordpress.tv stays public/no-auth.
    private let auth: AuthManager
    private let repository: ContentRepository
    /// Local Continue Watching store (per-device resume points).
    private let store: WatchProgressStore

    /// Splash plays once per cold launch, then hands off to the content grid.
    @State private var showSplash = true

    init() {
        let auth = AuthManager(broker: BrokerClient(baseURL: Self.brokerBaseURL))
        self.auth = auth
        self.repository = WPComContentRepository(authProvider: auth)
        self.store = WatchProgressStore()
    }

    var body: some Scene {
        WindowGroup {
            if showSplash {
                SplashView { showSplash = false }
            } else {
                ContentRootView(repository: repository, auth: auth, store: store)
            }
        }
    }

    /// Broker location: `BrokerBaseURL` from Info.plist, else the broker's
    /// `/pairing` routes on wordpress.tv.
    private static var brokerBaseURL: URL {
        if let string = Bundle.main.object(forInfoDictionaryKey: "BrokerBaseURL") as? String,
           let url = URL(string: string) {
            return url
        }
        return URL(string: "https://wordpress.tv/pairing")!
    }
}
