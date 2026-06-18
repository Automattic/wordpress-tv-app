#!/bin/bash -eu

# XcodeGen generates the (uncommitted) Xcode project; Fastlane's `before_all`
# regenerates it on every lane, so we only need the tool on PATH here.
echo "--- :package: Install XcodeGen"
if ! command -v xcodegen >/dev/null 2>&1; then
  brew install xcodegen
fi
xcodegen --version

echo "--- :ruby: Setting up Ruby tools"
install_gems

echo "--- :test_tube: Test WordPressTVCore"
bundle exec fastlane test

echo "--- :tv: Build WordPressTV (tvOS Simulator)"
bundle exec fastlane build
