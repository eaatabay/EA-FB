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

current="$(git -C "$DIR" rev-parse HEAD)"
if [ "$current" != "$REVISION" ]; then
  echo "Wrong CloudStream plugin revision: $current (expected $REVISION)" >&2
  exit 2
fi

echo "CloudStream Gradle source ready: $current"
