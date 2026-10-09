#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
if ! command -v kotlinc >/dev/null; then
  echo 'Standalone Kotlin compiler absent, use verified bootstrap from scripts/test-core.sh' >&2
  exit 2
fi
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
kotlinc EA-FB/src/main/kotlin/com/eafb/LandFastPlayCodec.kt \
  core-tests/LandFastPlayCodecTest.kt -include-runtime -d "$TMP/land-fixtures.jar"
java -jar "$TMP/land-fixtures.jar"
