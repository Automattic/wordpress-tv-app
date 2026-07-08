# WordPress.tv for the big screen

A monorepo for the native **WordPress.tv** TV apps — browse and watch [WordPress.tv](https://wordpress.tv) talks, tutorials, and WordCamp sessions on the couch.

Two platforms, one repo:

```
apple/      tvOS app (Apple TV) — Swift / SwiftUI, builds with Xcode + Fastlane
  WordPressTV/          thin tvOS app target (all SwiftUI lives here)
  WordPressTV.xcodeproj
  fastlane/             build / test / TestFlight lanes
  Gemfile               Ruby tooling (Fastlane)

android/    Google TV app (Android) — Kotlin / Compose for TV / Media3
  app/                  :app — all the Compose UI (splash, grid, player, QR sign-in)

shared/     Kotlin Multiplatform data/domain module used by both apps
  src/commonMain/       ContentRepository, domain models, mapping, WP.com REST logic
  src/androidMain/      Android HTTP engine
  src/tvosMain/         Darwin/tvOS HTTP engine

.buildkite/ CI for both platforms
  commands/apple/       tvOS build/test + TestFlight
  commands/android/     Google TV build/test (placeholder for now)
  pipeline.yml          one pipeline, one group per platform
```

## Platforms

| Platform | Path | Status | Getting started |
| --- | --- | --- | --- |
| Apple TV (tvOS) | [`apple/`](apple) | ✅ Builds, tests, ships to TestFlight | [apple/README.md](apple/README.md) |
| Google TV (Android) | [`android/`](android) | ✅ Builds, tests, assembles a debug APK | [android/README.md](android/README.md) |

Each platform owns its UI under its directory: open `apple/` in Xcode, open `android/` in Android Studio. Data/domain code lives once in [`shared/`](shared) and is consumed as an Android library variant by Google TV and as a generated static framework by tvOS.

## CI

Buildkite runs [`.buildkite/pipeline.yml`](.buildkite/pipeline.yml), which has one group per platform. The Apple group runs shared KMP tests plus a tvOS simulator build on the macOS fleet; the Android group runs shared Android unit tests plus the debug APK on a Linux agent with the Android SDK. See the per-platform READMEs for the lanes/tasks each group invokes.

## License

[GPL v2](LICENSE), matching the WordPress mobile apps.
