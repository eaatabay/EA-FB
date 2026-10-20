#!/usr/bin/env python3
"""Build/verify versioned test bytes. Never commits, pushes, or deploys."""
import argparse
import hashlib
import importlib.util
import io
import json
from pathlib import Path
import subprocess
import zipfile
from bronze_dex import Dex

ROOT = Path(__file__).resolve().parents[1]
BASELINE = "16396702470eb1f81cc6dac4360e6b0ff84927d1"
BRANCH = "test/ea-fb-universal-matcher-v83-20261020"
DIST = "dist-universal-8601-test"
ID = "EA-FB-UNIVERSAL-8601-TEST"
NAME = "EA-FB UNIVERSAL 8601 TEST"
VERSION = 8601
FILE = ID + ".cs3"
BASE_URL = f"https://raw.githubusercontent.com/eaatabay/EA-FB/{BRANCH}/{DIST}/"

def module(name, filename):
    spec = importlib.util.spec_from_file_location(name, ROOT / "scripts" / filename)
    obj = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(obj)
    return obj

v83 = module("v83_pack_helpers", "package-v83.py")
guard = module("series_source_guard", "verify-universal-v83.py")

# Exact methods/state machines whose search behaviour was approved. Resolver and UI algorithms are excluded.
def search_method(name):
    # Reviewed DTO/model changes and matching/metadata entry methods only; no resolver exemptions.
    return name.startswith((
        "Lcom/eafb/SourceSearchTitles;.candidates(", "Lcom/eafb/MediaQuery;", "Lcom/eafb/MediaOffer;",
        "Lcom/eafb/MultiSourceEngine;.sameContent(",
        "Lcom/eafb/BronzeApiAdapter;.search(", "Lcom/eafb/BronzeApiAdapter;.search$suspendImpl(",
        "Lcom/eafb/BronzeApiAdapter;.searchMovie(", "Lcom/eafb/BronzeApiAdapter$search",
        "Lcom/eafb/DiziYouAdapter;.search(", "Lcom/eafb/DiziYouAdapter;.episodeOffers(",
        "Lcom/eafb/DiziYouAdapter;.access$episodeOffers(", "Lcom/eafb/DiziYouAdapter$search", "Lcom/eafb/DiziYouAdapter$episodeOffers",
        "Lcom/eafb/DiziBoxAdapter;.search(", "Lcom/eafb/DiziBoxAdapter;.searchTitle(", "Lcom/eafb/DiziBoxAdapter$search",
        "Lcom/eafb/PlaybackQuery;.fromData", "Lcom/eafb/PlaybackLinkBridge;.alternatives", "Lcom/eafb/PlaybackLinkBridge;.sourceGroups",
        "Lcom/eafb/PlaybackLinkBridge$alternatives", "Lcom/eafb/PlaybackLinkBridge$sourceGroups",
        "Lcom/eafb/EAProvider;.loadLinks(", "Lcom/eafb/EAProvider$loadLinks",
    ))

IDENTITY_METHODS = {
    "Lcom/eafb/EAProvider;.<init>()V",
    "Lcom/eafb/DetailMetaRow;.renderFragment(Landroidx/fragment/app/Fragment;)V",
    "Lcom/eafb/DetailMetaRow;.renderNextEpisodeOnly(Landroidx/fragment/app/Fragment;)V",
    "Lcom/eafb/EASettingsDialog;.show(Landroid/content/Context;)V",
    "Lcom/eafb/EpisodeTitleStyle;.renderFragment(Landroidx/fragment/app/Fragment;)V",
    "Lcom/eafb/EpisodeUpcomingStyle;.renderFragment(Landroidx/fragment/app/Fragment;)V",
    "Lcom/eafb/FilmSeriesRail;.renderFragment(Landroidx/fragment/app/Fragment;)V",
    "Lcom/eafb/FilmSeriesRail;.makeRail$lambda$3$0$1(Landroidx/fragment/app/FragmentActivity;Lcom/eafb/FilmSeriesRail$Card;Landroid/view/View;)V",
}
NEW_CLASSES = ("Lcom/eafb/SourceTitleMatcher", "Lcom/eafb/SourcePageIdentity", "Lcom/eafb/CatalogMatchMetadata", "Lcom/eafb/SourceIdentity", "Lcom/eafb/MatchDecision", "Lcom/eafb/MatchLevel")

def normalized(dex, index, name):
    rows = [list(row) for row in v83.instruction_fingerprint(dex, index)]
    replacements = {"EA-FB V83 TEST": NAME} if name in IDENTITY_METHODS else {}
    if name == "Lcom/eafb/EASettings;.initialize(Landroid/content/Context;)V":
        replacements = {"ea_fb_v83_test": "ea_fb_universal_8601_test", "ea_fb_v82_test": "ea_fb_universal_8601_seed"}
    for row in rows:
        if row[1] in [0x1a, 0x1b]:
            for old, new in replacements.items():
                if repr(old) in row[2]:
                    row[2] = row[2].replace(repr(old), repr(new))
                    break
    return rows

def verify_runtime(raw):
    blob = subprocess.check_output(["git", "show", BASELINE + ":dist-v83-test/EA-FB-V83-TEST.cs3"], cwd=ROOT)
    old = Dex(zipfile.ZipFile(io.BytesIO(blob)).read("classes.dex"))
    new = Dex(raw)
    previous = {old.methods[i]: i for i in old.code}
    current = {new.methods[i]: i for i in new.code}
    checked = 0
    for name, index in previous.items():
        if search_method(name):
            continue
        if name not in current or normalized(old, index, name) != normalized(new, current[name], name):
            raise ValueError("Protected V83 runtime method changed: " + name)
        checked += 1
    for name in current.keys() - previous.keys():
        if not search_method(name) and not name.startswith(NEW_CLASSES):
            raise ValueError("Unapproved runtime method added: " + name)
    print(f"PASS: {checked} V83 runtime methods preserved; UI, film search and resolver algorithms retained")
    return checked

