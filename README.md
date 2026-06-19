# WordPress.tv for the big screen

A monorepo for the native **WordPress.tv** TV apps — browse and watch [WordPress.tv](https://wordpress.tv) talks, tutorials, and WordCamp sessions on the couch.

Two platforms, one repo:

```
apple/      tvOS app (Apple TV) — Swift / SwiftUI, builds with Xcode + Fastlane
  WordPressTV/          thin tvOS app target (all SwiftUI lives here)
  WordPressTVCore/      SPM package — portable, UI-free data layer
  WordPressTV.xcodeproj
  fastlane/             build / test / TestFlight lanes
  Gemfile               Ruby tooling (Fastlane)

android/    Google TV app (Android) — Kotlin / Gradle  ⏳ scaffold pending

.buildkite/ CI for both platforms
  commands/apple/       tvOS build/test + TestFlight
  commands/android/     Google TV build/test (placeholder for now)
  pipeline.yml          one pipeline, one group per platform
```

## Platforms

| Platform | Path | Status | Getting started |
| --- | --- | --- | --- |
| Apple TV (tvOS) | [`apple/`](apple) | ✅ Builds, tests, ships to TestFlight | [apple/README.md](apple/README.md) |
| Google TV (Android) | [`android/`](android) | ⏳ Placeholder — not scaffolded yet | [android/README.md](android/README.md) |

Each platform is self-contained under its directory: open `apple/` in Xcode, open `android/` in Android Studio. There's no shared code across the two yet — they're independent apps that happen to live in one repo so CI, issues, and releases stay in one place.

## CI

Buildkite runs [`.buildkite/pipeline.yml`](.buildkite/pipeline.yml), which has one group per platform. The Apple group runs on the macOS fleet (Xcode + Fastlane); the Android group is a placeholder that will move to a Linux agent once the Gradle project lands. See the per-platform READMEs for the lanes/tasks each group invokes.

## License

[GPL v2](LICENSE), matching the WordPress mobile apps.
