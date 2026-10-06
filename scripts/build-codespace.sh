#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

export ANDROID_HOME="${ANDROID_HOME:-$HOME/android-sdk}"
export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$ANDROID_HOME}"
export PATH="$HOME/.local/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"
command -v java >/dev/null || { echo "Java missing" >&2; exit 2; }
command -v gradle >/dev/null || { echo "Gradle missing" >&2; exit 2; }
test -f "$ANDROID_HOME/platforms/android-35/android.jar" || { echo "Android API 35 missing" >&2; exit 2; }

# Never publish a personal v4 build (which contained a personal API token).
if grep -Eq "tmdbBearerToken.*=.*\"[^\"]{25}" EA-FB/src/main/kotlin/com/eafb/EAConfig.kt; then
  echo "BLOCKED: EAConfig.kt contains a literal credential" >&2
  exit 2
fi
if grep -Eq "Authorization.*Bearer.*catalogToken" EA-FB/src/main/kotlin/com/eafb/EAProvider.kt; then
  echo "BLOCKED: the old client-side TMDb token path is still present" >&2
  exit 2
fi

# Force a clean public compilation; clear stale v4 private dex/manifest artifacts.
rm -rf EA-FB/build
rm -f build/plugins.json
java -version
gradle --version | head -n 8
bash scripts/prepare-cloudstream-gradle.sh
mkdir -p build
logfile="build/codespace-build.log"
if gradle :EA-FB:make makePluginsJson --no-daemon --console=plain --rerun-tasks 2>&1 | tee "$logfile"; then
  echo "Gradle build succeeded"
else
  echo "========== COMPILER ERRORS =========="
  grep -n -m 35 -E "(^e:|^> Task .*FAILED|Unresolved reference|Could not resolve|Could not find|^\\* What went wrong:)" "$logfile" || true
  echo "Full build output: $logfile" >&2
  exit 1
fi
if [[ "${EA_FB_V6_STAGING_BUILD:-}" == "1" ]]; then
  python3 scripts/prepare-v6-staging-overlay.py --verify
  # CloudStream's makePluginsJson can reuse the previous release version in its
  # aggregate metadata even though the freshly compiled CS3 manifest is V71.
  # Normalize only this isolated staging build, and only after proving the CS3
  # itself is V71. Production builds never enter this block.
  python3 - <<'PY'
import json, zipfile
from pathlib import Path

binaries = list(Path("EA-FB/build").glob("*.cs3"))
assert len(binaries) == 1, binaries
with zipfile.ZipFile(binaries[0]) as archive:
    manifest = json.loads(archive.read("manifest.json"))
assert manifest.get("version") == 71, manifest

metadata_path = Path("build/plugins.json")
metadata = json.loads(metadata_path.read_text(encoding="utf-8"))
assert len(metadata) == 1 and metadata[0].get("internalName") == "EA-FB", metadata
if metadata[0].get("version") != 71:
    print(f"Normalizing isolated V71 staging metadata from v{metadata[0].get('version')} to v71")
    metadata[0]["version"] = 71
    metadata_path.write_text(json.dumps(metadata, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
PY
  python3 scripts/stage-release.py --v6-staging
else
  python3 scripts/stage-release.py
fi

python3 - <<'PY'
import os, zipfile
from pathlib import Path
secret = os.environ.get("TMDB_READ_ACCESS_TOKEN", "").strip()
if secret.lower().startswith("bearer "): secret = secret[7:].strip()
package = Path("dist/EA-FB.cs3")
if secret and len(secret) > 25:
    with zipfile.ZipFile(package) as archive:
        if any(secret.encode() in archive.read(name) for name in archive.namelist()):
            package.unlink(missing_ok=True)
            raise SystemExit("BLOCKED: private token found in public package")
print("Public package inspected; no current Codespaces TMDb token found.")
PY
echo "Public EA-FB.cs3 staged under dist/. Never publish private-dist/."
