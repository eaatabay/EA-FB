#!/usr/bin/env python3
"""Prepare manifests from a real, locally compiled CloudStream package."""
import json
import os
import stat
import shutil
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RAW = "https://raw.githubusercontent.com/eaatabay/EA-FB/main/dist"
ICON = "https://raw.githubusercontent.com/eaatabay/EA-FB/main/assets/ea-fb-logo.png"


def stage(root=ROOT, staging=False):
    expected_version = int(os.environ.get("EA_FB_V6_STAGING_VERSION", "48")) if staging else 6
    if staging and expected_version != 48:
        raise ValueError("Only V48 isolated staging builds are supported")
    manifest_file = root / "build" / "plugins.json"
    if (root / "build").is_symlink() or manifest_file.is_symlink() or \
            (root / "EA-FB").is_symlink() or (root / "EA-FB" / "build").is_symlink():
        raise ValueError("Refusing symlinked build inputs")
    if not manifest_file.is_file():
        raise ValueError("Missing build/plugins.json; run Gradle makePluginsJson first")
    entries = json.loads(manifest_file.read_text(encoding="utf-8"))
    if not isinstance(entries, list) or len(entries) != 1:
        raise ValueError("EA-FB must contain exactly one CloudStream plugin")
    entry = entries[0]
    if (not isinstance(entry, dict) or entry.get("internalName") != "EA-FB" or
            type(entry.get("version")) is not int or entry["version"] != expected_version):
        raise ValueError(f"Expected EA-FB v{expected_version} Gradle plugin metadata")
    binaries = list((root / "EA-FB" / "build").glob("*.cs3"))
    if len(binaries) != 1:
        raise ValueError(f"Expected exactly one compiled .cs3, found {len(binaries)}")
    binary = binaries[0]
    if binary.is_symlink():
        raise ValueError("Refusing a symlinked .cs3 build artifact")
    if binary.stat().st_size < 100 or not zipfile.is_zipfile(binary):
        raise ValueError("Invalid or empty .cs3 archive")
    with zipfile.ZipFile(binary) as archive:
        members = archive.namelist()
        infos = archive.infolist()
        if len(infos) > 512 or sum(info.file_size for info in infos) > 128 * 1024 * 1024 or any(
                info.file_size > 64 * 1024 * 1024 or info.flag_bits & 1 for info in infos):
            raise ValueError("Oversized or encrypted .cs3 archive")
        if len(members) != len(set(members)):
            raise ValueError("Duplicate .cs3 ZIP members")
        if any(name.startswith("/") or "\\" in name or name.endswith("//") or
               any(part in ("", ".", "..") for part in name.rstrip("/").split("/"))
               for name in members):
            raise ValueError("Unsafe .cs3 ZIP member path")
        if any(info.create_system == 3 and
               stat.S_IFMT(info.external_attr >> 16) == stat.S_IFLNK
               for info in infos):
            raise ValueError("Symlink member inside .cs3 archive")
        if not {"classes.dex", "manifest.json"}.issubset(members):
            raise ValueError("The .cs3 is missing required dex or manifest files")
        try:
            package_manifest = json.loads(archive.read("manifest.json"))
        except (UnicodeDecodeError, json.JSONDecodeError) as exc:
            raise ValueError("Invalid .cs3 manifest JSON") from exc
        if (not isinstance(package_manifest, dict) or
                ("name" in package_manifest and package_manifest["name"] != "EA-FB") or
                ("version" in package_manifest and
                 (type(package_manifest["version"]) is not int or
                  package_manifest["version"] != expected_version))):
            raise ValueError("Unexpected .cs3 manifest identity or version")
        corrupt_member = archive.testzip()
        if corrupt_member:
            raise ValueError(f"Corrupt .cs3 archive entry: {corrupt_member}")
    dist = root / "dist"
    if dist.is_symlink():
        raise ValueError("Refusing a symlinked dist directory")
    dist.mkdir(exist_ok=True)
    output = dist / "EA-FB.cs3"
    if output.is_symlink() or (dist / "plugins.json").is_symlink() or (dist / "repo.json").is_symlink():
        raise ValueError("Refusing symlinked release outputs")
    shutil.copyfile(binary, output)
    entry["name"] = "EA-FB"
    entry["iconUrl"] = ICON
    entry["fileSize"] = output.stat().st_size
    entry["repositoryUrl"] = "https://github.com/eaatabay/EA-FB"
    entry["url"] = RAW + "/EA-FB.cs3"
    (dist / "plugins.json").write_text(json.dumps(entries, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    (dist / "repo.json").write_text(json.dumps({
        "name": "EA-FB",
        "iconUrl": ICON,
        "description": "Tek eklentide film, dizi ve izinli canlı TV",
        "manifestVersion": 1,
        "pluginLists": [RAW + "/plugins.json"],
    }, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Staged {output.name}: {output.stat().st_size} bytes")
    return dist


if __name__ == "__main__":
    try:
        if sys.argv[1:] not in ([], ["--v6-staging"]):
            raise ValueError("Usage: stage-release.py [--v6-staging]")
        stage(staging=sys.argv[1:] == ["--v6-staging"])
    except (OSError, ValueError, zipfile.BadZipFile, json.JSONDecodeError) as exc:
        print(f"Release blocked: {exc}", file=sys.stderr)
        sys.exit(1)
