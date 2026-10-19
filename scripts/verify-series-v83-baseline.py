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
    'dist-series-codex-8503-test/EA-FB-SERIES-CODEX-8503-TEST.cs3',
    'dist-series-codex-8503-test/SOURCE-LICENSE',
    'dist-series-codex-8503-test/SOURCE-NOTICE.txt',
    'dist-series-codex-8503-test/build-info.json',
    'dist-series-codex-8503-test/plugins.json',
    'dist-series-codex-8503-test/repo.json',

    'dist-series-codex-8502-test/SOURCE-NOTICE.txt',
    'dist-series-codex-8502-test/SOURCE-LICENSE',
    'dist-series-codex-8502-test/build-info.json',
    'dist-series-codex-8502-test/repo.json',
    'dist-series-codex-8502-test/plugins.json',
    'dist-series-codex-8502-test/EA-FB-SERIES-CODEX-8502-TEST.cs3',
    "dist-series-codex-8501-test/SOURCE-NOTICE.txt",
    "dist-series-codex-8501-test/SOURCE-LICENSE",
    "dist-series-codex-8501-test/build-info.json",
    "dist-series-codex-8501-test/repo.json",
    "dist-series-codex-8501-test/plugins.json",
    "dist-series-codex-8501-test/EA-FB-SERIES-CODEX-8501-TEST.cs3",
    "scripts/package-series-codex.py",
    "tests/test_series_package.py",
    "core-tests/clean/SeriesLiveLookupTest.kt",
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
FROZEN_8501 = {'dist-series-codex-8501-test/EA-FB-SERIES-CODEX-8501-TEST.cs3': 'f2b2d28a8a6adf5fbdfe8eae91fb70f36c346f658c3dbf7bcc91aae55fe12a3b', 'dist-series-codex-8501-test/SOURCE-LICENSE': '3972dc9744f6499f0f9b2dbf76696f2ae7ad8af9b23dde66d6af86c9dfb36986', 'dist-series-codex-8501-test/SOURCE-NOTICE.txt': '02b81432d90ed69f7d40774d5f3dcee9134b3a31979ddeb9c9c9c90e8d4c6cb5', 'dist-series-codex-8501-test/build-info.json': '93aac75cc72f366e57abe808cf90497e6da5fd46339858ee5211af2771f65830', 'dist-series-codex-8501-test/plugins.json': '462eabf994f120a4016e7a8d5ad475010a9549c73950d88b607cbd13f6b5e913', 'dist-series-codex-8501-test/repo.json': 'f0373e800693a7a879de3f8706f8a9be13d916d0a9a243d9f744a9ed5209f5dc'}
FROZEN_8502 = {'dist-series-codex-8502-test/EA-FB-SERIES-CODEX-8502-TEST.cs3': '92fbfd0dd39028b5d4d6110ae305418f718ec98c4c4d0f1d910ea1103efa83bf', 'dist-series-codex-8502-test/SOURCE-LICENSE': '3972dc9744f6499f0f9b2dbf76696f2ae7ad8af9b23dde66d6af86c9dfb36986', 'dist-series-codex-8502-test/SOURCE-NOTICE.txt': '2e6eee279152705195011a247df3a9da01eb4364330e599943a17265539d9d91', 'dist-series-codex-8502-test/build-info.json': 'b82f12b3d7a45e40d6d4a1dace27480833cd09d70e9060b40a1705133fe58bc3', 'dist-series-codex-8502-test/plugins.json': '45916b69da8e5d5b16a24f7055fcf302a4cc3d8671d5096b727abc2c641c933c', 'dist-series-codex-8502-test/repo.json': '4c150b74a2ba5de0d69b1473b08eac07be37a0cc09dfb3c65e4939f059fa2cbb'}
DOCS = "docs/series-name-codex/"

# Exact reviewed bytes; changing any other method or later bytes fails closed.
APPROVED_RUNTIME = {'EA-FB/src/main/kotlin/com/eafb/SourceSearchTitles.kt': '770804a1e14f96f0ac2924d04c86c3ac5ceaf998f6558c852a2a4203de018201', 'EA-FB/src/main/kotlin/com/eafb/BronzeApiAdapter.kt': '845c61d08689f0a9ca304740fc96dfa86390d2fdfc08015cc1d193f0bc8305ad', 'EA-FB/src/main/kotlin/com/eafb/DiziBoxAdapter.kt': '14ae40efb4a1a6616aeb8f067cd704206c430655b9bc2781d3721e2edf4a947a', 'EA-FB/src/main/kotlin/com/eafb/DiziYouAdapter.kt': 'c8cdde24c494776dbb25d8ce3a426af9843bfa37b7c3e6fe6859dd9554f5b7c7', 'EA-FB/src/main/kotlin/com/eafb/CleanTestIdentity.kt': 'ca5e85ff90bd667c3dc7569bff152f36933834de0b4006d19864ea78d4d31e2a', 'EA-FB/build.gradle.kts': '16f63065e350e0f69710e855e8689394bbb6b58b35d5a095c6a2ccb6c7ed7ad9'}
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
    for path, expected in FROZEN_8501.items():
        if hashlib.sha256(read_file(path)).hexdigest() != expected:
            raise ValueError("Protected published 8501 artifact changed: " + path)
    for path, expected in FROZEN_8502.items():
        if hashlib.sha256(read_file(path)).hexdigest() != expected:
            raise ValueError("Protected published 8502 artifact changed: " + path)
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
