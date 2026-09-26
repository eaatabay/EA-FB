#!/usr/bin/env bash
# Offline, focused v6 catalog verification; no GitHub Actions or deployment.
set -euo pipefail
cd "$(dirname "$0")/.."
command -v node >/dev/null || { echo "Node.js missing" >&2; exit 2; }
command -v kotlinc >/dev/null || { echo "kotlinc missing (use scripts/test-core.sh for bootstrap)" >&2; exit 2; }
command -v java >/dev/null || { echo "Java missing" >&2; exit 2; }
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
node --test \
  source-watchdog/test/catalog-shelves.test.mjs \
  source-watchdog/test/catalog-admin-preview.test.mjs \
  source-watchdog/test/catalog-draft-cli.test.mjs \
  source-watchdog/test/catalog-reserved-ids.test.mjs \
  source-watchdog/test/settings-refresh-wiring.test.mjs \
  source-watchdog/test/detail-ratings-collection-wiring.test.mjs
kotlinc EA-FB/src/main/kotlin/com/eafb/Domain.kt \
  EA-FB/src/main/kotlin/com/eafb/CatalogShelfPolicy.kt \
  core-tests/CatalogShelfPolicyTest.kt -include-runtime -d "$TMP/shelves.jar"
java -jar "$TMP/shelves.jar"
kotlinc EA-FB/src/main/kotlin/com/eafb/CatalogCardPolicy.kt \
  EA-FB/src/main/kotlin/com/eafb/CatalogPagePolicy.kt \
  core-tests/CatalogPagePolicyTest.kt -include-runtime -d "$TMP/pages.jar"
java -jar "$TMP/pages.jar"
kotlinc EA-FB/src/main/kotlin/com/eafb/Domain.kt \
  EA-FB/src/main/kotlin/com/eafb/CatalogSortPolicy.kt \
  core-tests/CatalogSortPolicyTest.kt -include-runtime -d "$TMP/sort.jar"
java -jar "$TMP/sort.jar"
kotlinc EA-FB/src/main/kotlin/com/eafb/FilmCollectionPolicy.kt \
  core-tests/FilmCollectionPolicyTest.kt -include-runtime -d "$TMP/collection.jar"
java -jar "$TMP/collection.jar"
echo "PASS: focused v6 catalog Node + Kotlin verification"
