#!/usr/bin/env python3
"""Guarded temporary source overlay for red V6 staging builds only."""
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
STAGING = "https://ea-fb-catalog-v6-staging.eaatabay.workers.dev"
PRODUCTION = "https://ea-fb-catalog.eaatabay.workers.dev"
CONFIG_URL = ("https://raw.githubusercontent.com/eaatabay/EA-FB/"
              "feature/detail-dual-ratings-v6/config/backend.v6-staging.json")
POLICY = ROOT / "EA-FB/src/main/kotlin/com/eafb/CatalogRelayPolicy.kt"
PROVIDER = ROOT / "EA-FB/src/main/kotlin/com/eafb/EAProvider.kt"
SETTINGS = ROOT / "EA-FB/src/main/kotlin/com/eafb/EASettings.kt"
CONFIG = ROOT / "config/backend.v6-staging.json"

def verify():
    config = json.loads(CONFIG.read_text())
    assert config == {"apiBaseUrl": STAGING, "status": "ready"}
    policy = POLICY.read_text()
    provider = PROVIDER.read_text()
    settings = SETTINGS.read_text()
    assert f'const val approvedOrigin = "{STAGING}"' in policy
    assert f'private val catalogConfigUrl = "{CONFIG_URL}"' in provider
    assert PRODUCTION not in policy
    assert 'main/config/backend.json' not in provider
    assert 'override var name = "EA-FB V6 STAGING"' in provider
    assert 'ea_fb_catalog_settings_v6_staging' in settings
    print("PASS: red V6 client pinned exclusively to staging Worker and branch config")

def apply():
    config = json.loads(CONFIG.read_text())
    if config != {"apiBaseUrl": STAGING, "status": "ready"}:
        raise ValueError("Unexpected staging config")
    policy = POLICY.read_text()
    provider = PROVIDER.read_text()
    settings = SETTINGS.read_text()
    old_pin = f'const val approvedOrigin = "{PRODUCTION}"'
    old_config = ('private val catalogConfigUrl = '
                  '"https://raw.githubusercontent.com/eaatabay/EA-FB/main/config/backend.json"')
    old_name = 'override var name = "EA-FB"'
    old_store = 'private const val STORE = "ea_fb_catalog_settings_v1"'
    if (policy.count(old_pin) != 1 or provider.count(old_config) != 1 or
            provider.count(old_name) != 1 or settings.count(old_store) != 1):
        raise ValueError("Production pin or config source differs; refusing source edit")
    POLICY.write_text(policy.replace(old_pin, f'const val approvedOrigin = "{STAGING}"'))
    PROVIDER.write_text(provider.replace(
        old_name, 'override var name = "EA-FB V6 STAGING"').replace(
        old_config, f'private val catalogConfigUrl = "{CONFIG_URL}"'))
    SETTINGS.write_text(settings.replace(
        old_store, 'private const val STORE = "ea_fb_catalog_settings_v6_staging"'))
    verify()

if __name__ == "__main__":
    try:
        verify() if sys.argv[1:] == ["--verify"] else apply() if not sys.argv[1:] else (
            sys.exit("Usage: prepare-v6-staging-overlay.py [--verify]"))
    except (OSError, ValueError, AssertionError) as exc:
        sys.exit(f"BLOCKED: staging overlay check failed: {exc}")
