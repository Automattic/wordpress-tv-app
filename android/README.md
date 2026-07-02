# WordPress.tv for Google TV

> The **Google TV (Android)** side of the [WordPress.tv monorepo](../README.md). The Apple TV app lives in [`../apple`](../apple).

A native **Android TV / Google TV** app for browsing and watching [WordPress.tv](https://wordpress.tv) on the big screen — the Android counterpart to the [tvOS app](../apple/README.md), built for feature parity with it.

## Status

✅ **Builds, tests, and produces a debug APK.** Feature parity with the tvOS app:

- Intro **splash** clip on cold launch (the same `Intro.mp4` the Apple app ships).
- Public **WordPress.tv** Latest grid → full-screen **playback** (HLS via Media3).
- **QR sign-in** with WordPress.com via the [pairing broker](../broker) — scan with your phone, no password on the TV.
- The private **a8c.tv** source for Automatticians: authenticated reads + VideoPress
  `metadata_token`-stamped posters and progressive playback. Non-a12s users are
  signed in to public WordPress.tv only and never see a8c.tv.
- **Account** avatar + log out, **session persistence** across launches.

## Shape — two modules, one seam

Mirrors the Apple side (`WordPressTV` app target + `WordPressTVCore` package):

```
android/
  settings.gradle.kts · build.gradle.kts · gradle/libs.versions.toml   version catalog
  core/      :core — UI-free data layer (the WordPressTVCore counterpart). Plain
             Kotlin/JVM: no Android, no Compose. Resolves URLs + maps WP.com REST
             JSON to domain types. Unit-tested on the JVM.
    domain/  ContentSource, Video, PlaybackAsset, Account, CategoryRef
    data/    ContentRepository (+ WpComContentRepository), Mapping, Html, wire DTOs,
             AuthTokenProvider
  app/       :app — all the UI. Jetpack Compose for TV (androidx.tv.material3),
             Media3/ExoPlayer playback, Coil posters, ZXing QR, DataStore session.
    MainActivity · WordPressTvApp (composition root)
    splash/ · root/ (source bar) · latest/ (grid) · player/ · auth/ (broker + pairing)
```

The seam is **`ContentRepository`**: the app calls it and gets domain types + a
`PlaybackAsset` (a ready-to-play URL + metadata). **`:core` resolves the URL; the
app feeds it to ExoPlayer.** `:core` never imports Compose or Media3.

## Build & run

```sh
# Unit tests (the :core data layer) + debug APK
./gradlew :core:test :app:assembleDebug

# Install on a running Google TV emulator / device
./gradlew :app:installDebug
```

Open `android/` in Android Studio to run it on an Android TV emulator (create one
via Device Manager → TV). Requires JDK 17+ and the Android SDK (`ANDROID_HOME`).

### Broker location

The QR sign-in talks to the pairing broker. The default is the `/pairing` routes
on wordpress.tv (`https://wordpress.tv/pairing`), set as `BROKER_BASE_URL` in
[`app/build.gradle.kts`](app/build.gradle.kts) — mirrors the iOS `BrokerBaseURL`
default. Point it at a local tunnel to develop against a broker on your machine
(see [`../broker/README.md`](../broker/README.md)).

## CI

[`.buildkite/commands/android/build-and-test.sh`](../.buildkite/commands/android/build-and-test.sh)
runs `:core:test` + `:app:assembleDebug`. It expects a Linux agent with the
Android SDK (`queue: android` in [`../.buildkite/pipeline.yml`](../.buildkite/pipeline.yml)) —
Gradle doesn't need the macOS fleet.
