import Foundation
import UIKit

/// The "Code for the People" documentary promo. WordPress.tv can't host the film
/// directly, so we feature it on Home and send viewers to YouTube — opening a
/// YouTube app when the TV has one, and otherwise letting them scan the QR to
/// watch on their phone. This is static, curated content (like `Catalog`); it
/// never touches the repository or data layer. Mirrors the Android
/// `CodeForThePeople`.
enum CodeForThePeople {
    static let videoID = "8lQijrTaaGg"
    static let title = "Code for the People"
    static let tagline = "The human story of the open web"
    static let credit = "Directed by Bao Nguyen"
    static let blurb = """
        The open web is arguably the world's most vital invisible utility — and it's \
        under siege. Code for the People is a documentary short on the past, present, \
        and contested future of the internet: what it's for, who gets to own it, and \
        what it takes to keep it free. The web belongs to all of us.
        """

    /// Encoded in the QR and opened on a phone — the short link resolves into the app.
    static let shareURL = URL(string: "https://youtu.be/\(videoID)")!

    /// The canonical watch URL, handed to a YouTube app as a universal link.
    static let watchURL = URL(string: "https://www.youtube.com/watch?v=\(videoID)")!

    /// The YouTube app's custom-scheme deep link, tried first for a direct hand-off.
    static let appURL = URL(string: "youtube://www.youtube.com/watch?v=\(videoID)")!

    /// Shown under the QR as human-readable text.
    static let shareLabel = "youtu.be/\(videoID)"
}

/// Hands the film off to a YouTube app on the TV. tvOS has no browser fallback, so
/// when nothing can open it the caller surfaces a toast (like Android's system
/// "no app can do this"); the QR/phone path stays separately available. Mirrors the
/// Android intent-based hand-off.
@MainActor
enum YouTubeHandoff {
    /// Open the film in a YouTube app: the custom scheme first (the reliable signal
    /// on a browser-less TV), then the universal link. Reports `false` when nothing
    /// could open it, so the caller can fall back to the QR.
    static func open(_ completion: @escaping (Bool) -> Void) {
        open([CodeForThePeople.appURL, CodeForThePeople.watchURL], completion: completion)
    }

    private static func open(_ candidates: [URL], completion: @escaping (Bool) -> Void) {
        guard let url = candidates.first else {
            completion(false)
            return
        }
        UIApplication.shared.open(url, options: [:]) { success in
            if success {
                completion(true)
            } else {
                open(Array(candidates.dropFirst()), completion: completion)
            }
        }
    }
}
