#!/usr/bin/env bash
set -euo pipefail

export ANDROID_HOME="$HOME/android-sdk"
export ANDROID_SDK_ROOT="$HOME/android-sdk"
export PATH="$HOME/.local/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"

if [ ! -x "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" ]; then
  echo "Android command-line tools are missing. Rebuild the Codespace or run .devcontainer/bootstrap.sh first." >&2
  exit 1
fi

echo "Android SDK licenses will be displayed by sdkmanager."
echo "Review them and answer the prompts to continue."
sdkmanager --licenses

sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"

echo
echo "Android SDK API 35 is installed."
echo "Now run: bash scripts/build-codespace.sh"
