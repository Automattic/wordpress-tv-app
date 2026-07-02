import SwiftUI
import WordPressTVCore

@main
struct WordPressTVApp: App {
    // Composition root. `auth` owns the a8c.tv token (Keychain + broker) and
    // feeds it to the repository, which sets `Authorization: Bearer` for the
    // private source. wordpress.tv stays public/no-auth.
    private let auth: AuthManager
    private let repository: ContentRepository

    /// Splash plays once per cold launch, then hands off to the content grid.
    @State private var showSplash = true

    init() {
        let auth = AuthManager(broker: BrokerClient(baseURL: Self.brokerBaseURL, session: Self.brokerSession))
        self.auth = auth
        self.repository = WPComContentRepository(authProvider: auth)
    }

    #if DEBUG
    /// TEMP: route broker traffic through mitmproxy on the Mac (127.0.0.1:8082,
    /// reachable from the sim via shared loopback) so we can see the exact
    /// request/response on the wire. mitmproxy resolves wordpress.tv via the
    /// Mac's /etc/hosts → the sandbox. Remove before shipping.
    private static var brokerSession: URLSession {
        let c = URLSessionConfiguration.ephemeral
        c.connectionProxyDictionary = [
            "HTTPEnable": 1, "HTTPProxy": "127.0.0.1", "HTTPPort": 8082,
            "HTTPSEnable": 1, "HTTPSProxy": "127.0.0.1", "HTTPSPort": 8082,
        ]
        return URLSession(configuration: c)
    }
    #else
    private static var brokerSession: URLSession { .shared }
    #endif

    var body: some Scene {
        WindowGroup {
            if showSplash {
                SplashView { showSplash = false }
            } else {
                ContentRootView(repository: repository, auth: auth)
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
