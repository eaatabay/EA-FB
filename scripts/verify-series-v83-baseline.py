#!/usr/bin/env python3
"""Protect every V83 file; permit only reviewed method regions with exact source hashes."""
import hashlib
from pathlib import Path
import subprocess

BASE = "16396702470eb1f81cc6dac4360e6b0ff84927d1"
BRANCH = "test/ea-fb-series-name-codex-20261019"
PACKAGE = "dist-v83-test/EA-FB-V83-TEST.cs3"
PACKAGE_SHA256 = "40fd24acb41dc69fa869ceb69ee6661e5136c1dff39434eb8bd3227f955f3b66"
# Exact new paths only. Future implementation requires a reviewed, narrow guard update.
ALLOWED_NEW = {
    "core-tests/clean/SeriesAliasContractTest.kt",
    "scripts/diagnose-series-catalog.py",
    "tests/test_series_catalog_diagnosis.py",
    ".github/workflows/series-name-catalog-diagnosis.yml",
    "core-tests/clean/SeriesNameDiagnosisTest.kt",
    "scripts/test-series-name-codex.sh",
    "scripts/verify-series-v83-baseline.py",
    "tests/test_series_v83_baseline.py",
    ".github/workflows/series-name-diagnostics.yml",
}
DOCS = "docs/series-name-codex/"

# Exact reviewed bytes; changing any other method or later bytes fails closed.
APPROVED_RUNTIME = {'EA-FB/src/main/kotlin/com/eafb/SourceSearchTitles.kt': '770804a1e14f96f0ac2924d04c86c3ac5ceaf998f6558c852a2a4203de018201', 'EA-FB/src/main/kotlin/com/eafb/BronzeApiAdapter.kt': '845c61d08689f0a9ca304740fc96dfa86390d2fdfc08015cc1d193f0bc8305ad', 'EA-FB/src/main/kotlin/com/eafb/DiziBoxAdapter.kt': '14ae40efb4a1a6616aeb8f067cd704206c430655b9bc2781d3721e2edf4a947a', 'EA-FB/src/main/kotlin/com/eafb/DiziYouAdapter.kt': 'c8cdde24c494776dbb25d8ce3a426af9843bfa37b7c3e6fe6859dd9554f5b7c7'}
METHOD_REGIONS = {
    "EA-FB/src/main/kotlin/com/eafb/BronzeApiAdapter.kt": ("    override suspend fun search(query:", "    // Film matching only:"),
    "EA-FB/src/main/kotlin/com/eafb/DiziBoxAdapter.kt": ("    private suspend fun searchTitle(", "    private suspend fun searchHtml("),
    "EA-FB/src/main/kotlin/com/eafb/DiziYouAdapter.kt": ("    override suspend fun search(query:", "    private suspend fun playlistInfo("),
}
def outside_region(data, markers):
    start, end = (marker.encode() for marker in markers)
    a = data.index(start); b = data.index(end, a)
    return data[:a] + data[b:]


def verify(root, read_file=None):
    root = Path(root)
    read_file = read_file or (lambda path: (root / path).read_bytes())
    def git(*args):
        return subprocess.check_output(["git", *args], cwd=root)
    baseline = git("ls-tree", "-r", "--name-only", BASE).decode().splitlines()
    for path in baseline:
        actual = read_file(path)
        original = git("show", BASE + ":" + path)
        if path in APPROVED_RUNTIME:
            if hashlib.sha256(actual).hexdigest() != APPROVED_RUNTIME[path]:
                raise ValueError("Protected V83 tracked file changed: " + path)
            if path in METHOD_REGIONS and outside_region(actual, METHOD_REGIONS[path]) != outside_region(original, METHOD_REGIONS[path]):
                raise ValueError("Protected V83 method outside approved search region changed: " + path)
        elif actual != original:
            raise ValueError("Protected V83 tracked file changed: " + path)
    changed = set(git("diff", "--name-only", BASE, "--").decode().splitlines())
    unknown = {p for p in changed if p not in ALLOWED_NEW and p not in APPROVED_RUNTIME and not p.startswith(DOCS)}
    if unknown:
        raise ValueError("Unexpected changed path: " + ", ".join(sorted(unknown)))
    untracked = git("ls-files", "--others", "--exclude-standard").decode().splitlines()
    unknown = {p for p in untracked if p not in ALLOWED_NEW and p not in APPROVED_RUNTIME and not p.startswith(DOCS)}
    if unknown:
        raise ValueError("Unexpected untracked path: " + ", ".join(sorted(unknown)))
    digest = hashlib.sha256(read_file(PACKAGE)).hexdigest()
    if digest != PACKAGE_SHA256:
        raise ValueError("Protected V83 package hash changed")
    print(f"PASS: {len(baseline)-len(APPROVED_RUNTIME)} V83 files byte-identical; {len(APPROVED_RUNTIME)} reviewed source hashes/method regions exact; V83 CS3 SHA-256 {digest}")
    return len(baseline)

if __name__ == "__main__":
    verify(Path(__file__).resolve().parents[1])
