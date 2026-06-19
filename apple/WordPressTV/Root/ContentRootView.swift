import SwiftUI
import WordPressTVCore

/// The app's home after the splash. Hosts the source switcher (WordPress.tv /
/// a8c.tv) above the Latest grid, and gates the private a8c.tv source behind the
/// QR pairing flow.
///
/// Picking a8c.tv while signed out presents `PairingView`; once a token lands we
/// switch to it. A 401/403 mid-session bubbles up as `LatestView`'s
/// "needs auth" state, which routes back here to re-pair.
struct ContentRootView: View {
    let repository: ContentRepository
    @State private var auth: AuthManager
    @State private var selected: ContentSource = Sources.wordpressTV
    @State private var showPairing = false

    init(repository: ContentRepository, auth: AuthManager) {
        self.repository = repository
        _auth = State(initialValue: auth)
    }

    var body: some View {
        VStack(spacing: 0) {
            sourceBar
            LatestView(repository: repository, source: selected, onAuthRequired: routeToPairing)
                // Recreate the grid when the source — or auth state — changes, so
                // signing in/out triggers a fresh load.
                .id("\(selected.id)-\(auth.isAuthenticated)")
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color.black.ignoresSafeArea())
        .fullScreenCover(isPresented: $showPairing) {
            PairingView(
                broker: auth.broker,
                onAuthorized: { token in
                    auth.signIn(token: token)
                    selected = Sources.a8cTV
                    showPairing = false
                },
                onCancel: { showPairing = false }
            )
        }
    }

    // MARK: Source bar

    private var sourceBar: some View {
        HStack(spacing: 24) {
            Text("WordPress TV")
                .font(.system(size: 40, weight: .bold))
                .foregroundStyle(.white)

            Spacer()

            ForEach(Sources.all) { source in
                sourceButton(source)
            }

            if selected.id == Sources.a8cTV.id, auth.isAuthenticated {
                Button(role: .destructive, action: logOut) {
                    Label("Log out", systemImage: "rectangle.portrait.and.arrow.right")
                }
                .buttonStyle(.bordered)
            }
        }
        .padding(.horizontal, 80)
        .padding(.top, 60)
        .padding(.bottom, 24)
    }

    @ViewBuilder
    private func sourceButton(_ source: ContentSource) -> some View {
        let isSelected = source.id == selected.id
        let locked = source.auth == .wpcomOAuth && !auth.isAuthenticated

        let button = Button { select(source) } label: {
            if locked {
                Label(source.displayName, systemImage: "lock.fill")
            } else {
                Text(source.displayName)
            }
        }

        if isSelected {
            button.buttonStyle(.borderedProminent)
        } else {
            button.buttonStyle(.bordered)
        }
    }

    // MARK: Actions

    private func select(_ source: ContentSource) {
        if source.auth == .wpcomOAuth, !auth.isAuthenticated {
            showPairing = true // sign in first; we switch on success
        } else {
            selected = source
        }
    }

    /// LatestView reported a 401/403 — clear the stale token and re-pair.
    private func routeToPairing() {
        auth.signOut()
        showPairing = true
    }

    private func logOut() {
        auth.signOut()
        selected = Sources.wordpressTV
    }
}
