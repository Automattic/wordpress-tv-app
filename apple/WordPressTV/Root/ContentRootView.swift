import SwiftUI

/// The app shell: a persistent top nav (the design's pill bar) over a body that
/// swaps between the railed Home, a category grid, flagship-camp drill-ins, and
/// search. Playback is hoisted here so any screen can request it through one
/// `play` path — which also wires the resume position and progress recording.
///
/// WordPress.tv is public and drives the whole visible nav. The private a8c.tv
/// source stays available to signed-in Automatticians as an extra trailing tab,
/// preserving the employee flow without intruding on the public design.
struct ContentRootView: View {
    let repository: ContentRepository
    let store: WatchProgressStore
    @State private var auth: AuthManager
    @State private var selected: Section = .home
    @State private var playback: PlaybackRequest?
    @State private var showPairing = false
    @State private var showAccountSheet = false
    /// Which nav control the tvOS focus engine currently holds, so the unified
    /// capsule can highlight it (selection and focus are independent).
    @FocusState private var focusedNav: NavFocus?

    init(repository: ContentRepository, auth: AuthManager, store: WatchProgressStore) {
        self.repository = repository
        self.store = store
        _auth = State(initialValue: auth)
    }

    /// A destination in the top nav (plus the flagship drill-in, which no pill
    /// selects).
    enum Section: Hashable {
        case home
        case category(NavCategory)
        case search
        case a8c
        case flagship(FlagshipCamp)
    }

    var body: some View {
        VStack(spacing: 0) {
            navBar
            body(for: selected)
                // Recreate the body when the selection — or auth state — changes,
                // so signing in/out reloads private content.
                .id("\(sectionKey)-\(auth.isAuthenticated)")
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color.black.ignoresSafeArea())
        .fullScreenCover(item: $playback) { request in
            PlayerView(request: request, store: store)
        }
        .fullScreenCover(isPresented: $showPairing) {
            PairingView(
                broker: auth.broker,
                onAuthorized: { result in
                    auth.signIn(result: result)
                    if auth.isAuthorizedForA8C { selected = .a8c }
                    showPairing = false
                },
                onCancel: {
                    showPairing = false
                    if !auth.isAuthorizedForA8C { selected = .home }
                }
            )
        }
    }

    // MARK: Body

    @ViewBuilder
    private func body(for section: Section) -> some View {
        switch section {
        case .home:
            HomeView(
                repository: repository,
                source: Sources.wordpressTV,
                store: store,
                onPlay: play,
                onOpenCamp: { selected = .flagship($0) },
                resolveCover: campCover,
                onAuthRequired: routeToPairing
            )

        case .category(let category):
            VideoGrid(
                repository: repository,
                source: Sources.wordpressTV,
                query: .category(category.ref),
                onPlay: play,
                onAuthRequired: routeToPairing
            )

        case .flagship(let camp):
            VStack(alignment: .leading, spacing: 8) {
                Text(camp.title)
                    .font(.title.weight(.bold))
                    .foregroundStyle(.white)
                    .padding(.horizontal, 80)
                    .padding(.top, 20)
                VideoGrid(
                    repository: repository,
                    source: Sources.wordpressTV,
                    query: .category(camp.ref),
                    onPlay: play,
                    onAuthRequired: routeToPairing
                )
            }

        case .search:
            SearchScreen(repository: repository, source: Sources.wordpressTV, onPlay: play)

        case .a8c:
            VideoGrid(
                repository: repository,
                source: Sources.a8cTV,
                query: .latest,
                onPlay: play,
                onAuthRequired: routeToPairing
            )
        }
    }

    /// Stable string for `.id(...)` — associated values make `Section` awkward to
    /// hash into a view identity directly.
    private var sectionKey: String {
        switch selected {
        case .home: "home"
        case .category(let c): "cat-\(c.slug)"
        case .flagship(let c): "camp-\(c.slug)"
        case .search: "search"
        case .a8c: "a8c"
        }
    }

    // MARK: Nav bar

    /// A focus target in the top bar. Kept separate from `Section` because the
    /// account control isn't a content section.
    private enum NavFocus: Hashable {
        case section(Section)
        case account
    }

    /// The design's top bar: the WordPress mark, one translucent capsule holding
    /// the section tabs and search, and the account control — laid out edge to
    /// edge with the capsule centered.
    private var navBar: some View {
        HStack(spacing: 24) {
            WordPressMark().frame(width: 52, height: 52)

            Spacer(minLength: 24)

            navCapsule

            Spacer(minLength: 24)

            accountControl
        }
        .padding(.horizontal, 80)
        .padding(.top, 44)
        .padding(.bottom, 24)
    }

    /// The single pill from the mock: text tabs (selected one a solid blue pill)
    /// plus a trailing search glyph, all inside one translucent capsule.
    private var navCapsule: some View {
        HStack(spacing: 6) {
            navItem(.home) { Text("Home") }
            ForEach(Catalog.categories) { category in
                navItem(.category(category)) { Text(category.title) }
            }
            if auth.isAuthorizedForA8C {
                navItem(.a8c) { Text("a8c.tv") }
            }
            navItem(.search) { Image(systemName: "magnifyingglass") }
        }
        .padding(8)
        .background(Capsule().fill(Color.white.opacity(0.08)))
    }

