#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

echo "== EA-FB V6 central playback: offline Node tests =="
node --test source-watchdog/test/playback-success.test.mjs \
  source-watchdog/test/playback-candidate-service.test.mjs \
  source-watchdog/test/trusted-playback-recorder.test.mjs \
  source-watchdog/test/playback-public-surface.test.mjs \
  source-watchdog/test/playback-retention.test.mjs

echo "== EA-FB V6 central playback: isolated SQLite migration tests =="
python3 -m unittest discover -s source-watchdog/tests \
  -p 'test_playback_success_migration.py' -v

echo "== EA-FB V6 stable playback identities: offline Kotlin =="
if ! command -v kotlinc >/dev/null 2>&1; then
  echo "kotlinc unavailable: Kotlin identity test not run" >&2
  exit 2
fi
if ! command -v java >/dev/null 2>&1; then
  echo "java unavailable: Kotlin identity test not run" >&2
  exit 2
fi
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
kotlinc EA-FB/src/main/kotlin/com/eafb/PlaybackData.kt \
  core-tests/PlaybackDataTest.kt -include-runtime -d "$TMP/playback-data.jar"
java -jar "$TMP/playback-data.jar"

# The exact episode query also depends on the existing coroutine-based engine.
KOTLIN_HOME_REAL="$(cd "$(dirname "$(command -v kotlinc)")/.." && pwd)"
COROUTINES="$KOTLIN_HOME_REAL/lib/kotlinx-coroutines-core-jvm.jar"
if [ ! -f "$COROUTINES" ]; then
  echo "Coroutines jar unavailable: exact episode query test not run" >&2
  exit 2
fi
kotlinc -cp "$COROUTINES" EA-FB/src/main/kotlin/com/eafb/Domain.kt \
  EA-FB/src/main/kotlin/com/eafb/SourceEngine.kt \
  EA-FB/src/main/kotlin/com/eafb/PlaybackData.kt \
  EA-FB/src/main/kotlin/com/eafb/PlaybackQuery.kt \
  core-tests/PlaybackQueryTest.kt -include-runtime -d "$TMP/playback-query.jar"
java -cp "$TMP/playback-query.jar:$COROUTINES" com.eafb.PlaybackQueryTestKt
# Exact-episode source filtering must reject wrong/missing TMDb IDs.
kotlinc -cp "$COROUTINES" EA-FB/src/main/kotlin/com/eafb/Domain.kt \
  EA-FB/src/main/kotlin/com/eafb/SourceEngine.kt \
  core-tests/SourceEngineTest.kt -include-runtime -d "$TMP/playback-engine.jar"
java -cp "$TMP/playback-engine.jar:$COROUTINES" com.eafb.SourceEngineTestKt
echo "== Offline playback checks complete =="
