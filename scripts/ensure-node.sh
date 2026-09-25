#!/usr/bin/env bash
set -euo pipefail

# Minimal user-local Node.js bootstrap for the existing Ubuntu Codespace.
# No sudo, no change to the Windows PC, no global packages or repo credentials.
export PATH="$HOME/.local/bin:$PATH"
if command -v node >/dev/null && command -v npm >/dev/null; then
  current="$(node --version)"
  major="${current#v}"
  major="${major%%.*}"
  if [[ "$major" =~ ^[0-9]+$ ]] && (( major >= 20 )); then
    echo "Node.js already available: $current"
    exit 0
  fi
fi

readonly VERSION="22.22.0"
case "$(uname -m)" in
  x86_64|amd64) ARCH="x64" ;;
  aarch64|arm64) ARCH="arm64" ;;
  *) echo "Unsupported Codespace architecture: $(uname -m)" >&2; exit 2 ;;
esac
readonly PACKAGE="node-v${VERSION}-linux-${ARCH}.tar.xz"
readonly BASE="https://nodejs.org/dist/v${VERSION}"
readonly DIR="$HOME/.local/opt/node-v${VERSION}"
mkdir -p "$HOME/.local/bin" "$HOME/.local/opt"
if [[ ! -x "$DIR/bin/node" ]]; then
  command -v curl >/dev/null || { echo "curl missing" >&2; exit 2; }
  tmpdir="$(mktemp -d)"
  trap 'rm -rf "$tmpdir"' EXIT
  echo "Installing official Node.js v$VERSION in your Codespace..."
  curl -fsSL --retry 3 "$BASE/SHASUMS256.txt" -o "$tmpdir/SHASUMS256.txt"
  curl -fsSL --retry 3 "$BASE/$PACKAGE" -o "$tmpdir/$PACKAGE"
  # Verify the downloaded archive against nodejs.org's SHA-256 manifest.
  grep -F "  $PACKAGE" "$tmpdir/SHASUMS256.txt" > "$tmpdir/expected.sha256"
  (cd "$tmpdir" && sha256sum -c expected.sha256)
  mkdir -p "$DIR"
  tar -xJf "$tmpdir/$PACKAGE" -C "$DIR" --strip-components=1
fi
ln -sf "$DIR/bin/node" "$HOME/.local/bin/node"
ln -sf "$DIR/bin/npm" "$HOME/.local/bin/npm"
ln -sf "$DIR/bin/npx" "$HOME/.local/bin/npx"
export PATH="$HOME/.local/bin:$PATH"
echo "Node.js ready: $(node --version), npm $(npm --version)"
