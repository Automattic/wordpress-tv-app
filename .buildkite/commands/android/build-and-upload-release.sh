#!/bin/bash -eu

aws s3 cp "s3://a8c-apps-ci-secrets/${BUILDKITE_PIPELINE_SLUG}/wordpress-tv-upload.jks" android/app/wordpress-tv-upload.jks --quiet
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

echo "--- :rubygems: Setting up Gems"
install_gems

echo "--- :android: Uploading AAB to Google Play (internal track)"
bundle exec fastlane android upload_to_play_internal
