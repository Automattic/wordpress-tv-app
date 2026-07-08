#!/bin/bash -eu

# The Apple (tvOS) toolchain — Gemfile, fastlane, the Xcode project and the
# app project lives under `apple/`, while the shared data/domain module is built
# from Gradle. The Ruby/Fastlane commands below must run from `apple/`.
cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && cd apple

echo "--- :ruby: Setting up Ruby tools"
install_gems

echo "--- :test_tube: Test shared data/domain"
bundle exec fastlane test

echo "--- :tv: Build WordPressTV (tvOS Simulator)"
bundle exec fastlane build
