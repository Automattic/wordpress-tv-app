#!/bin/bash -eu

cd android

echo "--- :ruby: Ruby version"
ruby_version=$(ruby -v)
echo "$ruby_version"
if [[ "$ruby_version" != "ruby 3.4.13 "* ]]; then
  echo "^^^ +++ Expected Ruby 3.4.13, got: $ruby_version"
  exit 1
fi

echo "--- :rubygems: Setting up Gems"
install_gems

echo "--- :android: Running the release lane without an AAB"
set +e
output=$(bundle exec fastlane android upload_to_play_internal 2>&1)
status=$?
set -e
echo "$output"

if [[ $status -eq 0 ]]; then
  echo "^^^ +++ The lane succeeded, but it should have stopped at the missing AAB"
  exit 1
fi
if ! grep -q "Could not find aab file" <<< "$output"; then
  echo "^^^ +++ The lane failed, but not at the missing AAB check"
  exit 1
fi
if ! grep -q "Ruby 3.4.13" <<< "$output"; then
  echo "^^^ +++ fastlane did not report running on Ruby 3.4.13"
  exit 1
fi

echo "+++ :white_check_mark: Release lane loads on Ruby 3.4.13 and stops before publishing"
