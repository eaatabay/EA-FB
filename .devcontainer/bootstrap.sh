#!/usr/bin/env bash
set -euo pipefail

GRADLE_VERSION="8.12"
ANDROID_TOOLS_REV="15859902"
ANDROID_TOOLS_SHA256="4e4c464f145a7512b57d088ac6c278c03c9eea610886b35a5e0804e74eedf583"

mkdir -p "$HOME/.local/bin" "$HOME/.local/opt" "$HOME/android-sdk/cmdline-tools"

if [ ! -x "$HOME/.local/opt/gradle-$GRADLE_VERSION/bin/gradle" ]; then
  curl -fsSL "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip" -o /tmp/gradle.zip
  unzip -q /tmp/gradle.zip -d "$HOME/.local/opt"
  rm -f /tmp/gradle.zip
fi
ln -sf "$HOME/.local/opt/gradle-$GRADLE_VERSION/bin/gradle" "$HOME/.local/bin/gradle"

if [ ! -x "$HOME/android-sdk/cmdline-tools/latest/bin/sdkmanager" ]; then
  curl -fsSL "https://dl.google.com/android/repository/commandlinetools-linux-${ANDROID_TOOLS_REV}_latest.zip" -o /tmp/android-tools.zip
  echo "${ANDROID_TOOLS_SHA256}  /tmp/android-tools.zip" | sha256sum -c -
  rm -rf /tmp/android-tools-unpack
  mkdir -p /tmp/android-tools-unpack
  unzip -q /tmp/android-tools.zip -d /tmp/android-tools-unpack
  rm -rf "$HOME/android-sdk/cmdline-tools/latest"
  mv /tmp/android-tools-unpack/cmdline-tools "$HOME/android-sdk/cmdline-tools/latest"
  rm -rf /tmp/android-tools-unpack /tmp/android-tools.zip
fi

ENV_BLOCK='# EA-FB development environment
export ANDROID_HOME="$HOME/android-sdk"
export ANDROID_SDK_ROOT="$HOME/android-sdk"
export PATH="$HOME/.local/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"'
if ! grep -q "EA-FB development environment" "$HOME/.bashrc"; then
  printf "\n%s\n" "$ENV_BLOCK" >> "$HOME/.bashrc"
fi

export ANDROID_HOME="$HOME/android-sdk"
export ANDROID_SDK_ROOT="$HOME/android-sdk"
export PATH="$HOME/.local/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"

echo
echo "EA-FB Codespace bootstrap ready."
java -version
gradle --version | head -n 8
echo
echo "NEXT: run  bash .devcontainer/install-android-sdk.sh"
echo "That command will show/accept Android SDK licenses and install API 35 build packages."
