#!/usr/bin/env python3
"""Fail closed: this diagnostic branch currently allows no runtime/source changes."""
import hashlib
from pathlib import Path
import subprocess

BASE = "16396702470eb1f81cc6dac4360e6b0ff84927d1"
BRANCH = "test/ea-fb-series-name-codex-20261019"
PACKAGE = "dist-v83-test/EA-FB-V83-TEST.cs3"
PACKAGE_SHA256 = "40fd24acb41dc69fa869ceb69ee6661e5136c1dff39434eb8bd3227f955f3b66"
# Exact new paths only. Future implementation requires a reviewed, narrow guard update.
ALLOWED_NEW = {
    "core-tests/clean/SeriesNameDiagnosisTest.kt",
    "scripts/test-series-name-codex.sh",
    "scripts/verify-series-v83-baseline.py",
    "tests/test_series_v83_baseline.py",
    ".github/workflows/series-name-diagnostics.yml",
}
DOCS = "docs/series-name-codex/"

def verify(root, read_file=None):
    root = Path(root)
    read_file = read_file or (lambda path: (root / path).read_bytes())
    def git(*args):
        return subprocess.check_output(["git", *args], cwd=root)
    baseline = git("ls-tree", "-r", "--name-only", BASE).decode().splitlines()
    for path in baseline:
        if read_file(path) != git("show", BASE + ":" + path):
            raise ValueError("Protected V83 tracked file changed: " + path)
    changed = set(git("diff", "--name-only", BASE, "--").decode().splitlines())
    unknown = {p for p in changed if p not in ALLOWED_NEW and not p.startswith(DOCS)}
    if unknown:
        raise ValueError("Unexpected changed path: " + ", ".join(sorted(unknown)))
    untracked = git("ls-files", "--others", "--exclude-standard").decode().splitlines()
    unknown = {p for p in untracked if p not in ALLOWED_NEW and not p.startswith(DOCS)}
    if unknown:
        raise ValueError("Unexpected untracked path: " + ", ".join(sorted(unknown)))
    digest = hashlib.sha256(read_file(PACKAGE)).hexdigest()
    if digest != PACKAGE_SHA256:
        raise ValueError("Protected V83 package hash changed")
    print(f"PASS: {len(baseline)} V83 tracked files byte-identical; V83 CS3 SHA-256 {digest}")
    return len(baseline)

if __name__ == "__main__":
    verify(Path(__file__).resolve().parents[1])
