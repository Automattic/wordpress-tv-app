import SwiftUI

/// The full-screen "Code for the People" detail, opened from the Home hero. The
/// film's key art anchors the right as a poster (its title treatment carries the
/// name); the synopsis and credit sit on the left. Watching offers a clear choice
/// of where — "Watch on YouTube" or "Watch on your phone" (a QR). We always show
/// both, like the Google TV build. If the YouTube hand-off can't open (no app to
/// handle it), we surface a transient toast — the equivalent of Android's system
/// "you don't have an app that can do this" — rather than diverting to the QR; the
/// QR stays one tap away behind "Watch on your phone". Dismissed with the Menu
/// button. Mirrors the Android `PromoScreen`.
struct PromoView: View {
    let onClose: () -> Void

    /// The QR replaces the two buttons after "Watch on your phone"; Back returns.
    @State private var showQR = false
    /// Shown (briefly) when the YouTube hand-off can't open — see `youTubeErrorToken`.
    @State private var youTubeError = false
    /// Bumped on each failed hand-off so re-tapping restarts the toast's timer.
    @State private var youTubeErrorToken = 0
    @FocusState private var focus: Control?

    /// The two watch controls. The primary one always holds initial focus; in the
    /// QR view the lone Back button is the primary.
    private enum Control: Hashable { case primary, secondary }

    init(onClose: @escaping () -> Void) {
        self.onClose = onClose
    }

    var body: some View {
        ZStack {
            LinearGradient(
                colors: [Color(red: 0.043, green: 0.067, blue: 0.133), Color(red: 0.024, green: 0.024, blue: 0.035)],
                startPoint: .top,
                endPoint: .bottom
            )
            .ignoresSafeArea()

            HStack(alignment: .center, spacing: 80) {
                synopsis
                    .frame(maxWidth: .infinity, alignment: .leading)
                artAndControls
                    .frame(maxWidth: .infinity)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .padding(.horizontal, 100)
            .padding(.vertical, 44)
        }
        .overlay(alignment: .bottom) {
            if youTubeError {
                ToastView(message: "You don't have the YouTube app installed.")
                    .padding(.bottom, 72)
                    .transition(.move(edge: .bottom).combined(with: .opacity))
                    // Auto-dismiss; `id` restarts the timer if it's re-triggered.
                    .task(id: youTubeErrorToken) {
                        try? await Task.sleep(for: .seconds(3))
                        youTubeError = false
                    }
            }
        }
        .animation(.easeOut(duration: 0.25), value: youTubeError)
        .onExitCommand(perform: handleBack)
        .defaultFocus($focus, .primary)
        // The overlay always holds focus (Home is behind it); when the view swaps
        // between the choice and the QR, re-claim it on the new primary control.
        .onChange(of: showQR) { _, _ in focus = .primary }
    }

    // MARK: Left column — synopsis + credit

    /// The film's name lives in the key art, so the copy leads with the tagline
    /// rather than repeating the title.
    private var synopsis: some View {
        VStack(alignment: .leading, spacing: 20) {
            FeaturedBadge()

            Text(CodeForThePeople.tagline)
                .font(.system(size: 58, weight: .bold))
                .foregroundStyle(.white)
                .fixedSize(horizontal: false, vertical: true)

            Text(CodeForThePeople.blurb)
                .font(.system(size: 34))
                .foregroundStyle(.white.opacity(0.6))
                .fixedSize(horizontal: false, vertical: true)

            Text(CodeForThePeople.credit)
                .font(.system(size: 26, weight: .medium))
                .foregroundStyle(.white.opacity(0.5))
                .padding(.top, 4)
        }
    }

    // MARK: Right column — key art + watch controls

    private var artAndControls: some View {
        VStack(spacing: 28) {
            keyArt

            if showQR {
                qrControls
            } else {
                watchChoice
            }
        }
    }

    // A large, poster-like crop of the still: a mild ~1.6:1 side crop of the native
    // 1.85:1, filling the column width so it stands tall like the Android detail.
    // `.fit` on the ratio box lets it shrink to share height with the QR in that
    // state; scaledToFill crops to the box and the border hugs the cropped edges.
    private var keyArt: some View {
        Color.clear
            .aspectRatio(1.6, contentMode: .fit)
            .frame(maxWidth: .infinity)
            .overlay(
                Image("CodeForThePeople")
                    .resizable()
                    .scaledToFill()
            )
            .clipShape(RoundedRectangle(cornerRadius: 18))
            .overlay(
                RoundedRectangle(cornerRadius: 18)
                    .stroke(.white.opacity(0.12), lineWidth: 1)
            )
    }

    /// YouTube app present: pick where to watch.
    private var watchChoice: some View {
        VStack(spacing: 18) {
            Button {
                YouTubeHandoff.open { opened in
                    // No app could open it — flag the toast (don't divert to the QR;
                    // it stays available behind "Watch on your phone").
                    if !opened {
                        youTubeError = true
                        youTubeErrorToken += 1
                    }
                }
            } label: {
                Label("Watch on YouTube", systemImage: "play.fill")
            }
            .buttonStyle(PromoButtonStyle(prominent: true, isFocused: focus == .primary))
            .focused($focus, equals: .primary)

            Button("Watch on your phone") { showQR = true }
                .buttonStyle(PromoButtonStyle(prominent: false, isFocused: focus == .secondary))
                .focused($focus, equals: .secondary)
        }
        .animation(.easeOut(duration: 0.15), value: focus)
    }

    private var qrControls: some View {
        VStack(spacing: 16) {
            Text("Scan with your phone to watch")
                .font(.headline)
                .foregroundStyle(.white.opacity(0.85))

            BrandedQR()

            Text(CodeForThePeople.shareLabel)
                .font(.subheadline)
                .foregroundStyle(.white.opacity(0.5))

            Button("Back", action: handleBack)
                .buttonStyle(PromoButtonStyle(prominent: false, isFocused: focus == .primary))
                .focused($focus, equals: .primary)
                .animation(.easeOut(duration: 0.15), value: focus)
        }
    }

    // MARK: Back handling

    /// Menu/Back and the Back button: step out of the QR view back to the buttons
    /// when showing the QR, otherwise dismiss the whole promo.
    private func handleBack() {
        if showQR {
            showQR = false
        } else {
            onClose()
        }
    }
}

/// The small brand-blue "FEATURED" pill used on the Home hero and this screen.
/// Mirrors the Android `FeaturedBadge`.
struct FeaturedBadge: View {
    var body: some View {
        Text("FEATURED")
            .font(.caption.weight(.bold))
            .tracking(1.8)
            .foregroundStyle(.white)
            .padding(.horizontal, 12)
            .padding(.vertical, 6)
            .background(RoundedRectangle(cornerRadius: 6).fill(Brand.blue))
    }
}

/// A transient, toast-style banner (tvOS has no system toast) surfaced when the
/// YouTube hand-off can't open — the equivalent of Android's "you don't have an app
/// that can do this" system toast. Auto-dismissed by the caller.
private struct ToastView: View {
    let message: String

