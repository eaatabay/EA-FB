#!/usr/bin/env bash
set -euo pipefail

# Fetch the upstream LGPL-3.0 CloudStream Gradle plugin at a fixed revision.
# It lives in an ignored directory, isolated from the user's other projects.
readonly REVISION="69fdb8fc4be2815cbdf5ecec7f407727001cc7"
readonly DIR="vendor/cloudstream-gradle"

cd "$(dirname "$0")/.."
command -v git >/dev/null || { echo "git is required" >&2; exit 2; }

if [ ! -d "$DIR/.git" ]; then
  mkdir -p vendor
  if [ -e "$DIR" ]; then
    echo "Unexpected existing $DIR; refusing to overwrite it." >&2
    exit 2
  fi
  echo "Fetching CloudStream Gradle plugin source (pinned upstream revision)..."
  git clone --quiet https://github.com/recloudstream/gradle.git "$DIR"
  git -C "$DIR" checkout --quiet --detach "$REVISION"
fi

# Normalize shell/Git output before comparing the pinned full commit hash.
current="$(git -C "$DIR" rev-parse --verify HEAD^{commit} | tr -d '\\r\\n')"
expected="$(printf '%s' "$REVISION" | tr -d '\\r\\n')"
if [ "$current" != "$expected" ]; then
  echo "CloudStream plugin checkout differs from pinned revision; repairing checkout..."
  git -C "$DIR" fetch --quiet origin "$expected"
  git -C "$DIR" checkout --quiet --detach "$expected"
  current="$(git -C "$DIR" rev-parse --verify HEAD^{commit} | tr -d '\\r\\n')"
fi
if [ "$current" != "$expected" ]; then
  printf 'CloudStream revision check still failed. Actual <%q>, expected <%q>\\n' "$current" "$expected" >&2
  exit 2
fi
echo "CloudStream Gradle source ready: $current"
