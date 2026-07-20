import Foundation
import SwiftUI

struct ContentLanguageSelection: Equatable, Sendable {
    static let storageKey = "contentLanguageTermIds"

    var ids: [Int64]

    static let all = ContentLanguageSelection(ids: [])

    init(ids: [Int64]) {
        var seen = Set<Int64>()
        self.ids = ids.filter { seen.insert($0).inserted }
    }

    init(rawValue: String) {
        if rawValue.isEmpty || rawValue == "all" {
            self = .all
        } else {
            self.init(ids: rawValue.split(separator: ",").compactMap { Int64($0) })
        }
    }

    static var saved: ContentLanguageSelection {
        if let raw = UserDefaults.standard.string(forKey: storageKey) {
            return ContentLanguageSelection(rawValue: raw)
        }
        return .all
    }

    var rawValue: String {
        ids.map(String.init).joined(separator: ",")
    }

    var contentLanguageTermIds: [Int64] {
        ids
    }

    func summary(languages: [ContentLanguage]) -> String {
        guard !ids.isEmpty else { return "All languages" }
        let names = ids.map { id in
            languages.first { $0.id == id }?.name ?? "#\(id)"
        }
        if names.count <= 3 { return names.joined(separator: ", ") }
        return "\(names.prefix(3).joined(separator: ", ")) +\(names.count - 3)"
    }

    func contains(_ language: ContentLanguage) -> Bool {
        ids.contains(language.id)
    }

    mutating func toggle(_ language: ContentLanguage) {
        if ids.contains(language.id) {
            ids.removeAll { $0 == language.id }
        } else {
            ids.append(language.id)
        }
    }
}