    var body: some View {
        HStack(spacing: 14) {
            Image(systemName: "exclamationmark.circle.fill")
                .font(.system(size: 26, weight: .semibold))
                .foregroundStyle(.white.opacity(0.9))
            Text(message)
                .font(.system(size: 26, weight: .medium))
                .foregroundStyle(.white)
        }
        .padding(.horizontal, 34)
        .padding(.vertical, 22)
        .background(.regularMaterial, in: Capsule())
        .overlay(Capsule().stroke(.white.opacity(0.14), lineWidth: 1))
        .shadow(color: .black.opacity(0.45), radius: 24, y: 10)
    }
}

/// A white QR card for `CodeForThePeople.shareURL` with a brand play badge at its
/// center. The QR uses error-correction "H" (via `QRCodeView`), so the badge can
/// overlap without breaking scannability.
private struct BrandedQR: View {
    var size: CGFloat = 230

    var body: some View {
        QRCodeView(string: CodeForThePeople.shareURL.absoluteString)
            .padding(size * 0.06)
            .frame(width: size, height: size)
            .background(RoundedRectangle(cornerRadius: size * 0.08).fill(.white))
            .overlay {
                ZStack {
                    Circle().fill(.white).frame(width: size * 0.26, height: size * 0.26)
                    Circle().fill(Brand.blue).frame(width: size * 0.2, height: size * 0.2)
                    Image(systemName: "play.fill")
                        .font(.system(size: size * 0.085, weight: .bold))
                        .foregroundStyle(.white)
                }
            }
    }
}

/// The promo's watch buttons: a full-width pill that is brand-blue when
/// `prominent` and a translucent white otherwise, lifting on focus. A custom style
/// (like the nav-bar tabs) so tvOS doesn't paint its own opaque highlight over ours.
private struct PromoButtonStyle: ButtonStyle {
    var prominent: Bool
    var isFocused: Bool

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.title3.weight(.semibold))
            .foregroundStyle(.white)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 18)
            .padding(.horizontal, 28)
            .background(RoundedRectangle(cornerRadius: 14).fill(fill))
            .overlay(
                RoundedRectangle(cornerRadius: 14)
                    .stroke(.white.opacity(isFocused ? 0.85 : 0), lineWidth: 3)
            )
            .scaleEffect(isFocused ? 1.03 : 1)
            .opacity(configuration.isPressed ? 0.8 : 1)
    }

    private var fill: Color {
        if prominent {
            return isFocused ? Brand.blue : Brand.blue.opacity(0.9)
        }
        return .white.opacity(isFocused ? 0.28 : 0.14)
    }
}
