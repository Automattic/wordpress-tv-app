#!/bin/bash -eu

# The Apple (tvOS) toolchain lives under `apple/` in this monorepo; run from there.
cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && cd apple

echo "--- :java: Setting up JDK 21"
brew install --cask temurin@21
export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
export PATH="$JAVA_HOME/bin:$PATH"
java --version

echo "--- :ruby: Setting up Ruby tools"
install_gems

# Assumes the signing/upload secrets are already in the environment on CI
# (MATCH_PASSWORD, MATCH_S3_ACCESS_KEY/SECRET, APP_STORE_CONNECT_API_KEY_*).
# The build number comes from $BUILDKITE_BUILD_NUMBER (read inside the lane).
echo "--- :rocket: Build WordPressTV and upload to TestFlight"
bundle exec fastlane build_and_upload_to_testflight
