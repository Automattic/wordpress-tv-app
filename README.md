# WordPress.tv for Apple TV

A native **tvOS** app for browsing and watching [WordPress.tv](https://wordpress.tv) — talks, tutorials, and WordCamp sessions — on the big screen.

This is an early, intentionally small scaffold: it does **one real thing end to end** — fetch the Latest videos from the WordPress.com REST API, show them in a focusable grid, and play one full-screen in AVPlayer. Everything else is stubbed behind a clean seam so it's easy to grow. Contributions welcome. 👋

## Requirements

- macOS with **Xcode 26+** (tvOS 26 SDK)
- [XcodeGen](https://github.com/yonaskolb/XcodeGen) — `brew install xcodegen`

## Run it

The Xcode project is **generated** from [`project.yml`](project.yml) and is not checked in. Generate it, then open and run:

```sh
xcodegen generate
open WordPressTV.xcodeproj
```

Pick an **Apple TV** simulator and hit Run. The app launches, fetches Latest, and you can focus a video and play it.

Run the data-layer tests from the command line (they run on the Mac host — Core is pure Foundation):

```sh
cd WordPressTVCore && swift test
```

## Build & test with Fastlane

[Fastlane](https://fastlane.tools) is the entry point for tooling, and it's what CI (Buildkite) runs. Each lane regenerates the Xcode project first, so you don't need to run `xcodegen` yourself.

```sh
bundle install            # once, installs Fastlane from the Gemfile
bundle exec fastlane test   # run the WordPressTVCore unit tests
bundle exec fastlane build  # build the app for the tvOS Simulator (no signing)
```

## How it's put together

Two modules with a single seam between them:

```
WordPressTVCore/        SPM package — NO UI (never imports SwiftUI / AVKit)
  Domain/               Video, ContentSource, CategoryRef, PlaybackAsset
  Data/                 ContentRepository (protocol + WP.com impl), wire DTOs, mapping
  Sources.swift         the single registered source (wordpress.tv)
WordPressTV/            thin tvOS app target — ALL SwiftUI lives here
  App/                  @main entry + composition root
  Latest/               the grid screen + its view model
  Player/               AVPlayer presentation
```

The seam is [`ContentRepository`](WordPressTVCore/Sources/WordPressTVCore/Data/ContentRepository.swift). The app asks it for domain types and a `PlaybackAsset` — a ready-to-play absolute URL plus metadata. **Core resolves the URL; the app feeds it to AVPlayer.** Core never imports AVKit, so the data layer stays portable and unit-testable without a UI.

`ContentRepository` declares the full content contract; today only `listLatest` and `resolvePlayback` are implemented. `listCategories`, `listByCategory`, and `search` throw `RepositoryError.notImplemented` until their slices land.

### Data flow, in one breath

`listLatest` → `GET /sites/wordpress.tv/posts` → decode wire DTOs → map to `[Video]` (HTML-decoded titles, VideoPress GUID, poster). Tap a video → `resolvePlayback` → `GET /videos/{guid}` → pick the best stream (HLS → DASH → MP4) and build its absolute URL → `PlaybackAsset` → `AVPlayer`.

## How to add UI

The app target owns all SwiftUI. A new screen typically:

1. Takes a `ContentRepository` (and a `ContentSource`) by initializer — never a concrete type.
2. Wraps its data calls in an `@Observable` view model exposing a simple state enum (see [`LatestViewModel`](WordPressTV/Latest/LatestViewModel.swift)).
3. Renders domain types directly.

To develop offline, swap the repository in the [composition root](WordPressTV/App/WordPressTVApp.swift) for a fake conforming to `ContentRepository`.

To add data: implement one of the stubbed methods in [`WPComContentRepository`](WordPressTVCore/Sources/WordPressTVCore/Data/WPComContentRepository.swift), map its wire shape in `Mapping.swift`, and cover it with a fixture-backed test.

## License

[GPL v2](LICENSE), matching the WordPress mobile apps.
