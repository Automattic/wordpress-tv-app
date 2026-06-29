#!/bin/bash -eu

# Build & test the Google TV (Android) app.
#
# Runs the unit tests for the UI-free :core data layer and assembles the debug
# APK. Needs a Linux agent with the Android SDK (ANDROID_HOME/ANDROID_SDK_ROOT
# set) — Gradle does not need, and should not occupy, the macOS build fleet.

echo "--- :robot_face: Building & testing the Google TV (Android) app"

cd android
./gradlew --no-daemon --stacktrace :core:test :app:assembleDebug
