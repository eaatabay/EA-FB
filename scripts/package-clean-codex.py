#!/usr/bin/env python3
"""Package only the isolated CODEX test, with metadata derived from final bytes."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import zipfile

BRANCH = "test/clean-codex-fix-20261009"
DIST = "dist-clean-codex-fix-20261009"
NAME = "EA-FB CODEX CLEAN TEST"
ID = "EA-FB-CODEX-CLEAN-20261009"
FILE = ID + ".cs3"
BASE = f"https://raw.githubusercontent.com/eaatabay/EA-FB/{BRANCH}/{DIST}/"


def entry(data, version):
    return dict(fileHash="sha256-" + hashlib.sha256(data).hexdigest(), fileSize=len(data),
                apiVersion=1, repositoryUrl="https://github.com/eaatabay/EA-FB", status=3,
                language="tr", authors=["EA-FB"], tvTypes=["Movie", "TvSeries", "Live"],
                version=version, internalName=ID, name=NAME,
                description="Isolated CODEX CLEAN test: DiziBox and DiziYou only; Mi Box playback unverified",
                url=BASE + FILE)


def package(source, root):
    branch = subprocess.check_output(["git", "branch", "--show-current"], cwd=root, text=True).strip()
    if branch != BRANCH:
        raise ValueError("Refusing to package outside " + BRANCH)
    version = int(re.search(r"^version = (\d+)$", (root / "EA-FB/build.gradle.kts").read_text(), re.M)[1])
    source_commit = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=root, text=True).strip()
    with zipfile.ZipFile(source) as z:
        if z.testzip() is not None:
            raise ValueError("Corrupt build archive")
        manifest = json.loads(z.read("manifest.json"))
        if manifest["version"] != version or manifest["pluginClassName"] != "com.eafb.EAPlugin":
            raise ValueError("Build manifest does not match this source")
        dex = z.read("classes.dex")
        if not dex.startswith(b"dex\n"):
            raise ValueError("Missing compiled DEX")
        # Required compiled descriptors, in addition to the source registry.
        for name in ["EAPlugin", "EAProvider", "PlaybackLinkBridge", "DiziBoxAdapter", "DiziYouAdapter", "CleanTestIdentity"]:
            if ("Lcom/eafb/" + name + ";").encode() not in dex:
                raise ValueError("Missing compiled class: " + name)
        manifest.update(name=NAME, internalName=ID, authors=["EA-FB"])
        out = root / DIST
        out.mkdir(exist_ok=True)
        dst = out / FILE
        with zipfile.ZipFile(dst, "w", compression=zipfile.ZIP_DEFLATED) as w:
            for name in sorted(z.namelist()):
                info = zipfile.ZipInfo(name, (2026, 10, 9, 0, 0, 0))
                info.compress_type = zipfile.ZIP_DEFLATED
                w.writestr(info, json.dumps(manifest, ensure_ascii=False).encode() if name == "manifest.json" else z.read(name))
    data = dst.read_bytes()
    (out / "plugins.json").write_text(json.dumps([entry(data, version)], ensure_ascii=False, indent=2) + "\n")
    (out / "repo.json").write_text(json.dumps(dict(name=NAME, description="Isolated CODEX test; does not update CLEAN, RED or BLUE", manifestVersion=1, pluginLists=[BASE + "plugins.json"]), indent=2) + "\n")
    (out / "build-info.json").write_text(json.dumps(dict(sourceCommit=source_commit, branch=branch, adapters=["dizibox", "diziyou"], dexSha256=hashlib.sha256(dex).hexdigest()), indent=2) + "\n")
    verify(out)
    return dst


def verify(out):
    data = (out / FILE).read_bytes()
    actual = json.loads((out / "plugins.json").read_text())
    with zipfile.ZipFile(out / FILE) as z:
        if z.testzip() is not None:
            raise ValueError("Corrupt test package")
        manifest = json.loads(z.read("manifest.json"))
    if actual != [entry(data, manifest["version"])] or manifest.get("internalName") != ID or manifest["name"] != NAME:
        raise ValueError("Distribution mismatch")
    if manifest["pluginClassName"] != "com.eafb.EAPlugin":
        raise ValueError("Wrong provider plugin")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("source", type=Path, nargs="?")
    parser.add_argument("--verify", action="store_true")
    args = parser.parse_args()
    root = Path(__file__).resolve().parent.parent
    if args.verify:
        verify(root / DIST)
        print("PASS: isolated CS3 manifest, hash, size and update list")
    else:
        if args.source is None:
            parser.error("source CS3 required")
        print(package(args.source.resolve(), root))
