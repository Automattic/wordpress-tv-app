#!/bin/bash -eu

# Build the signed release AAB + APK (Google TV) and upload them to Buildkite
# artifacts. Fetch the upload keystore to the path build.gradle expects;
# UPLOAD_KEYSTORE_PASSWORD comes from the per-pipeline env. versionCode tracks
# the Buildkite build number (mirrors the iOS build number).

aws s3 cp "s3://a8c-apps-ci-secrets/${BUILDKITE_PIPELINE_SLUG}/wordpress-tv-upload.jks" android/app/wordpress-tv-upload.jks --quiet

echo "--- :robot_face: Building signed release AAB + APK"
cd android
./gradlew --no-daemon --stacktrace -PversionCode="$BUILDKITE_BUILD_NUMBER" :app:bundleRelease :app:assembleRelease

echo "--- :arrow_up: Uploading to Buildkite artifacts"
buildkite-agent artifact upload "app/build/outputs/bundle/release/*.aab"
buildkite-agent artifact upload "app/build/outputs/apk/release/*.apk"
