#!/usr/bin/env python3
"""Package the isolated V82 test, with metadata derived from final bytes."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import struct
import zlib
import zipfile
import os
import tempfile
import urllib.request
import io
from bronze_dex import Dex

BRANCH = "test/ea-fb-v82-name-cleanup"
DIST = "dist-v82-test"
NAME = "EA-FB V82 TEST"
ID = "EA-FB-V82-TEST"
FILE = ID + ".cs3"
BASE = f"https://raw.githubusercontent.com/eaatabay/EA-FB/{BRANCH}/{DIST}/"


def compiled_classes(dex):
    if not dex.startswith(b"dex\n") or len(dex) < 112:
        raise ValueError("Missing compiled DEX")
    if hashlib.sha1(dex[32:]).digest() != dex[12:32] or zlib.adler32(dex[12:]) & 0xffffffff != struct.unpack_from("<I", dex, 8)[0]:
        raise ValueError("DEX integrity failure")
    def u32(offset):
        return struct.unpack_from("<I", dex, offset)[0]
    def string(index):
        offset = u32(u32(60) + 4 * index)
        while dex[offset] & 128:
            offset += 1
        offset += 1
        return dex[offset:dex.index(0, offset)].decode("utf-8", errors="replace")
    return {string(u32(u32(68) + 4 * u32(u32(100) + 32 * i))) for i in range(u32(96))}


def validate_compiled_scope(dex):
    classes = compiled_classes(dex)
    for name in ["EAPlugin", "EAProvider", "PlaybackLinkBridge", "DiziBoxAdapter", "DiziYouAdapter", "BronzeNlAdapter", "BronzeLandAdapter", "CleanTestIdentity"]:
        if "Lcom/eafb/" + name + ";" not in classes:
            raise ValueError("Missing compiled class: " + name)
    for name in ["HDFilmCehennemi", "HDFilmcehennemiLand", "LocalHlsServer"]:
        if "Lcom/keyiflerolsun/" + name + ";" not in classes:
            raise ValueError("Missing original Bronze class: " + name)



BRONZE_REF = "7f680cd5ef2ad8be2dc579b1e46b53ba3e28d4cd"
BRONZE = {
    "HDFilmCehennemi_v52.cs3": (52, "62069a271af4837d84131a11c274ee5248b5ff4329f86855a7c15168feca501a"),
    "HDFilmcehennemiLand_v4.cs3": (4, "3342b06358b64b32a6c5f8b463958d2d40faa4f69359447eb1d88d05b38b8c80"),
}

def merge_bronze(own, root):
    sdk = Path(os.environ.get("ANDROID_HOME", os.environ.get("ANDROID_SDK_ROOT", "/nonexistent")))
    d8 = sdk / "build-tools/35.0.0/lib/d8.jar"
    if not d8.is_file():
        raise ValueError("Android D8 required for original Bronze DEX merge")
    with tempfile.TemporaryDirectory() as temp:
        folder = Path(temp)
        inputs = [folder / "ea.dex"]
        inputs[0].write_bytes(own)
        expected = compiled_classes(own)
        originals = []
        for filename, (version, digest) in BRONZE.items():
            url = f"https://raw.githubusercontent.com/dr-octagon/Cloudstream-BronzeCloud/{BRONZE_REF}/{filename}"
            blob = urllib.request.urlopen(url, timeout=60).read()
            if hashlib.sha256(blob).hexdigest() != digest:
                raise ValueError("Bronze checksum mismatch: " + filename)
            with zipfile.ZipFile(io.BytesIO(blob)) as archive:
                if archive.testzip() or json.loads(archive.read("manifest.json"))["version"] != version:
                    raise ValueError("Bronze manifest/archive mismatch")
                original = archive.read("classes.dex")
            originals.append(original)
            classes = compiled_classes(original)
            if expected & classes or not all(c.startswith("Lcom/keyiflerolsun/") for c in classes):
                raise ValueError("Bronze class collision or unexpected namespace")
            expected |= classes
            target = folder / (filename + ".dex")
            target.write_bytes(original)
            inputs.append(target)
        output = folder / "merged"
        output.mkdir()
        subprocess.run(["java", "-cp", str(d8), "com.android.tools.r8.D8", "--min-api", "21", "--output", str(output), *map(str, inputs)], check=True)
        if list(output.glob("*.dex")) != [output / "classes.dex"]:
            raise ValueError("Unexpected multidex package")
        merged = (output / "classes.dex").read_bytes()
        if compiled_classes(merged) != expected:
            raise ValueError("Merged DEX class definitions differ from original input union")
        verify_bronze_methods(originals, merged)
        return merged



def instruction_fingerprint(dex, idx):
    out = []
    for offset, opcode, annotation, raw in dex.instructions(idx):
        raw = list(raw)
        # DEX merging relocates type/string/field/method table indices only.
        if opcode in [0x1a, 0x1b, 0x1c, 0x1f, 0x20, 0x22, 0x23, 0x24, 0x25] or 0x52 <= opcode <= 0x6d or 0x6e <= opcode <= 0x72 or 0x74 <= opcode <= 0x78:
            if opcode in [0x24, 0x25]:
                annotation = "type " + dex.types[raw[1]]
            raw[1] = 0
            if opcode == 0x1b:
                raw[2] = 0
        out.append((offset, opcode, annotation, raw))
    return out

def verify_bronze_methods(originals, merged):
    final = Dex(merged)
    by_name = {name: idx for idx, name in enumerate(final.methods)}
    checked = 0
    for raw in originals:
        original = Dex(raw)
        for idx in original.code:
            name = original.methods[idx]
            target = by_name.get(name)
            if target not in final.code or instruction_fingerprint(original, idx) != instruction_fingerprint(final, target):
                raise ValueError("Original Bronze instructions changed: " + name)
            checked += 1
    print(f"PASS: {checked} original Bronze method instruction sequences retained after DEX relocation")



BASELINE = "88d7966fedf5150c38e4c898da86c482f1284b70"
RENAMES = {
    "EA-FB/src/main/kotlin/com/eafb/CleanTestIdentity.kt": [
        ("EA-FB CODEX BRONZE NL LAND TEST", "EA-FB V82 TEST"),
        ("EA-FB-CODEX-BRONZE-NL-LAND-20261009", "EA-FB-V82-TEST"),
        ('const val STORE = "ea_fb_clean_bronze_nl_land_20261009"', 'const val STORE = "ea_fb_v82_test"'),
        ('const val LEGACY_STORE = "ea_fb_clean_codex_fix_20261009"', 'const val LEGACY_STORE = "ea_fb_clean_bronze_nl_land_20261009"'),
        ('val legacySourceIds = listOf("dizibox", "diziyou")', 'val legacySourceIds = sourceIds'),
    ],
    "EA-FB/src/main/kotlin/com/eafb/BronzeApiAdapter.kt": [("NL (Bronze v52)", "HDFilmCehennemi"), ("LAND (Bronze v4)", "HDFilmCehennemi LAND")],
    "EA-FB/src/main/kotlin/com/eafb/EAProvider.kt": [('"hdfilmcehennemi-nl" -> "NL"', '"hdfilmcehennemi-nl" -> "HDFilmCehennemi"'), ('"hdfilmcehennemi-land" -> "LAND"', '"hdfilmcehennemi-land" -> "HDFilmCehennemi LAND"')],
    "EA-FB/src/main/kotlin/com/eafb/EASettingsDialog.kt": [("NL (Bronze v52 deneme)", "HDFilmCehennemi"), ("LAND (Bronze v4 deneme)", "HDFilmCehennemi LAND")],
}

def verify_scope(root):
    files = subprocess.check_output(["git", "ls-tree", "-r", "--name-only", BASELINE, "EA-FB/src/main"], cwd=root, text=True).splitlines()
    for path in files:
        original = subprocess.check_output(["git", "show", BASELINE + ":" + path], cwd=root)
        if path in RENAMES:
            text = original.decode()
            for old, new in RENAMES[path]:
                text = text.replace(old, new)
            original = text.encode()
        if (root / path).read_bytes() != original:
            raise ValueError("Unexpected source/runtime change: " + path)
    print("PASS: baseline main sources unchanged except permitted identity and display labels")

def verify_runtime(dex, root):
    path = "dist-clean-codex-bronze-nl-land-20261009/EA-FB-CODEX-BRONZE-NL-LAND-20261009.cs3"
    blob = subprocess.check_output(["git", "show", BASELINE + ":" + path], cwd=root)
    baseline = Dex(zipfile.ZipFile(io.BytesIO(blob)).read("classes.dex"))
    final = Dex(dex)
    baseline_methods = {baseline.methods[i]: i for i in baseline.code}
    final_methods = {final.methods[i]: i for i in final.code}
    if baseline_methods.keys() != final_methods.keys():
        raise ValueError("Runtime method set changed")
    labels = {"EA-FB CODEX BRONZE NL LAND TEST": NAME, "NL (Bronze v52)": "HDFilmCehennemi", "LAND (Bronze v4)": "HDFilmCehennemi LAND", "NL (Bronze v52 deneme)": "HDFilmCehennemi", "LAND (Bronze v4 deneme)": "HDFilmCehennemi LAND", "NL": "HDFilmCehennemi", "LAND": "HDFilmCehennemi LAND", "ea_fb_clean_bronze_nl_land_20261009": "ea_fb_v82_test", "ea_fb_clean_codex_fix_20261009": "ea_fb_clean_bronze_nl_land_20261009"}
    exceptions = {"Lcom/eafb/CleanTestIdentity;.<clinit>()V"}
    checked = 0
    for name, index in baseline_methods.items():
        if name in exceptions:
            continue  # Identity's source IDs are unchanged; migration target is V81's four toggles.
        expected = [list(row) for row in instruction_fingerprint(baseline, index)]
        actual = [list(row) for row in instruction_fingerprint(final, final_methods[name])]
        for row in expected:
            if row[1] in [0x1a, 0x1b]:
                for old, new in labels.items():
                    row[2] = row[2].replace(repr(old), repr(new))
        if expected != actual:
            raise ValueError("Unexpected runtime instruction change: " + name)
        checked += 1
    print(f"PASS: {checked} V81 runtime methods preserved with permitted presentation strings")


def entry(data, version):
    return dict(fileHash="sha256-" + hashlib.sha256(data).hexdigest(), fileSize=len(data),
                apiVersion=1, repositoryUrl="https://github.com/eaatabay/EA-FB", status=3,
                language="tr", authors=["EA-FB"], tvTypes=["Movie", "TvSeries", "Live"],
                version=version, internalName=ID, name=NAME,
                description="EA-FB V82 TEST: DiziBox, DiziYou, HDFilmCehennemi and HDFilmCehennemi LAND; device verification pending",
                url=BASE + FILE)


def package(source, root):
    verify_scope(root)
    branch = subprocess.check_output(["git", "branch", "--show-current"], cwd=root, text=True).strip()
    if branch != BRANCH:
        raise ValueError("Refusing to package outside " + BRANCH)
    version = int(re.search(r"^version = (\d+)$", (root / "EA-FB/build.gradle.kts").read_text(), re.M)[1])
    source_commit = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=root, text=True).strip()
    with zipfile.ZipFile(source) as z:
        if z.testzip() is not None:
            raise ValueError("Corrupt build archive")
        manifest = json.loads(z.read("manifest.json"))
        if version != 82 or manifest["version"] != version or manifest["pluginClassName"] != "com.eafb.EAPlugin":
            raise ValueError("Build manifest does not match this source")
        dex = merge_bronze(z.read("classes.dex"), root)
        validate_compiled_scope(dex)
        verify_runtime(dex, root)
        manifest.update(name=NAME, internalName=ID, authors=["EA-FB"])
        out = root / DIST
        out.mkdir(exist_ok=True)
        notice_dir = root / "docs/v82"
        for notice in ["SOURCE-LICENSE", "SOURCE-NOTICE.txt"]:
            (out / notice).write_bytes((notice_dir / notice).read_bytes())
        dst = out / FILE
        with zipfile.ZipFile(dst, "w", compression=zipfile.ZIP_DEFLATED) as w:
            for name in sorted(z.namelist()):
                info = zipfile.ZipInfo(name, (2026, 10, 9, 0, 0, 0))
                info.compress_type = zipfile.ZIP_DEFLATED
                w.writestr(info, json.dumps(manifest, ensure_ascii=False).encode() if name == "manifest.json" else dex if name == "classes.dex" else z.read(name))
            for notice in ["SOURCE-LICENSE", "SOURCE-NOTICE.txt"]:
                info = zipfile.ZipInfo(notice, (2026, 10, 9, 0, 0, 0))
                info.compress_type = zipfile.ZIP_DEFLATED
                w.writestr(info, (notice_dir / notice).read_bytes())
    data = dst.read_bytes()
    (out / "plugins.json").write_text(json.dumps([entry(data, version)], ensure_ascii=False, indent=2) + "\n")
    (out / "repo.json").write_text(json.dumps(dict(name=NAME, description="EA-FB V82 isolated test repository", manifestVersion=1, pluginLists=[BASE + "plugins.json"]), indent=2) + "\n")
    (out / "build-info.json").write_text(json.dumps(dict(sourceCommit=source_commit, branch=branch, bronzeRef=BRONZE_REF, bronzeInputs=BRONZE, adapters=["dizibox", "diziyou", "hdfilmcehennemi-nl", "hdfilmcehennemi-land"], dexSha256=hashlib.sha256(dex).hexdigest()), indent=2) + "\n")
    verify(out)
    return dst


def verify(out):
    data = (out / FILE).read_bytes()
    actual = json.loads((out / "plugins.json").read_text())
    with zipfile.ZipFile(out / FILE) as z:
        if z.testzip() is not None:
            raise ValueError("Corrupt test package")
        manifest = json.loads(z.read("manifest.json"))
        dex = z.read("classes.dex")
        validate_compiled_scope(dex)
        for notice in ["SOURCE-LICENSE", "SOURCE-NOTICE.txt"]:
            if z.read(notice) != (out / notice).read_bytes():
                raise ValueError("Missing or mismatched Bronze notice")
    verify_metadata(data, manifest, actual)
    repo = json.loads((out / "repo.json").read_text())
    provenance = json.loads((out / "build-info.json").read_text())
    if repo["pluginLists"] != [BASE + "plugins.json"] or repo["name"] != NAME:
        raise ValueError("Wrong isolated repository list")
    if provenance.get("bronzeRef") != BRONZE_REF or provenance.get("bronzeInputs") != {key: list(value) for key, value in BRONZE.items()}:
        raise ValueError("Wrong pinned Bronze provenance")
    if provenance["branch"] != BRANCH or provenance["adapters"] != ["dizibox", "diziyou", "hdfilmcehennemi-nl", "hdfilmcehennemi-land"] or provenance["dexSha256"] != hashlib.sha256(dex).hexdigest() or not re.fullmatch(r"[0-9a-f]{40}", provenance["sourceCommit"]):
        raise ValueError("Wrong build provenance")


def verify_metadata(data, manifest, actual):
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
