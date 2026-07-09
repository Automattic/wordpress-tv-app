import SwiftUI

// The card vocabulary shared across Home, category grids, and search: a poster
// primitive plus the three card shapes in the design — landscape video card,
// portrait WordCamp event card, and the wide Continue Watching card with a
// resume bar.

/// A rounded poster image with a branded placeholder while it loads or if the
/// source has none. `aspect` sizes it; the caller fixes the width.
struct PosterImage: View {
    let url: URL?
    var aspect: CGFloat = 16 / 9
    var cornerRadius: CGFloat = 16

    var body: some View {
        AsyncImage(url: url) { phase in
            switch phase {
            case .success(let image):
                image.resizable().scaledToFill()
            default:
                ZStack {
                    Rectangle().fill(Color.white.opacity(0.08))
                    Image(systemName: "play.rectangle.fill")
                        .font(.largeTitle)
                        .foregroundStyle(.white.opacity(0.35))
                }
            }
        }
        .aspectRatio(aspect, contentMode: .fill)
        .frame(maxWidth: .infinity)
        .clipShape(RoundedRectangle(cornerRadius: cornerRadius))
    }
}

/// Landscape poster + title. Used in every horizontal rail and grid. The poster
/// URL resolves lazily (private a8c.tv posters need a token appended), so only
/// on-screen cards fetch one. The `.card` button style gives the tvOS focus
/// lift/parallax for free.
struct VideoCard: View {
    let video: Video
    var width: CGFloat = 360
    let resolvePoster: (Video) async -> URL?
    let onSelect: () -> Void

    @State private var posterURL: URL?

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Button(action: onSelect) {
                PosterImage(url: posterURL)
            }
            .buttonStyle(.card)

            Text(video.title)
                .font(.callout.weight(.medium))
                .foregroundStyle(.white)
                .lineLimit(2, reservesSpace: true)
                .multilineTextAlignment(.leading)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
        .frame(width: width)
        .task { posterURL = await resolvePoster(video) }
    }
}

/// Portrait WordCamp event card. A real cover image from the event's latest
/// video sits behind a brand-tinted scrim, with the WordPress mark and event
/// name over it — a populated poster rather than a flat colour. Falls back to
/// the brand gradient until (or if) the cover resolves. Opens the event's videos.
struct PortraitCampCard: View {
    let event: ContentEvent
    var width: CGFloat = 270
    /// Resolves the cover image (the event's newest video poster). Lazy so only
    /// on-screen cards fetch.
    let resolveCover: (ContentEvent) async -> URL?
    let onSelect: () -> Void

    @State private var coverURL: URL?

    private var height: CGFloat { width * 1.5 }

    var body: some View {
        VStack(alignment: .leading, spacing: 18) {
            Button(action: onSelect) {
                ZStack(alignment: .bottomLeading) {
                    // Brand gradient — the base, and the fallback if no cover.
                    LinearGradient(colors: event.colors, startPoint: .top, endPoint: .bottom)

                    // Real cover image, sized to the card and clipped so it can't
                    // grow the ZStack (which would push the title out of view).
                    if let coverURL {
                        AsyncImage(url: coverURL) { image in
                            image.resizable().scaledToFill()
                        } placeholder: {
                            Color.clear
                        }
                        .frame(width: width, height: height)
                        .clipped()
                    }

                    // Full-card gradient: a light top darkening for cohesion,
                    // deepening into the brand colour at the bottom so the cover
                    // reads as this camp and the title stays legible over any art.
                    LinearGradient(
                        colors: [
                            .black.opacity(0.4),
                            .black.opacity(0.1),
                            event.colors.last?.opacity(0.98) ?? .black.opacity(0.9),
                        ],
                        startPoint: .top,
                        endPoint: .bottom
                    )

                    WordPressMark()
                        .frame(width: 36, height: 36)
                        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
                        .padding(22)

                    VStack(alignment: .leading, spacing: 2) {
                        Text("WordCamp")
                            .font(.title3.weight(.semibold))
                            .foregroundStyle(.white.opacity(0.9))
                        Text(event.displayPlace)
                            .font(.system(size: 36, weight: .heavy))
                            .foregroundStyle(.white)
                            .minimumScaleFactor(0.6)
                            .lineLimit(2)
                    }
                    .padding(22)
                }
                .frame(width: width, height: height)
                .clipShape(RoundedRectangle(cornerRadius: 18))
            }
            .buttonStyle(.card)

            Text(event.name)
                .font(.callout.weight(.medium))
                .foregroundStyle(.white)
                .lineLimit(1)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
        .frame(width: width)
        .task { coverURL = await resolveCover(event) }
    }
}

/// Wide Continue Watching card: poster with a resume bar showing how far in the
/// viewer got. Tapping resumes from `progress.positionSeconds`.
struct ContinueWatchingCard: View {
    let progress: WatchProgress
    var width: CGFloat = 440
    let resolvePoster: @MainActor (WatchProgress) async -> URL?
    let onPosterResolved: @MainActor (String, URL) -> Void
    let onSelect: () -> Void

    @State private var posterURL: URL?

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Button(action: onSelect) {
                PosterImage(url: posterURL)
                    .overlay(alignment: .bottom) {
                        ResumeBar(fraction: progress.fractionComplete)
                            .padding(.horizontal, 12)
                            .padding(.bottom, 12)
                    }
            }
            .buttonStyle(.card)

            Text(progress.title)
                .font(.callout.weight(.medium))
                .foregroundStyle(.white)
                .lineLimit(2, reservesSpace: true)
                .multilineTextAlignment(.leading)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
        .frame(width: width)
        .task(id: "\(progress.videoGuid)-\(progress.posterURLString ?? "")") {
            posterURL = progress.posterURL
            if posterURL == nil, let resolved = await resolvePoster(progress) {
                posterURL = resolved
                onPosterResolved(progress.videoGuid, resolved)
            }
        }
    }
}

/// A thin rounded resume indicator: full-width track, brand-blue fill.
private struct ResumeBar: View {
    let fraction: Double

    var body: some View {
        GeometryReader { geo in
            ZStack(alignment: .leading) {
                Capsule().fill(.black.opacity(0.5))
                Capsule()
                    .fill(Brand.blue)
                    .frame(width: max(6, geo.size.width * fraction))
            }
        }
        .frame(height: 6)
    }
}

/// The official WordPress logo mark, used across the nav bar and WordCamp cards.
/// Rendered from the bundled vector asset as a template so it tints to its
/// context and stays crisp at any size.
struct WordPressMark: View {
    var tint: Color = .white

    var body: some View {
        Image("WordPressLogo")
            .resizable()
            .renderingMode(.template)
            .aspectRatio(contentMode: .fit)
            .foregroundStyle(tint)
    }
}

/// Shared brand palette. The selected/accent blue is WordPress blue — chosen so
/// it stays distinct from the white tvOS focus highlight.
enum Brand {
    static let blue = Color(red: 0.22, green: 0.34, blue: 0.91)
}
