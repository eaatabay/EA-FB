#!/usr/bin/env bash
# Compatibility wrapper: NEVER mutate an existing local D1 database.
# The release validation creates a unique temporary D1 and destroys it.
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ "$(git branch --show-current)" != "feature/detail-dual-ratings-v6" ]]; then
  echo "Refusing local D1 test outside the v6 feature branch" >&2
  exit 2
fi
bash dev/run-release-validation.sh
