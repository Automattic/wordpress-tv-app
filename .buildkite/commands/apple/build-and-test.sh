#!/bin/bash -eu

# The Apple (tvOS) toolchain — Gemfile, fastlane, the Xcode project and the
# app project lives under `apple/`, while the shared data/domain module is built
# from Gradle. The Ruby/Fastlane commands below must run from `apple/`.
cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && cd apple

echo "--- :java: Setting up JDK 21"
brew install --cask temurin@21
export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
export PATH="$JAVA_HOME/bin:$PATH"
java --version

echo "--- :ruby: Setting up Ruby tools"
install_gems

echo "--- :test_tube: Test shared data/domain"
bundle exec fastlane test

echo "--- :tv: Build WordPressTV (tvOS Simulator)"
bundle exec fastlane build
