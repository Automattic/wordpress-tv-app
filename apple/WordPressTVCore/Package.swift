// swift-tools-version: 6.0
import PackageDescription

let package = Package(
    name: "WordPressTVCore",
    // tvOS is the shipping platform; macOS is declared only so the test suite
    // can run on the host via `swift test` (Core is pure Foundation — no UI).
    platforms: [.tvOS("26.0"), .macOS(.v13)],
    products: [
        .library(name: "WordPressTVCore", targets: ["WordPressTVCore"]),
    ],
    targets: [
        .target(name: "WordPressTVCore"),
        .testTarget(
            name: "WordPressTVCoreTests",
            dependencies: ["WordPressTVCore"],
            resources: [.copy("Fixtures")]
        ),
    ]
)
