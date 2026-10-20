#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
export PYTHONDONTWRITEBYTECODE=1
baseline_dir="$(mktemp -d)"
trap 'rm -rf "$baseline_dir"' EXIT
git archive 16396702470eb1f81cc6dac4360e6b0ff84927d1 | tar -x -C "$baseline_dir"
cp core-tests/clean/UniversalAdaptersTest.kt "$baseline_dir/core-tests/clean/UniversalAdaptersTest.kt"
# Run the identical discovery cases on unmodified V83 runtime files, in an archive fixture (no worktree).
python3 - "$baseline_dir" <<'PY'
from pathlib import Path
import sys
p=Path(sys.argv[1])/'scripts/test-bronze-nl-land.sh'
s=p.read_text().replace('  core-tests/clean/Clean*Test.kt','  core-tests/clean/UniversalAdaptersTest.kt core-tests/clean/Clean*Test.kt')
a=s.index('for suite in ');b=s.index('; do',a)
s=s[:a]+'for suite in UniversalAdaptersTest DomainTest SourceEngineTest PlaybackDataTest PlaybackQueryTest DiziYouEpisodeParserTest PlaybackSourceHealthTest CleanSettingsTest CleanEngineTest CleanAdaptersTest BronzeAdaptersTest BronzeFilmSearchTest BronzeBridgeTest'+s[b:]
s=s.replace('java -cp ', 'java -Duniversal.baseline=true -cp ')
p.write_text(s)
PY
(cd "$baseline_dir" && bash scripts/test-bronze-nl-land.sh)
(cd "$baseline_dir" && GIT_DIR="$OLDPWD/.git" GIT_WORK_TREE="$baseline_dir" python3 -m unittest discover -s tests -p 'test_v83*.py' -v)
