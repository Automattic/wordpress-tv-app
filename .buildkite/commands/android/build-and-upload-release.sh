#!/bin/bash -eu

# Build the signed release APK (Google TV) and upload it to Buildkite artifacts.
# Fetch the upload keystore to the path build.gradle expects;
# UPLOAD_KEYSTORE_PASSWORD comes from the per-pipeline env.

aws s3 cp "s3://a8c-apps-ci-secrets/${BUILDKITE_PIPELINE_SLUG}/wordpress-tv-upload.jks" android/app/wordpress-tv-upload.jks --quiet

echo "--- :robot_face: Building signed release APK"
cd android
./gradlew --no-daemon --stacktrace :app:assembleRelease

echo "--- :arrow_up: Uploading APK to Buildkite artifacts"
buildkite-agent artifact upload "app/build/outputs/apk/release/*.apk"
