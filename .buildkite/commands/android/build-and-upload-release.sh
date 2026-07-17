#!/bin/bash -eu

# Build the signed release AAB + APK (Google TV), upload them to Buildkite
# artifacts, and publish the AAB to the Google Play internal testing track (the
# Android analogue of the tvOS TestFlight step). Fetch the upload keystore to the
# path build.gradle expects; UPLOAD_KEYSTORE_PASSWORD comes from the per-pipeline
# env. versionCode tracks the Buildkite build number (mirrors the iOS build number).

aws s3 cp "s3://a8c-apps-ci-secrets/${BUILDKITE_PIPELINE_SLUG}/wordpress-tv-upload.jks" android/app/wordpress-tv-upload.jks --quiet

# Fetch the Google Play service-account JSON that fastlane `supply` authenticates
# with. Same bucket/prefix as the keystore; the Fastfile reads it from android/.
aws s3 cp "s3://a8c-apps-ci-secrets/${BUILDKITE_PIPELINE_SLUG}/play-service-account.json" android/play-service-account.json --quiet

# Fetch tags so Gradle can derive the versionName from the latest git tag.
# Buildkite's default checkout doesn't always bring them; without this the
# version would fall back to 0.0.1 even when a release tag exists. Non-fatal.
git fetch --tags --force || echo "warning: could not fetch tags; versionName will fall back to 0.0.1"

echo "--- :robot_face: Building signed release AAB + APK"
cd android
./gradlew --no-daemon --stacktrace -PversionCode="$BUILDKITE_BUILD_NUMBER" :app:bundleRelease :app:assembleRelease

echo "--- :arrow_up: Uploading to Buildkite artifacts"
buildkite-agent artifact upload "app/build/outputs/bundle/release/*.aab"
buildkite-agent artifact upload "app/build/outputs/apk/release/*.apk"

# Publish the signed AAB to the Google Play internal testing track. `install_gems`
# and `bundle` resolve android/Gemfile and android/fastlane/ (we're already in android/).
echo "--- :rubygems: Setting up Gems"
install_gems

echo "--- :android: Uploading AAB to Google Play (internal track)"
bundle exec fastlane android upload_to_play_internal
