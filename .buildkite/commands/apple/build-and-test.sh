#!/bin/bash -eu

# The Apple (tvOS) toolchain — Gemfile, fastlane, the Xcode project and the
# WordPressTVCore SPM package — lives under `apple/` in this monorepo, so the
# Ruby/Fastlane commands below must run from there.
cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && cd apple

echo "--- :ruby: Setting up Ruby tools"
install_gems

echo "--- :test_tube: Test WordPressTVCore"
bundle exec fastlane test

echo "--- :tv: Build WordPressTV (tvOS Simulator)"
bundle exec fastlane build