/// The app shell: a persistent top nav (the design's pill bar) over a body that
/// swaps between the railed Home, a category grid, WordCamp event drill-ins, and
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
    /// WordCamp drill-in, shown as a full-screen cover so dismissing it restores
    /// focus to the card it opened from and it owns the Menu/back button.
    @State private var openedEvent: ContentEvent?
    @State private var playback: PlaybackRequest?
    /// Playback started inside the event cover, presented from within it.
    @State private var eventPlayback: PlaybackRequest?
    @State private var showPairing = false
    @State private var showAccountSheet = false
    @State private var showSettings = false
    /// The "Code for the People" documentary promo, opened from the Home hero.
    @State private var showPromo = false
    @AppStorage(ContentLanguageSelection.storageKey)
    private var contentLanguageSelectionRaw = ContentLanguageSelection.saved.rawValue
    /// Which nav control the tvOS focus engine currently holds, so the unified
    /// capsule can highlight it (selection and focus are independent).
    @FocusState private var focusedNav: NavFocus?

    init(repository: ContentRepository, auth: AuthManager, store: WatchProgressStore) {
        self.repository = repository
        self.store = store
        _auth = State(initialValue: auth)
    }

    /// A destination in the top nav. The WordCamp drill-in isn't here — see
    /// `openedEvent`.
    enum Section: Hashable {
        case home
        case category(NavCategory)
        case search
        case a8c
    }

    var body: some View {
        VStack(spacing: 0) {
            navBar
            body(for: selected)
                // Recreate the body when the selection — or auth state — changes,
                // so signing in/out and language changes reload content.
                .id("\(sectionKey)-\(auth.isAuthenticated)-\(contentLanguageSelectionRaw)")
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color.black.ignoresSafeArea())
        .onAppear { applyContentLanguageSelection() }
        .onChange(of: contentLanguageSelectionRaw) { _, _ in applyContentLanguageSelection() }
        .fullScreenCover(item: $openedEvent) { event in
            wordCampEventCover(event)
        }
        .fullScreenCover(isPresented: $showSettings) {
            SettingsScreen(
                repository: repository,
                languageSelection: contentLanguageSelectionBinding,
                onDismiss: { showSettings = false }
            )
        }
        .fullScreenCover(isPresented: $showPromo) {
            PromoView(onClose: { showPromo = false })
        }
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
                onOpenEvent: { openedEvent = $0 },
                resolveCover: eventCover,
                onAuthRequired: routeToPairing,
                onOpenPromo: { showPromo = true }
            )

        case .category(let category):
            if category.slug == Catalog.wordCampsSlug {
                WordCampsView(
                    repository: repository,
                    source: Sources.wordpressTV,
                    onPlay: play
                )
            } else {
                VideoGrid(
                    repository: repository,
                    source: Sources.wordpressTV,
                    query: .category(category.ref),
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

    /// The WordCamp event's video grid, shown as a full-screen cover.
    /// `.onExitCommand` dismisses it on Menu/back.
    @ViewBuilder
    private func wordCampEventCover(_ event: ContentEvent) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(event.name)
                .font(.title.weight(.bold))
                .foregroundStyle(.white)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 80)
                .padding(.top, 20)
            VideoGrid(
                repository: repository,
                source: Sources.wordpressTV,
                query: .event(event, applyLanguageFilter: false),
                onPlay: playFromEvent,
                onAuthRequired: routeToPairing
            )
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .background(Color.black.ignoresSafeArea())
        .onExitCommand { openedEvent = nil }
        .fullScreenCover(item: $eventPlayback) { request in
            PlayerView(request: request, store: store)
        }
    }

    /// Stable string for `.id(...)` — associated values make `Section` awkward to
    /// hash into a view identity directly.
    private var sectionKey: String {
        switch selected {
        case .home: "home"
        case .category(let c): "cat-\(c.slug)"
        case .search: "search"
        case .a8c: "a8c"
        }
    }

    // MARK: Nav bar

    /// A focus target in the top bar. Kept separate from `Section` because the
    /// account control isn't a content section.
    private enum NavFocus: Hashable {
        case section(Section)
        case settings
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
        HStack(spacing: 14) {
            let settingsFocused = focusedNav == .settings
            Button { showSettings = true } label: {
                Image(systemName: "gearshape.fill")
            }
            .buttonStyle(IconCircleButtonStyle(isFocused: settingsFocused))
            .focused($focusedNav, equals: .settings)
            .animation(.easeOut(duration: 0.15), value: focusedNav)

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
    }

    private var contentLanguageSelection: ContentLanguageSelection {
        ContentLanguageSelection(rawValue: contentLanguageSelectionRaw)
    }

    private var contentLanguageSelectionBinding: Binding<ContentLanguageSelection> {
        Binding(
            get: { contentLanguageSelection },
            set: { contentLanguageSelectionRaw = $0.rawValue }
        )
    }

    private func applyContentLanguageSelection() {
        repository.setContentLanguageTermIds(contentLanguageSelection.contentLanguageTermIds)
    }

    // MARK: Playback

    /// A WordCamp card's cover: the newest video's poster in that event.
    /// Best-effort — the card keeps its brand gradient if this fails.
    private func eventCover(_ event: ContentEvent) async -> URL? {
        let videos = try? await repository.listByEvent(
            source: Sources.wordpressTV,
            event: event,
            page: 1,
            applyLanguageFilter: false
        )
        guard let video = videos?.first else { return nil }
        return await repository.posterURL(source: Sources.wordpressTV, video: video)
    }

    private func play(_ video: Video, source: ContentSource) {
        Task { playback = await makePlaybackRequest(video, source: source) }
    }

    /// Play a video tapped inside the WordCamp event cover, presented from it.
    private func playFromEvent(_ video: Video, source: ContentSource) {
        Task { eventPlayback = await makePlaybackRequest(video, source: source) }
    }

    /// Resolve a tapped video to a playable asset and wire its resume point.
    /// Returns `nil` if resolution fails.
    private func makePlaybackRequest(_ video: Video, source: ContentSource) async -> PlaybackRequest? {
        guard let asset = try? await repository.resolvePlayback(source: source, video: video) else { return nil }
        let posterURL: URL?
        if let existingPosterURL = video.posterUrl {
            posterURL = existingPosterURL
        } else {
            posterURL = await repository.posterURL(source: source, video: video)
        }
        let playableVideo = video.withPosterURL(posterURL)
        let resume = store.progress(forGuid: video.videoGuid)?.positionSeconds ?? 0
        return PlaybackRequest(asset: asset, video: playableVideo, resumeAt: resume)
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

private struct IconCircleButtonStyle: ButtonStyle {
    var isFocused = false

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.title2.weight(.semibold))
            .foregroundStyle(isFocused ? .white : .white.opacity(0.7))
            .frame(width: 58, height: 58)
            .background(Circle().fill(isFocused ? .white.opacity(0.22) : .white.opacity(0.08)))
            .opacity(configuration.isPressed ? 0.75 : 1)
    }
}

private struct SettingsScreen: View {
    let repository: ContentRepository
    @Binding var languageSelection: ContentLanguageSelection
    let onDismiss: () -> Void

    @State private var languages: [ContentLanguage] = []
    @State private var state: LoadState = .loading
    @FocusState private var focusedRow: Row?

    private enum LoadState: Equatable {
        case loading
        case loaded
        case failed
    }

    private enum Row: Hashable {
        case all
        case language(Int64)
        case done
    }

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()

