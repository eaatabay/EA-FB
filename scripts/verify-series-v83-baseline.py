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
    'dist-series-codex-8502-test/SOURCE-NOTICE.txt',
    'dist-series-codex-8502-test/SOURCE-LICENSE',
    'dist-series-codex-8502-test/build-info.json',
    'dist-series-codex-8502-test/repo.json',
    'dist-series-codex-8502-test/plugins.json',
    'dist-series-codex-8502-test/EA-FB-SERIES-CODEX-8502-TEST.cs3',
    "scripts/verify-blue-isolation.py",
    "tests/test_blue_isolation.py",
    "dist-series-codex-8501-test/SOURCE-NOTICE.txt",
    "dist-series-codex-8501-test/SOURCE-LICENSE",
    "dist-series-codex-8501-test/build-info.json",
    "dist-series-codex-8501-test/repo.json",
    "dist-series-codex-8501-test/plugins.json",
    "dist-series-codex-8501-test/EA-FB-SERIES-CODEX-8501-TEST.cs3",
    "scripts/package-series-codex.py",
    "tests/test_series_package.py",
    "core-tests/clean/SeriesLiveLookupTest.kt",
    "EA-FB/src/main/kotlin/com/eafb/SeriesTestIsolation.kt",
    "core-tests/clean/SeriesIsolationTest.kt",
    "core-tests/clean/SeriesAliasSafetyTest.kt",
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
NEW_RUNTIME = {'EA-FB/src/main/kotlin/com/eafb/SeriesTestIsolation.kt': 'a7e85b8dd15a181ffe7ec3f87d4d1e96c8cbee50b82b7694adf47b85c8975f4d'}
FROZEN_8501 = {'dist-series-codex-8501-test/EA-FB-SERIES-CODEX-8501-TEST.cs3': 'f2b2d28a8a6adf5fbdfe8eae91fb70f36c346f658c3dbf7bcc91aae55fe12a3b', 'dist-series-codex-8501-test/SOURCE-LICENSE': '3972dc9744f6499f0f9b2dbf76696f2ae7ad8af9b23dde66d6af86c9dfb36986', 'dist-series-codex-8501-test/SOURCE-NOTICE.txt': '02b81432d90ed69f7d40774d5f3dcee9134b3a31979ddeb9c9c9c90e8d4c6cb5', 'dist-series-codex-8501-test/build-info.json': '93aac75cc72f366e57abe808cf90497e6da5fd46339858ee5211af2771f65830', 'dist-series-codex-8501-test/plugins.json': '462eabf994f120a4016e7a8d5ad475010a9549c73950d88b607cbd13f6b5e913', 'dist-series-codex-8501-test/repo.json': 'f0373e800693a7a879de3f8706f8a9be13d916d0a9a243d9f744a9ed5209f5dc'}
DOCS = "docs/series-name-codex/"

# Exact reviewed bytes; changing any other method or later bytes fails closed.
APPROVED_RUNTIME = {'EA-FB/src/main/kotlin/com/eafb/SourceSearchTitles.kt': '770804a1e14f96f0ac2924d04c86c3ac5ceaf998f6558c852a2a4203de018201', 'EA-FB/src/main/kotlin/com/eafb/BronzeApiAdapter.kt': '845c61d08689f0a9ca304740fc96dfa86390d2fdfc08015cc1d193f0bc8305ad', 'EA-FB/src/main/kotlin/com/eafb/DiziBoxAdapter.kt': '14ae40efb4a1a6616aeb8f067cd704206c430655b9bc2781d3721e2edf4a947a', 'EA-FB/src/main/kotlin/com/eafb/DiziYouAdapter.kt': 'c8cdde24c494776dbb25d8ce3a426af9843bfa37b7c3e6fe6859dd9554f5b7c7', 'EA-FB/src/main/kotlin/com/eafb/CleanTestIdentity.kt': 'fe4071786487cfa94c8bd9e3905b5330eba2b9c6ad684afb110f12981a8698c0', 'EA-FB/src/main/kotlin/com/eafb/EAPlugin.kt': 'eab8097698ea8a8e53d3c7753c3febac2e32f5432e687dfe8f6bef0509eb938e', 'EA-FB/build.gradle.kts': '9c3e2a8fdb3ad6d46dcf805fae9dc1b085050ee1111bf6d4c05080061388d623'}
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
    for path, expected in NEW_RUNTIME.items():
        if hashlib.sha256(read_file(path)).hexdigest() != expected:
            raise ValueError("Unapproved new runtime source: " + path)
    for path, expected in FROZEN_8501.items():
        if hashlib.sha256(read_file(path)).hexdigest() != expected:
            raise ValueError("Protected published 8501 artifact changed: " + path)
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
