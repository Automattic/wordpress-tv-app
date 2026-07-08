import SwiftUI

// The card vocabulary shared across Home, category grids, and search: a poster
// primitive plus the three card shapes in the design — landscape video card,
// portrait flagship card, and the wide Continue Watching card with a resume bar.

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

/// Portrait flagship-WordCamp card. A real cover image from the camp's latest
/// video sits behind a brand-tinted scrim, with the WordPress mark and event
/// name over it — a populated poster rather than a flat colour. Falls back to
/// the brand gradient until (or if) the cover resolves. Opens the camp's videos.
struct PortraitCampCard: View {
    let camp: FlagshipCamp
    var width: CGFloat = 300
    /// Resolves the cover image (the camp's newest video poster). Lazy so only
    /// on-screen cards fetch.
    let resolveCover: (FlagshipCamp) async -> URL?
    let onSelect: () -> Void

    @State private var coverURL: URL?

    /// The event's place, e.g. "Asia" — the flagship name minus the shared
    /// "WordCamp" prefix, so it reads as poster art rather than a wrapped label.
    private var place: String {
        camp.title.replacingOccurrences(of: "WordCamp ", with: "")
    }

    private var height: CGFloat { width * 1.5 }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Button(action: onSelect) {
                ZStack(alignment: .bottomLeading) {
                    // Brand gradient — the base, and the fallback if no cover.
                    LinearGradient(colors: camp.colors, startPoint: .top, endPoint: .bottom)

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
                            camp.colors.last?.opacity(0.98) ?? .black.opacity(0.9),
                        ],
                        startPoint: .top,
                        endPoint: .bottom
                    )

                    WordPressMark()
                        .frame(width: 40, height: 40)
                        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
                        .padding(24)

                    VStack(alignment: .leading, spacing: 2) {
                        Text("WordCamp")
                            .font(.title3.weight(.semibold))
                            .foregroundStyle(.white.opacity(0.9))
                        Text(place)
                            .font(.system(size: 40, weight: .heavy))
                            .foregroundStyle(.white)
                            .minimumScaleFactor(0.6)
                            .lineLimit(2)
                    }
                    .padding(24)
                }
                .frame(width: width, height: height)
                .clipShape(RoundedRectangle(cornerRadius: 20))
            }
            .buttonStyle(.card)

            Text(camp.title)
                .font(.callout.weight(.medium))
                .foregroundStyle(.white)
                .lineLimit(1)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
        .frame(width: width)
        .task { coverURL = await resolveCover(camp) }
    }
}

/// Wide Continue Watching card: poster with a resume bar showing how far in the
/// viewer got. Tapping resumes from `progress.positionSeconds`.
struct ContinueWatchingCard: View {
    let progress: WatchProgress
    var width: CGFloat = 440
    let onSelect: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Button(action: onSelect) {
                PosterImage(url: progress.posterURL)
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

/// The official WordPress logo mark, used across the nav bar and flagship cards.
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
