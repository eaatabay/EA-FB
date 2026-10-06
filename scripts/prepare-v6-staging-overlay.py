#!/usr/bin/env python3
"""Guarded temporary source overlay for RED V6 staging builds only."""
import json
import os
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
STAGING = "https://ea-fb-catalog-v6-staging.eaatabay.workers.dev"
PRODUCTION = "https://ea-fb-catalog.eaatabay.workers.dev"
CONFIG_URL = (
    "https://raw.githubusercontent.com/eaatabay/EA-FB/"
    "feature/detail-dual-ratings-v6/config/backend.v6-staging.json"
)
POLICY = ROOT / "EA-FB/src/main/kotlin/com/eafb/CatalogRelayPolicy.kt"
PROVIDER = ROOT / "EA-FB/src/main/kotlin/com/eafb/EAProvider.kt"
SETTINGS = ROOT / "EA-FB/src/main/kotlin/com/eafb/EASettings.kt"
BUILD = ROOT / "EA-FB/build.gradle.kts"
CONFIG = ROOT / "config/backend.v6-staging.json"
STAGING_VERSION = int(os.environ.get("EA_FB_V6_STAGING_VERSION", "71"))
if STAGING_VERSION != 71:
    raise ValueError("Only new red V71 staging builds are allowed")


def verify():
    config = json.loads(CONFIG.read_text(encoding="utf-8"))
    assert config == {"apiBaseUrl": STAGING, "status": "ready"}
    policy = POLICY.read_text(encoding="utf-8")
    provider = PROVIDER.read_text(encoding="utf-8")
    settings = SETTINGS.read_text(encoding="utf-8")
    build = BUILD.read_text(encoding="utf-8")
    assert f'const val approvedOrigin = "{STAGING}"' in policy
    assert f'private val catalogConfigUrl = "{CONFIG_URL}"' in provider
    assert PRODUCTION not in policy
    assert 'main/config/backend.json' not in provider
    assert 'override var name = "EA-FB V6 STAGING"' in provider
    assert 'ea_fb_catalog_settings_v6_staging' in settings
    assert re.search(rf'^version = {STAGING_VERSION}$', build, re.MULTILINE)
    print("PASS: RED V6 client pinned exclusively to staging Worker and branch config")


def apply():
    config = json.loads(CONFIG.read_text(encoding="utf-8"))
    if config != {"apiBaseUrl": STAGING, "status": "ready"}:
        raise ValueError("Unexpected staging config")
    policy = POLICY.read_text(encoding="utf-8")
    provider = PROVIDER.read_text(encoding="utf-8")
    settings = SETTINGS.read_text(encoding="utf-8")
    build = BUILD.read_text(encoding="utf-8")
    old_pin = f'const val approvedOrigin = "{PRODUCTION}"'
    old_config = (
        'private val catalogConfigUrl = '
        '"https://raw.githubusercontent.com/eaatabay/EA-FB/main/config/backend.json"'
    )
    old_name = 'override var name = "EA-FB"'
    old_store = 'private const val STORE = "ea_fb_catalog_settings_v1"'
    if (policy.count(old_pin) != 1 or provider.count(old_config) != 1 or
            provider.count(old_name) != 1 or settings.count(old_store) != 1 or
            len(re.findall(r'^version = 6$', build, re.MULTILINE)) != 1):
        raise ValueError("Production pin or config source differs; refusing source edit")
    POLICY.write_text(policy.replace(old_pin, f'const val approvedOrigin = "{STAGING}"'),
                      encoding="utf-8")
    PROVIDER.write_text(
        provider.replace(old_name, 'override var name = "EA-FB V6 STAGING"')
        .replace(old_config, f'private val catalogConfigUrl = "{CONFIG_URL}"'),
        encoding="utf-8"
    )
    SETTINGS.write_text(
        settings.replace(old_store,
                         'private const val STORE = "ea_fb_catalog_settings_v6_staging"'),
        encoding="utf-8"
    )
    BUILD.write_text(
        re.sub(r'^version = 6$', f'version = {STAGING_VERSION}', build,
               count=1, flags=re.MULTILINE),
        encoding="utf-8"
    )
    verify()


if __name__ == "__main__":
    try:
        if sys.argv[1:] == ["--verify"]:
            verify()
        elif not sys.argv[1:]:
            apply()
        else:
            sys.exit("Usage: prepare-v6-staging-overlay.py [--verify]")
    except (OSError, ValueError, AssertionError) as exc:
        sys.exit(f"BLOCKED: staging overlay check failed: {exc}")
