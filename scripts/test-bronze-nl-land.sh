#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
export PYTHONDONTWRITEBYTECODE=1
command -v kotlinc >/dev/null || { echo 'Kotlin compiler required' >&2; exit 2; }
task_tmp="$(mktemp -d)"
trap 'rm -rf "$task_tmp"' EXIT
compiler="$(readlink -f "$(command -v kotlinc)")"
compiler_root="$(dirname "$(dirname "$compiler")")"
coroutines="$compiler_root/lib/kotlinx-coroutines-core-jvm.jar"
test_cache="${CLEAN_TEST_CACHE:-$task_tmp/libs}"
mkdir -p "$test_cache"
fetch_verified() {
  local file="$1" url="$2" digest="$3"
  if [ ! -f "$test_cache/$file" ]; then
    curl --fail --silent --show-error --location "$url" -o "$test_cache/$file"
  fi
  printf '%s  %s\n' "$digest" "$test_cache/$file" | sha256sum -c -
}
fetch_verified jsoup-1.18.3.jar https://repo.maven.apache.org/maven2/org/jsoup/jsoup/1.18.3/jsoup-1.18.3.jar 5be1ccd3228ae5fd6eed1bd6d827bac2bc65b91c20e9957d16ea65f739f15302
fetch_verified json-20240303.jar https://repo.maven.apache.org/maven2/org/json/json/20240303/json-20240303.jar 3cf6cd6892e32e2b4c1c39e0f52f5248a2f5b37646fdfbb79a66b46b618414ed
test -f "$coroutines"
classpath="$coroutines:$test_cache/jsoup-1.18.3.jar:$test_cache/json-20240303.jar"
source_dir=EA-FB/src/main/kotlin/com/eafb
sources=()
for name in Domain CatalogSortPolicy CleanTestIdentity EASettings SourceEngine SourceSearchTitles HlsPlaylistInfo DiziBoxAdapter DiziYouAdapter BronzeApiAdapter DiziYouEpisodeParser PlaybackData PlaybackQuery PlaybackSourceGate PlaybackSourceHealth PlaybackLinkPreferences PlaybackLinkBridge; do
  sources+=("$source_dir/$name.kt")
done
kotlinc -cp "$classpath" "${sources[@]}" \
  core-tests/clean/android/content/Context.kt core-tests/clean/android/util/*.kt \
  core-tests/clean/com/lagradost/cloudstream3/Fixtures.kt \
  core-tests/clean/com/lagradost/cloudstream3/utils/Extractors.kt \
  core-tests/clean/Clean*Test.kt core-tests/clean/BronzeAdaptersTest.kt \
  core-tests/DomainTest.kt core-tests/SourceEngineTest.kt core-tests/PlaybackDataTest.kt \
  core-tests/PlaybackQueryTest.kt core-tests/DiziYouEpisodeParserTest.kt \
  core-tests/PlaybackSourceHealthTest.kt \
  -include-runtime -d "$task_tmp/clean-tests.jar"
for suite in DomainTest SourceEngineTest PlaybackDataTest PlaybackQueryTest DiziYouEpisodeParserTest PlaybackSourceHealthTest CleanSettingsTest CleanEngineTest CleanAdaptersTest BronzeAdaptersTest; do
  java -cp "$task_tmp/clean-tests.jar:$classpath" "com.eafb.${suite}Kt"
done

