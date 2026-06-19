# WordPress.tv for Google TV

> The **Google TV (Android)** side of the [WordPress.tv monorepo](../README.md). The Apple TV app lives in [`../apple`](../apple).

A native **Android TV / Google TV** app for browsing and watching [WordPress.tv](https://wordpress.tv) on the big screen — the Android counterpart to the [tvOS app](../apple/README.md).

## Status

⏳ **Not scaffolded yet.** This directory is a placeholder that reserves the spot in the monorepo and the CI pipeline. There is no Gradle project here yet.

## Planned shape

A standard Android TV app, mirroring the Apple side's "one real thing end to end" scope (fetch Latest from the WordPress.com REST API → focusable grid → full-screen playback):

```
android/
  settings.gradle.kts
  build.gradle.kts
  gradle/                 wrapper
  gradlew, gradlew.bat
  app/
    build.gradle.kts
    src/main/AndroidManifest.xml   # LEANBACK_LAUNCHER intent + TV banner
    src/main/kotlin/.../MainActivity.kt
```

Likely choices when it's built out: **Kotlin**, **Jetpack Compose for TV** (or Leanback), **ExoPlayer/Media3** for playback, and a Kotlin data layer mirroring `WordPressTVCore`.

## CI

The pipeline already has a Google TV group ([`.buildkite/commands/android/build-and-test.sh`](../.buildkite/commands/android/build-and-test.sh)). Today it's a no-op that just logs a TODO and passes. When this app is scaffolded:

1. Replace that script's body with the real build/test (e.g. `./gradlew :app:assembleDebug :app:testDebugUnitTest`).
2. Move the step onto a Linux agent with the Android SDK (`queue: android`) in [`.buildkite/pipeline.yml`](../.buildkite/pipeline.yml) — Gradle doesn't need the macOS fleet.
