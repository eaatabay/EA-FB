#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
KOTLIN_HOME="${KOTLIN_HOME:-$(cd "$(dirname "$(command -v kotlinc)")/.." && pwd)}"
COROUTINES="$KOTLIN_HOME/lib/kotlinx-coroutines-core-jvm.jar"
test -f "$COROUTINES" || { echo "Missing Kotlin coroutines jar: $COROUTINES" >&2; exit 2; }
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
