#!/usr/bin/env bash
set -euo pipefail

export ANDROID_HOME="${ANDROID_HOME:-$HOME/android-sdk}"
export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$ANDROID_HOME}"
export PATH="$HOME/.local/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"

command -v java >/dev/null || { echo "Java not found" >&2; exit 2; }
command -v gradle >/dev/null || { echo "Gradle not found" >&2; exit 2; }
test -f "$ANDROID_HOME/platforms/android-35/android.jar" || {
  echo "Android API 35 is not installed. Run: bash .devcontainer/install-android-sdk.sh" >&2
  exit 2
}

java -version
gradle --version | head -n 8

gradle :EA-FB:make makePluginsJson --no-daemon

python3 scripts/stage-release.py

echo
echo "Build completed. Files staged under dist/:"
ls -lh dist/
echo
echo "Do not publish until EA-FB.cs3 is tested in CloudStream on an Android device."
