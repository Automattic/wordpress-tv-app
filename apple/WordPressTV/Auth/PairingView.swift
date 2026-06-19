import SwiftUI
import UIKit
import CoreImage.CIFilterBuiltins

/// The sign-in screen shown when the user opens a8c.tv without a token.
///
/// Layout mirrors the design handoff: WordPress brand + headline on the left, a
/// white QR card on the right. The TV asks the broker for a pairing session,
/// renders the `qr_url` as a QR, and polls in the background; when the phone
/// finishes signing in the broker hands back the token and we dismiss.
struct PairingView: View {
    @State private var model: PairingViewModel
    let onCancel: () -> Void

    init(
        broker: BrokerClient,
        onAuthorized: @escaping (String) -> Void,
        onCancel: @escaping () -> Void
    ) {
        _model = State(initialValue: PairingViewModel(broker: broker, onAuthorized: onAuthorized))
        self.onCancel = onCancel
    }

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            HStack(alignment: .center, spacing: 140) {
                brand
                qrPanel
            }
            .padding(.horizontal, 160)
        }
        .onExitCommand(perform: onCancel)
        .onAppear { model.start() }
        .onDisappear { model.stop() }
    }

    private var brand: some View {
        VStack(alignment: .leading, spacing: 36) {
            Image(systemName: "w.circle.fill")
                .font(.system(size: 120))
                .foregroundStyle(.white)

            Text("Sign in with your\nWordPress.com account")
                .font(.system(size: 58, weight: .semibold))
                .foregroundStyle(.white)
                .fixedSize(horizontal: false, vertical: true)

            Text("Scan the code with your phone's camera to watch a8c.tv.")
                .font(.title3)
                .foregroundStyle(.white.opacity(0.55))
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    @ViewBuilder
    private var qrPanel: some View {
        VStack(spacing: 28) {
            card
            status
        }
    }

    private var card: some View {
        RoundedRectangle(cornerRadius: 48, style: .continuous)
            .fill(.white)
            .frame(width: 520, height: 520)
            .overlay { cardContent }
            .shadow(color: .black.opacity(0.4), radius: 40, y: 20)
    }

    @ViewBuilder
    private var cardContent: some View {
        switch model.state {
        case .creating:
            ProgressView()
                .controlSize(.large)
                .tint(.black)

        case .showing(let qrURL):
            QRCodeView(string: qrURL)
                .padding(44)
                .overlay {
                    // Brand mark in the center; QR uses high error correction
                    // so the covered modules are recoverable.
                    Image(systemName: "w.circle.fill")
                        .font(.system(size: 74))
                        .foregroundStyle(.black)
                        .padding(10)
                        .background(.white, in: Circle())
                }

        case .success:
            VStack(spacing: 20) {
                Image(systemName: "checkmark.circle.fill")
                    .font(.system(size: 120))
                    .foregroundStyle(.green)
                Text("Signed in")
                    .font(.title2.weight(.semibold))
                    .foregroundStyle(.black)
            }

        case .failed(let message):
            VStack(spacing: 28) {
                Image(systemName: "exclamationmark.triangle.fill")
                    .font(.system(size: 70))
                    .foregroundStyle(.orange)
                Text(message)
                    .font(.title3)
                    .foregroundStyle(.black)
                    .multilineTextAlignment(.center)
                Button("Try again") { model.retry() }
                    .tint(.black)
            }
            .padding(44)
        }
    }

    @ViewBuilder
    private var status: some View {
        switch model.state {
        case .showing:
            HStack(spacing: 14) {
                ProgressView().controlSize(.small).tint(.white)
                Text("Waiting for you to sign in on your phone…")
                    .font(.headline)
                    .foregroundStyle(.white.opacity(0.7))
            }
        case .success:
            Text("Returning to a8c.tv…")
                .font(.headline)
                .foregroundStyle(.white.opacity(0.7))
        default:
            // Keep the column height stable across states.
            Color.clear.frame(height: 30)
        }
    }
}

/// Renders a string as a QR code image. Uses error-correction level "H" so a
/// centered brand mark can overlap without breaking scannability.
struct QRCodeView: View {
    let string: String

    var body: some View {
        if let image = Self.makeImage(from: string) {
            Image(uiImage: image)
                .interpolation(.none) // keep the modules crisp when scaled up
                .resizable()
                .scaledToFit()
        } else {
            Image(systemName: "xmark.octagon")
                .resizable()
                .scaledToFit()
                .foregroundStyle(.red)
        }
    }

    private static func makeImage(from string: String) -> UIImage? {
        let filter = CIFilter.qrCodeGenerator()
        filter.message = Data(string.utf8)
        filter.correctionLevel = "H"
        guard let output = filter.outputImage else { return nil }

        // Upscale the tiny generator output so the CGImage is crisp.
        let scaled = output.transformed(by: CGAffineTransform(scaleX: 14, y: 14))
        let context = CIContext()
        guard let cgImage = context.createCGImage(scaled, from: scaled.extent) else { return nil }
        return UIImage(cgImage: cgImage)
    }
}
