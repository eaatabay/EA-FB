#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

echo "== EA-FB V6 central playback: offline Node tests =="
node --test source-watchdog/test/playback-success.test.mjs \
  source-watchdog/test/playback-candidate-service.test.mjs \
  source-watchdog/test/trusted-playback-recorder.test.mjs \
  source-watchdog/test/playback-public-surface.test.mjs \
  source-watchdog/test/playback-retention.test.mjs \
  source-watchdog/test/playback-observer-proof.test.mjs \
  source-watchdog/test/playback-observer-service.test.mjs

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
KOTLINC_REAL="$(readlink -f "$(command -v kotlinc)" || command -v kotlinc)"
KOTLIN_HOME_REAL="$(cd "$(dirname "$KOTLINC_REAL")/.." && pwd)"
COROUTINES=""
for candidate in "${KOTLIN_HOME:-}/lib/kotlinx-coroutines-core-jvm.jar" \
  "$KOTLIN_HOME_REAL/lib/kotlinx-coroutines-core-jvm.jar"; do
  if [ -f "$candidate" ]; then COROUTINES="$candidate"; break; fi
done
if [ -z "$COROUTINES" ]; then
  COROUTINES="$(find "$HOME/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlinx/kotlinx-coroutines-core-jvm" \
    -name 'kotlinx-coroutines-core-jvm-1.9.0.jar' -type f -print -quit 2>/dev/null || true)"
fi
if [ -z "$COROUTINES" ]; then
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
echo "== EA-FB V6 fail-closed source gate: offline Kotlin =="
kotlinc -cp "$COROUTINES" EA-FB/src/main/kotlin/com/eafb/Domain.kt \
  EA-FB/src/main/kotlin/com/eafb/SourceEngine.kt \
  EA-FB/src/main/kotlin/com/eafb/PlaybackData.kt \
  EA-FB/src/main/kotlin/com/eafb/PlaybackQuery.kt \
  EA-FB/src/main/kotlin/com/eafb/PlaybackSourceGate.kt \
  core-tests/PlaybackSourceGateTest.kt -include-runtime -d "$TMP/playback-source-gate.jar"
java -cp "$TMP/playback-source-gate.jar:$COROUTINES" com.eafb.PlaybackSourceGateTestKt

echo "== EA-FB V49-safe catalog link bridge: offline Kotlin =="
kotlinc -cp "$COROUTINES" EA-FB/src/main/kotlin/com/eafb/Domain.kt \
  EA-FB/src/main/kotlin/com/eafb/SourceEngine.kt \
  EA-FB/src/main/kotlin/com/eafb/PlaybackData.kt \
  EA-FB/src/main/kotlin/com/eafb/PlaybackQuery.kt \
  EA-FB/src/main/kotlin/com/eafb/PlaybackSourceGate.kt \
  EA-FB/src/main/kotlin/com/eafb/PlaybackSourceHealth.kt \
  EA-FB/src/main/kotlin/com/eafb/PlaybackLinkBridge.kt \
  core-tests/PlaybackLinkBridgeTest.kt -include-runtime -d "$TMP/playback-link-bridge.jar"
java -cp "$TMP/playback-link-bridge.jar:$COROUTINES" com.eafb.PlaybackLinkBridgeTestKt

echo "== EA-FB V6 expiring source health: offline Kotlin =="
kotlinc EA-FB/src/main/kotlin/com/eafb/PlaybackSourceHealth.kt \
  core-tests/PlaybackSourceHealthTest.kt -include-runtime -d "$TMP/playback-health.jar"
java -jar "$TMP/playback-health.jar"

echo "== EA-FB V6 playback resolution handoff: offline Kotlin =="
kotlinc -cp "$COROUTINES" EA-FB/src/main/kotlin/com/eafb/Domain.kt \
  EA-FB/src/main/kotlin/com/eafb/SourceEngine.kt \
  EA-FB/src/main/kotlin/com/eafb/PlaybackData.kt \
  EA-FB/src/main/kotlin/com/eafb/PlaybackQuery.kt \
  EA-FB/src/main/kotlin/com/eafb/PlaybackResolution.kt \
  core-tests/PlaybackResolutionTest.kt -include-runtime -d "$TMP/playback-resolution.jar"
java -cp "$TMP/playback-resolution.jar:$COROUTINES" com.eafb.PlaybackResolutionTestKt

echo "== EA-FB V6 future episode selection: offline Kotlin =="
kotlinc EA-FB/src/main/kotlin/com/eafb/EpisodeAirPolicy.kt \
  core-tests/EpisodeAirPolicyTest.kt -include-runtime -d "$TMP/episode-air-policy.jar"
java -jar "$TMP/episode-air-policy.jar"

echo "== EA-FB V6 detail wiring: offline Node =="
node --test source-watchdog/test/next-air-detail-wiring.test.mjs
echo "== Offline playback checks complete =="