    /// One tab inside the capsule. A custom `ButtonStyle` (not `.plain`) so tvOS
    /// doesn't paint its own opaque white focus highlight over ours — selection
    /// is a brand-blue pill, focus a subtle translucent pill, both contained.
    @ViewBuilder
    private func navItem<Label: View>(_ section: Section, @ViewBuilder label: () -> Label) -> some View {
        Button { selected = section } label: { label() }
            .buttonStyle(CapsuleTabStyle(
                isSelected: selected == section,
                isFocused: focusedNav == .section(section)
            ))
            .focused($focusedNav, equals: .section(section))
            .animation(.easeOut(duration: 0.15), value: focusedNav)
    }

    /// Gravatar (→ log out) when signed in, a "Sign in" pill otherwise — both
    /// with a contained focus highlight, no system chrome.
    @ViewBuilder
    private var accountControl: some View {
        let isFocused = focusedNav == .account
        if auth.isAuthenticated {
            Button { showAccountSheet = true } label: {
                AvatarView(url: auth.account?.avatarURL)
                    .overlay(Circle().stroke(.white, lineWidth: isFocused ? 4 : 0))
            }
            .buttonStyle(BareFocusStyle())
            .focused($focusedNav, equals: .account)
            .animation(.easeOut(duration: 0.15), value: focusedNav)
            .confirmationDialog(
                auth.account?.displayName ?? "Account",
                isPresented: $showAccountSheet,
                titleVisibility: .visible
            ) {
                Button("Log out", role: .destructive, action: logOut)
                Button("Cancel", role: .cancel) {}
            }
        } else {
            // Sign-in relies on the QR pairing backend, which isn't ready yet, so
            // the entry point is limited to debug builds until it ships.
            #if DEBUG
            Button { showPairing = true } label: {
                Label("Sign in", systemImage: "person.crop.circle")
            }
            .buttonStyle(CapsuleTabStyle(isFocused: isFocused, idleFill: .white.opacity(0.08)))
            .focused($focusedNav, equals: .account)
            .animation(.easeOut(duration: 0.15), value: focusedNav)
            #endif
        }
    }

    // MARK: Playback

    /// A flagship card's cover: the newest video's poster in that camp's
    /// category. Best-effort — the card keeps its brand gradient if this fails.
    private func campCover(_ camp: FlagshipCamp) async -> URL? {
        let videos = try? await repository.listByCategory(
            source: Sources.wordpressTV,
            category: camp.ref,
            page: 1
        )
        return videos?.first?.posterUrl
    }

    /// Resolve a tapped video to a playable asset, wire its resume point, and
    /// present the player. Best-effort: if resolution fails the cover just
    /// doesn't present.
    private func play(_ video: Video, source: ContentSource) {
        Task {
            guard let asset = try? await repository.resolvePlayback(source: source, video: video) else { return }
            let resume = store.progress(forGuid: video.videoGuid)?.positionSeconds ?? 0
            playback = PlaybackRequest(asset: asset, video: video, resumeAt: resume)
        }
    }

    // MARK: Actions

    /// A grid reported a 401/403 (an a8c.tv session expired) — clear the stale
    /// token and re-pair.
    private func routeToPairing() {
        auth.signOut()
        selected = .home
        showPairing = true
    }

    private func logOut() {
        auth.signOut()
        selected = .home
    }
}

/// The capsule tab look: a rounded text pill that is brand-blue when selected, a
/// subtle translucent fill when focused, and `idleFill` otherwise. A custom
/// `ButtonStyle` on purpose — it replaces (rather than layers over) the tvOS
/// system focus highlight, which is the opaque white blob we don't want.
private struct CapsuleTabStyle: ButtonStyle {
    var isSelected = false
    var isFocused = false
    var idleFill: Color = .clear

    func makeBody(configuration: Configuration) -> some View {
        let fill: Color = isSelected
            ? Brand.blue
            : (isFocused ? .white.opacity(0.22) : idleFill)
        return configuration.label
            .font(.title3.weight(isSelected ? .semibold : .regular))
            .foregroundStyle(isSelected || isFocused ? .white : .white.opacity(0.62))
            // One line at natural width, or the capsule compresses labels into
            // mid-word wraps ("Word-Camps").
            .lineLimit(1)
            .fixedSize(horizontal: true, vertical: false)
            .padding(.horizontal, 24)
            .padding(.vertical, 12)
            .background(Capsule().fill(fill))
            .opacity(configuration.isPressed ? 0.75 : 1)
    }
}

/// Bare style for the avatar: just a press dim, no system focus chrome (the
/// focus ring is drawn by the caller). Keeps the round avatar from getting the
/// white rounded-rect highlight.
private struct BareFocusStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label.opacity(configuration.isPressed ? 0.75 : 1)
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
