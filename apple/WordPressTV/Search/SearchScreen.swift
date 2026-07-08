import SwiftUI

/// Search over the current source. A focusable field brings up the tvOS
/// keyboard; submitting runs the query and rebuilds the results grid (the
/// `.id(term)` forces a fresh load per submitted term).
struct SearchScreen: View {
    let repository: ContentRepository
    let source: ContentSource
    let onPlay: (Video, ContentSource) -> Void

    @State private var query = ""
    @State private var submitted = ""

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            searchField
            results
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
    }

    private var searchField: some View {
        HStack(spacing: 16) {
            Image(systemName: "magnifyingglass")
                .foregroundStyle(.secondary)
            TextField("Search talks, speakers, and topics", text: $query)
                .textFieldStyle(.plain)
                .font(.title3)
                .onSubmit { submitted = query.trimmingCharacters(in: .whitespacesAndNewlines) }
        }
        .padding(.horizontal, 32)
        .padding(.vertical, 20)
        .background(Color.white.opacity(0.1), in: Capsule())
        .padding(.horizontal, 80)
        .padding(.top, 20)
    }

    @ViewBuilder
    private var results: some View {
        if submitted.isEmpty {
            Placeholder(message: "Search WordPress.tv for talks, speakers, and topics.")
        } else {
            VideoGrid(
                repository: repository,
                source: source,
                query: .search(submitted),
                onPlay: onPlay
            )
            .id(submitted)
        }
    }
}
