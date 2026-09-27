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
