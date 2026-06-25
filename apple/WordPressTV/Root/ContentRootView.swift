import SwiftUI
import WordPressTVCore

/// The app's home after the splash. Shows the public WordPress.tv grid out of
/// the box — no account needed — with a "Sign in" affordance in the top bar.
///
/// Signing in is plain WordPress.com OAuth (the QR pairing flow). Once a token
/// lands, `AuthManager` loads the account and checks a8c.tv access: an employee
/// gets the private a8c.tv source revealed and selected; anyone else simply
/// stays on WordPress.tv, signed in, with no a8c.tv entry point ever shown.
struct ContentRootView: View {
    let repository: ContentRepository
    @State private var auth: AuthManager
    @State private var selected: ContentSource = Sources.wordpressTV
    @State private var showPairing = false
    @State private var showAccountSheet = false

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
                onAuthorized: { result in
                    auth.signIn(result: result)
                    // Reveal and jump to a8c.tv only for an Automattician;
                    // everyone else lands back on WordPress.tv, signed in.
                    if auth.isAuthorizedForA8C { selected = Sources.a8cTV }
                    showPairing = false
                },
                onCancel: {
                    showPairing = false
                    if !auth.isAuthorizedForA8C { selected = Sources.wordpressTV }
                }
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

            ForEach(visibleSources) { source in
                sourceButton(source)
            }

            accountControl
        }
        .padding(.horizontal, 80)
        .padding(.top, 60)
        .padding(.bottom, 24)
    }

    /// WordPress.tv is always offered; the private a8c.tv source appears only
    /// once we've confirmed the signed-in account can read it.
    private var visibleSources: [ContentSource] {
        Sources.all.filter { $0.auth == .none || auth.isAuthorizedForA8C }
    }

    @ViewBuilder
    private func sourceButton(_ source: ContentSource) -> some View {
        let button = Button { selected = source } label: {
            Text(source.displayName)
        }

        if source.id == selected.id {
            button.buttonStyle(.borderedProminent)
        } else {
            button.buttonStyle(.bordered)
        }
    }

    /// Right side of the bar: a Gravatar (tap → log out) when signed in, a plain
    /// "Sign in" button otherwise.
    @ViewBuilder
    private var accountControl: some View {
        if auth.isAuthenticated {
            Button { showAccountSheet = true } label: {
                AvatarView(url: auth.account?.avatarURL)
            }
            .buttonStyle(.bordered)
            .confirmationDialog(
                auth.account?.displayName ?? "Account",
                isPresented: $showAccountSheet,
                titleVisibility: .visible
            ) {
                Button("Log out", role: .destructive, action: logOut)
                Button("Cancel", role: .cancel) {}
            }
        } else {
            Button { showPairing = true } label: {
                Label("Sign in", systemImage: "person.crop.circle")
            }
            .buttonStyle(.bordered)
        }
    }

    // MARK: Actions

    /// LatestView reported a 401/403 (an authorized a8c.tv session expired) —
    /// clear the stale token and re-pair.
    private func routeToPairing() {
        auth.signOut()
        selected = Sources.wordpressTV
        showPairing = true
    }

    private func logOut() {
        auth.signOut()
        selected = Sources.wordpressTV
    }
}

/// Circular Gravatar for the signed-in user, falling back to a glyph while the
/// image loads or if the account has no avatar.
private struct AvatarView: View {
    let url: URL?

    var body: some View {
        AsyncImage(url: url) { phase in
            switch phase {
            case .success(let image):
                image.resizable().scaledToFill()
            default:
                Image(systemName: "person.crop.circle.fill")
                    .resizable()
                    .scaledToFit()
                    .foregroundStyle(.white.opacity(0.7))
            }
        }
        .frame(width: 52, height: 52)
        .clipShape(Circle())
    }
}
