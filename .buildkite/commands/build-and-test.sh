#!/bin/bash -eu

echo "--- :ruby: Setting up Ruby tools"
install_gems

echo "--- :test_tube: Test WordPressTVCore"
bundle exec fastlane test

echo "--- :tv: Build WordPressTV (tvOS Simulator)"
bundle exec fastlane build