def entry(data):
    return dict(fileHash="sha256-"+hashlib.sha256(data).hexdigest(), fileSize=len(data),
                apiVersion=1, repositoryUrl="https://github.com/eaatabay/EA-FB", status=3,
                language="tr", authors=["EA-FB"], tvTypes=["Movie", "TvSeries", "Live"],
                version=VERSION, internalName=ID, name=NAME,
                description="General metadata-based title matcher; Blue CloudStream STABLE test; remove old EA-FB first. Mi Box playback unverified.",
                url=BASE_URL+FILE)

def verify_metadata(data, manifest, feed):
    if manifest.get("version") != VERSION or manifest.get("name") != NAME or manifest.get("internalName") != ID or manifest.get("pluginClassName") != "com.eafb.EAPlugin":
        raise ValueError("Wrong test identity/version")
    if feed != [entry(data)]:
        raise ValueError("Feed hash, size, identity or update URL mismatch")

def verify(out):
    guard.verify(ROOT)
    data = (out/FILE).read_bytes()
    with zipfile.ZipFile(io.BytesIO(data)) as z:
        if z.testzip(): raise ValueError("Corrupt CS3")
        manifest = json.loads(z.read("manifest.json"))
        raw = z.read("classes.dex")
        v83.validate_compiled_scope(raw)
        verify_runtime(raw)
        for name in ["SOURCE-LICENSE", "SOURCE-NOTICE.txt"]:
            if z.read(name) != (out/name).read_bytes(): raise ValueError("Notice mismatch")
    verify_metadata(data, manifest, json.loads((out/'plugins.json').read_text()))
    info = json.loads((out/'build-info.json').read_text())
    if info.get('packageSha256') != hashlib.sha256(data).hexdigest() or info.get('dexSha256') != hashlib.sha256(raw).hexdigest():
        raise ValueError('Build-info hash mismatch')
    if info.get('loadingMode') != 'standard-cloudstream-plugin' or info.get('miBoxPlaybackVerified') is not False:
        raise ValueError('Wrong loading mode or unsupported device success claim')
    subprocess.run(['git','diff','--exit-code',info['sourceCommit'],'HEAD','--','EA-FB'],cwd=ROOT,check=True)
    repo = json.loads((out/'repo.json').read_text())
    if repo.get('pluginLists') != [BASE_URL+'plugins.json']: raise ValueError('Wrong repository manifest URL')
    return hashlib.sha256(data).hexdigest()

def package(source):
    guard.verify(ROOT)
    branch = subprocess.check_output(['git','branch','--show-current'],cwd=ROOT,text=True).strip()
    if branch != BRANCH: raise ValueError('Wrong packaging branch')
    subprocess.run(['git','diff','--exit-code','HEAD','--','EA-FB'],cwd=ROOT,check=True)
    out = ROOT/DIST
    out.mkdir(exist_ok=True)
    notice = (ROOT/'docs/v83/SOURCE-NOTICE.txt').read_text() + '\nThis derivative: '+NAME+'; '+BRANCH+'. Standard plugin loading; blue STABLE test only.\n'
    (out/'SOURCE-NOTICE.txt').write_text(notice)
    (out/'SOURCE-LICENSE').write_bytes((ROOT/'docs/v83/SOURCE-LICENSE').read_bytes())
    with zipfile.ZipFile(source) as z:
        manifest=json.loads(z.read('manifest.json'))
        if manifest.get('version') != VERSION: raise ValueError('Stale build version')
        raw=v83.merge_bronze(z.read('classes.dex'),ROOT)
        v83.validate_compiled_scope(raw)
        count=verify_runtime(raw)
        manifest.update(name=NAME,internalName=ID,authors=['EA-FB'])
        with zipfile.ZipFile(out/FILE,'w',compression=zipfile.ZIP_DEFLATED) as w:
            for name in sorted(z.namelist()):
                info=zipfile.ZipInfo(name,(2026,10,19,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED
                w.writestr(info,json.dumps(manifest,ensure_ascii=False).encode() if name=='manifest.json' else raw if name=='classes.dex' else z.read(name))
            for name in ['SOURCE-LICENSE','SOURCE-NOTICE.txt']:
                info=zipfile.ZipInfo(name,(2026,10,19,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED
                w.writestr(info,(out/name).read_bytes())
    data=(out/FILE).read_bytes()
    (out/'plugins.json').write_text(json.dumps([entry(data)],ensure_ascii=False,indent=2)+'\n')
    (out/'repo.json').write_text(json.dumps(dict(name=NAME,description='Blue CloudStream STABLE test; replace the old Bunny EA-FB plugin',manifestVersion=1,pluginLists=[BASE_URL+'plugins.json']),indent=2)+'\n')
    info=dict(sourceCommit=subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip(),
              baseline=BASELINE,branch=BRANCH,version=VERSION,protectedV83RuntimeMethods=count,
              dexSha256=hashlib.sha256(raw).hexdigest(),packageSha256=hashlib.sha256(data).hexdigest(),
              loadingMode='standard-cloudstream-plugin',miBoxPlaybackVerified=False)
    (out/'build-info.json').write_text(json.dumps(info,indent=2)+'\n')
    print('PASS test package SHA-256:',verify(out))

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('source',nargs='?',type=Path);parser.add_argument('--verify',action='store_true')
    args=parser.parse_args()
    if args.verify: print('PASS verified SHA-256:',verify(ROOT/DIST))
    elif args.source:package(args.source)
    else:parser.error('source CS3 or --verify required')
