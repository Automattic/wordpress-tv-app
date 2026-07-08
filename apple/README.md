# WordPress.tv for Apple TV

> The **Apple (tvOS)** side of the [WordPress.tv monorepo](../README.md). The Google TV app lives in [`../android`](../android). All commands below are run from this `apple/` directory.

A native **tvOS** app for browsing and watching [WordPress.tv](https://wordpress.tv) — talks, tutorials, and WordCamp sessions — on the big screen.

This is an early, intentionally small scaffold: it does **one real thing end to end** — fetch the Latest videos from the WordPress.com REST API, show them in a focusable grid, and play one full-screen in AVPlayer. Everything else is stubbed behind a clean seam so it's easy to grow. Contributions welcome. 👋

## Requirements

- macOS with **Xcode 26+** (tvOS 26 SDK)

## Run it

Open the project and run — no extra tooling required:

```sh
open WordPressTV.xcodeproj
```

Pick an **Apple TV** simulator and hit Run. The app launches, fetches Latest, and you can focus a video and play it.

Run the shared data/domain tests from the command line:

```sh
cd ../android && ./gradlew :shared:tvosSimulatorArm64Test
```

## Build & test with Fastlane

[Fastlane](https://fastlane.tools) is the entry point for tooling, and it's what CI (Buildkite) runs.

```sh
bundle install            # once, installs Fastlane from the Gemfile
bundle exec fastlane test   # run the shared KMP data/domain tests
bundle exec fastlane build  # build the app for the tvOS Simulator (no signing)
```

## How it's put together

The tvOS target owns SwiftUI and uses a thin Swift adapter over the shared KMP
framework:

```
../shared/              Kotlin Multiplatform data/domain module
  src/commonMain/       ContentRepository, domain models, mapping, WP.com REST logic
  src/tvosMain/         Darwin/tvOS HTTP engine
WordPressTV/            thin tvOS app target — ALL SwiftUI lives here
  App/                  @main entry + composition root
  Shared/SharedCore.swift Swift adapter over WordPressTVSharedCore.framework
  Player/               AVPlayer presentation
```

The seam is `ContentRepository`. The app asks it for domain types and a
`PlaybackAsset` — a ready-to-play absolute URL plus metadata. **The shared KMP
module resolves the URL; the app feeds it to AVPlayer.** The shared module never
imports SwiftUI or AVKit, so the data layer stays portable and unit-testable
without UI.

`ContentRepository` declares the full content contract; today only `listLatest` and `resolvePlayback` are implemented. `listCategories`, `listByCategory`, and `search` throw `RepositoryError.notImplemented` until their slices land.

### Data flow, in one breath

`listLatest` → `GET /sites/wordpress.tv/posts` → decode wire DTOs → map to `[Video]` (HTML-decoded titles, VideoPress GUID, poster). Tap a video → `resolvePlayback` → `GET /videos/{guid}` → pick the best stream (HLS → DASH → MP4) and build its absolute URL → `PlaybackAsset` → `AVPlayer`.

## How to add UI

The app target owns all SwiftUI. A new screen typically:

1. Takes a `ContentRepository` (and a `ContentSource`) by initializer — never a concrete type.
2. Wraps its data calls in an `@Observable` view model exposing a simple state enum (see [`VideoFeedViewModel`](WordPressTV/Shared/VideoFeed.swift)).
3. Renders domain types directly.

To develop offline, swap the repository in the [composition root](WordPressTV/App/WordPressTVApp.swift) for a fake conforming to `ContentRepository`.

To add data: implement it in `shared/src/commonMain/.../WpComContentRepository.kt`,
map its wire shape in `Mapping.kt`, and cover it with a fixture-backed common
test.

## License

[GPL v2](LICENSE), matching the WordPress mobile apps.
