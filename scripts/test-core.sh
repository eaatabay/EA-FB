#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
# Codespaces can expose kotlinc through a symlink or wrapper under /workspaces.
# Resolve the real compiler and search its bundled libraries before falling
# back to the version already declared in the project's Gradle dependencies.
KOTLINC="$(command -v kotlinc)"
KOTLINC_REAL="$(readlink -f "$KOTLINC" || printf '%s' "$KOTLINC")"
KOTLIN_REAL_HOME="$(cd "$(dirname "$KOTLINC_REAL")/.." && pwd)"
COROUTINES=""
for candidate in \
  "${KOTLIN_HOME:-}/lib/kotlinx-coroutines-core-jvm.jar" \
  "$KOTLIN_REAL_HOME/lib/kotlinx-coroutines-core-jvm.jar"; do
  if [ -f "$candidate" ]; then COROUTINES="$candidate"; break; fi
done
if [ -z "$COROUTINES" ]; then
  COROUTINES="$(find "$HOME/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlinx/kotlinx-coroutines-core-jvm" \
    -name 'kotlinx-coroutines-core-jvm-1.9.0.jar' -type f -print -quit 2>/dev/null || true)"
fi
if [ -z "$COROUTINES" ]; then
  command -v curl >/dev/null || { echo "Coroutines jar missing and curl unavailable" >&2; exit 2; }
  COROUTINES="$TMP/kotlinx-coroutines-core-jvm-1.9.0.jar"
  curl --fail --silent --show-error --location --retry 2 \
    "https://repo.maven.apache.org/maven2/org/jetbrains/kotlinx/kotlinx-coroutines-core-jvm/1.9.0/kotlinx-coroutines-core-jvm-1.9.0.jar" \
    -o "$COROUTINES"
  jar tf "$COROUTINES" >/dev/null
fi
echo "Kotlin coroutines dependency ready."
DOMAIN="EA-FB/src/main/kotlin/com/eafb/Domain.kt"
kotlinc "$DOMAIN" core-tests/DomainTest.kt -include-runtime -d "$TMP/domain.jar"
java -jar "$TMP/domain.jar"
kotlinc "$DOMAIN" EA-FB/src/main/kotlin/com/eafb/LiveSourcePolicy.kt core-tests/LiveSourcePolicyTest.kt -include-runtime -d "$TMP/live.jar"
java -jar "$TMP/live.jar"
kotlinc -cp "$COROUTINES" "$DOMAIN" EA-FB/src/main/kotlin/com/eafb/SourceEngine.kt core-tests/SourceEngineTest.kt -include-runtime -d "$TMP/source.jar"
java -cp "$TMP/source.jar:$COROUTINES" com.eafb.SourceEngineTestKt

# Home rows: suppress posterless cards and stop at real TMDb page boundaries.
kotlinc EA-FB/src/main/kotlin/com/eafb/CatalogCardPolicy.kt core-tests/CatalogCardPolicyTest.kt -include-runtime -d "$TMP/catalog-cards.jar"
java -jar "$TMP/catalog-cards.jar"
kotlinc EA-FB/src/main/kotlin/com/eafb/EpisodeAirPolicy.kt core-tests/EpisodeAirPolicyTest.kt -include-runtime -d "$TMP/episode-air.jar"
java -jar "$TMP/episode-air.jar"