            VStack(spacing: 0) {
                HStack(alignment: .center) {
                    Text("Settings")
                        .font(.largeTitle.weight(.bold))
                        .foregroundStyle(.white)

                    Spacer()

                    Button("Done", action: onDismiss)
                        .buttonStyle(SettingsDoneButtonStyle(isFocused: focusedRow == .done))
                        .focused($focusedRow, equals: .done)
                }
                .padding(.horizontal, 84)
                .padding(.top, 58)
                .padding(.bottom, 44)

                HStack(alignment: .top, spacing: 72) {
                    VStack(alignment: .leading, spacing: 16) {
                        Text("Content Language")
                            .font(.title2.weight(.semibold))
                            .foregroundStyle(.white)
                            .lineLimit(1)
                            .minimumScaleFactor(0.9)

                        Text(languageSelection.summary(languages: languages))
                            .font(.body)
                            .foregroundStyle(.white.opacity(0.62))
                            .lineLimit(3)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    .frame(width: 560, alignment: .leading)
                    .padding(.top, 8)

                    languageList
                }
                .padding(.horizontal, 84)
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
            }
        }
        .task { await loadLanguages() }
        .onExitCommand(perform: onDismiss)
    }

    @ViewBuilder
    private var languageList: some View {
        switch state {
        case .loading:
            ProgressView()
                .controlSize(.large)
                .frame(maxWidth: 780, maxHeight: .infinity)

        case .failed:
            Placeholder(
                message: "Couldn’t load languages. Please try again.",
                action: ("Retry", { Task { await loadLanguages() } })
            )
            .frame(maxWidth: 780, maxHeight: .infinity)

        case .loaded:
            ScrollViewReader { proxy in
                ScrollView {
                    LazyVStack(spacing: 10) {
                        allLanguagesButton
                            .id(Row.all)

                        ForEach(languages) { language in
                            languageButton(language)
                                .id(Row.language(language.id))
                        }
                    }
                    .padding(.trailing, 36)
                    .padding(.bottom, 80)
                }
                .frame(maxWidth: 780, maxHeight: .infinity)
                .onAppear {
                    focusedRow = initialFocus
                    proxy.scrollTo(initialFocus, anchor: .center)
                }
            }
        }
    }

    private func loadLanguages() async {
        state = .loading
        do {
            languages = try await repository.listLanguages(source: Sources.wordpressTV)
            state = .loaded
        } catch {
            languages = []
            state = .failed
        }
    }

    private var initialFocus: Row {
        languageSelection.ids.first.map(Row.language) ?? .all
    }

    private var allLanguagesButton: some View {
        Button {
            languageSelection = .all
        } label: {
            SettingsLanguageRow(
                title: "All languages",
                isSelected: languageSelection.ids.isEmpty
            )
        }
        .buttonStyle(SettingsLanguageButtonStyle(isFocused: focusedRow == .all))
        .focused($focusedRow, equals: .all)
    }

    private func languageButton(_ language: ContentLanguage) -> some View {
        Button {
            var next = languageSelection
            next.toggle(language)
            languageSelection = next
        } label: {
            SettingsLanguageRow(
                title: language.name,
                isSelected: languageSelection.contains(language)
            )
        }
        .buttonStyle(SettingsLanguageButtonStyle(isFocused: focusedRow == .language(language.id)))
        .focused($focusedRow, equals: .language(language.id))
    }
}

private struct SettingsLanguageRow: View {
    let title: String
    let isSelected: Bool

    var body: some View {
        HStack(spacing: 20) {
            Image(systemName: isSelected ? "checkmark.circle.fill" : "circle")
                .font(.title3.weight(.semibold))
                .foregroundStyle(isSelected ? Brand.blue : .white.opacity(0.42))
                .frame(width: 34)

            Text(title)
                .font(.title3.weight(.semibold))
                .foregroundStyle(.white)
                .lineLimit(1)
                .minimumScaleFactor(0.82)

            Spacer(minLength: 20)
        }
        .frame(maxWidth: .infinity, minHeight: 74, alignment: .leading)
    }
}

private struct SettingsLanguageButtonStyle: ButtonStyle {
    var isFocused = false

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .padding(.horizontal, 24)
            .background(RoundedRectangle(cornerRadius: 12).fill(isFocused ? .white.opacity(0.22) : .white.opacity(0.08)))
            .overlay(RoundedRectangle(cornerRadius: 12).stroke(isFocused ? .white.opacity(0.58) : .clear, lineWidth: 2))
            .opacity(configuration.isPressed ? 0.72 : 1)
    }
}

private struct SettingsDoneButtonStyle: ButtonStyle {
    var isFocused = false

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.title3.weight(.semibold))
            .foregroundStyle(.white)
            .padding(.horizontal, 28)
            .padding(.vertical, 14)
            .background(Capsule().fill(isFocused ? Brand.blue : .white.opacity(0.1)))
            .opacity(configuration.isPressed ? 0.72 : 1)
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
