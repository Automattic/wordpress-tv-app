import SwiftUI

/// The Home hero: a full-width featured banner for the "Code for the People"
/// documentary, above the rails. A cinematic still bleeds full-height to the right
/// edge and dissolves into `navy` on the left, so our own title/tagline read over
/// the dark side. NOTE: the intended art is a title-free still — the film's title
/// lives in the copy here, not baked into the image. The whole card is one
/// focusable control that opens `PromoView`. Mirrors the Android
/// `FeaturedPromoBanner`.
struct FeaturedPromoBanner: View {
    let onOpen: () -> Void

    @FocusState private var focused: Bool

    /// The dark, faintly-branded ground the still fades into on its left.
    private static let navy = Color(red: 0.043, green: 0.086, blue: 0.212) // #0B1636
    // Tall enough that the ~1.85:1 still isn't over-cropped vertically — keeps the
    // banner near the Android hero's ~4.5:1 proportions so the title art reads.
    private static let height: CGFloat = 390

    var body: some View {
        Button(action: onOpen) { banner }
            .buttonStyle(PromoBannerButtonStyle(isFocused: focused))
            .focused($focused)
    }

    private var banner: some View {
        GeometryReader { geo in
            ZStack(alignment: .leading) {
                Self.navy

                // The still bleeds full-height to the right edge...
                Image("CodeForThePeople")
                    .resizable()
                    .scaledToFill()
                    .frame(width: geo.size.width * 0.64, height: geo.size.height)
                    .clipped()
                    .frame(maxWidth: .infinity, alignment: .trailing)

                // ...and dissolves into the navy on the left so the copy reads over it.
                LinearGradient(
                    stops: [
                        .init(color: Self.navy, location: 0.0),
                        .init(color: Self.navy, location: 0.36),
                        .init(color: .clear, location: 0.66),
                    ],
                    startPoint: .leading,
                    endPoint: .trailing
                )

                // The film's title ("Code for the People") lives in the art on the
                // right, so the copy here leads with the tagline and the Watch cue.
                copy
                    .frame(width: geo.size.width * 0.5, alignment: .leading)
                    .padding(.leading, 48)
            }
            .frame(width: geo.size.width, height: geo.size.height)
        }
        .frame(height: Self.height)
        .clipShape(RoundedRectangle(cornerRadius: 20))
    }

    private var copy: some View {
        VStack(alignment: .leading, spacing: 16) {
            FeaturedBadge()

            Text(CodeForThePeople.tagline)
                .font(.title3.weight(.medium))
                .foregroundStyle(.white.opacity(0.82))
                .fixedSize(horizontal: false, vertical: true)

            HStack(spacing: 14) {
                PlayBadge()
                Text("Watch the documentary")
                    .font(.headline)
                    .foregroundStyle(.white)
            }
        }
    }
}

/// A small circular play affordance next to the call to action.
private struct PlayBadge: View {
    var body: some View {
        Image(systemName: "play.fill")
            .font(.system(size: 15, weight: .bold))
            .foregroundStyle(.white)
            .frame(width: 40, height: 40)
            .background(Circle().fill(.white.opacity(0.16)))
    }
}

/// The banner's focus look: a subtle lift, ring, and shadow when the tvOS focus
/// engine highlights it — matching the `.card` feel without the poster crop. Driven
/// by an explicit `isFocused` (from the view's `@FocusState`), the same pattern the
/// nav-bar tab styles use.
private struct PromoBannerButtonStyle: ButtonStyle {
    var isFocused: Bool

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .overlay(
                RoundedRectangle(cornerRadius: 20)
                    .stroke(.white.opacity(isFocused ? 0.9 : 0), lineWidth: 4)
            )
            .scaleEffect(isFocused ? 1.02 : 1)
            .shadow(color: .black.opacity(isFocused ? 0.5 : 0), radius: 24, y: 12)
            .opacity(configuration.isPressed ? 0.85 : 1)
            .animation(.easeOut(duration: 0.18), value: isFocused)
    }
}
