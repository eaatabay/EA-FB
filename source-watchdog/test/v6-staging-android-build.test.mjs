import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
const read = p => readFileSync(new URL("../../" + p, import.meta.url), "utf8");
test("red V6 staging build overlays only the two Android relay references", () => {
  const script=read("scripts/build-v6-staging-codespace.sh");
  const overlay=read("scripts/prepare-v6-staging-overlay.py");
  const cfg=JSON.parse(read("config/backend.v6-staging.json"));
  assert.deepEqual(cfg,{
    apiBaseUrl:"https://ea-fb-catalog-v6-staging.eaatabay.workers.dev",
    status:"ready"
  });
  assert.match(script,/git branch --show-current/);
  assert.match(script,/git status --porcelain/);
  assert.match(script,/trap restore EXIT/);
  assert.match(script,/build\/v6-staging-artifacts\/EA-FB-V6-STAGING\.cs3/);
  assert.match(script,/bash scripts\/build-codespace\.sh/);
  assert.match(overlay,/policy\.count\(old_pin\) != 1/);
  assert.match(overlay,/provider\.count\(old_config\) != 1/);
  assert.match(overlay,/config\/backend\.v6-staging\.json/);
  assert.doesNotMatch(script,/wrangler deploy|git push|gh release/);
});
test("normal plugin retains production relay pin until explicit staging build",()=>{
  assert.match(read("EA-FB/src/main/kotlin/com/eafb/CatalogRelayPolicy.kt"),
    /const val approvedOrigin = "https:\/\/ea-fb-catalog\.eaatabay\.workers\.dev"/);
  assert.match(read("EA-FB/src/main/kotlin/com/eafb/EAProvider.kt"),
    /main\/config\/backend\.json/);
});

test("V6 keeps disabled shelves registered so later re-enabling can restore them", () => {
  const provider = read("EA-FB/src/main/kotlin/com/eafb/EAProvider.kt");
  const settings = read("EA-FB/src/main/kotlin/com/eafb/EASettings.kt");
  const dialog = read("EA-FB/src/main/kotlin/com/eafb/EASettingsDialog.kt");
  assert.match(provider, /\*categories\.map \{ "\$\{it\.id\}\|cfg=\$\{EASettings\.homeRevision\(\)\}" to it\.title \}\.toTypedArray\(\)/);
  assert.doesNotMatch(provider, /categories\.filter \{ EASettings\.categoryEnabled\(it\.id\) \}/);
  assert.match(provider, /if \(!EASettings\.categoryEnabled\(category\.id\)\)/);
  assert.match(settings, /fun setAllCategories\(enabled: Boolean\)/);
  assert.match(settings, /fun setCategoryEnabled\(id: String, enabled: Boolean\)/);
  assert.match(settings, /fun homeRevision\(\): String/);
  assert.match(provider, /request\.data\.substringBefore\("\\|cfg="\)/);
  assert.match(dialog, /EASettings\.setCategoryEnabled\(category\.id, state\)/);
});

test("Apple and Paramount switches retain separate movie/series routes and do not borrow other platforms", () => {
  const domain = read("EA-FB/src/main/kotlin/com/eafb/Domain.kt");
  const provider = read("EA-FB/src/main/kotlin/com/eafb/EAProvider.kt");
  for (const [id, media, providerId] of [
    ["apple-movie", "movie", "350"],
    ["apple-tv", "tv", "350"],
    ["paramount-movie", "movie", "531"],
    ["paramount-tv", "tv", "531"],
  ]) {
    assert.ok(domain.includes('CatalogCategory("' + id + '"'), id + " is missing");
    const line = domain.split("\n").find(line => line.includes('CatalogCategory("' + id + '"'));
    assert.ok(line?.includes("/discover/" + media + "?with_watch_providers=" + providerId + "&watch_region=TR"), id + " route mismatch");
  }
  assert.match(provider, /if \(results\.isEmpty\(\)\) return newHomePageResponse\(emptyList\(\), false\)/);
  assert.match(provider, /route != category\.tmdbPath && category\.tmdbPath\.startsWith\("\/discover\/"\)/);
});
