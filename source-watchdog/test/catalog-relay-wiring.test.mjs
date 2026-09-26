import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync} from "node:fs";

const root = new URL("../../", import.meta.url);
const kotlin = new URL("EA-FB/src/main/kotlin/com/eafb/", root);
const provider = readFileSync(new URL("EAProvider.kt", kotlin), "utf8");
const policy = readFileSync(new URL("CatalogRelayPolicy.kt", kotlin), "utf8");
const backend = JSON.parse(readFileSync(new URL("config/backend.json", root), "utf8"));

test("tracked backend points to the reviewed compiled Worker origin", () => {
  assert.equal(backend.status, "ready");
  assert.equal(backend.apiBaseUrl, "https://ea-fb-catalog.eaatabay.workers.dev");
  assert.match(policy, /const val approvedOrigin = "https:\/\/ea-fb-catalog\.eaatabay\.workers\.dev"/);
});
test("Android provider must validate remote relay config against compiled pin", () => {
  assert.match(provider, /CatalogRelayPolicy\.approved\(\s*config\.optString\("apiBaseUrl"\),\s*config\.optString\("status"\)/);
  assert.doesNotMatch(provider, /val url = config\.optString\("apiBaseUrl"\)\.trim\(\)/);
});
test("temporary config outage never trusts an unreviewed or unbounded cached relay", () => {
  assert.match(provider, /CatalogRelayPolicy\.usableCached\(cached, relayCheckedAt, now\)/);
  assert.match(policy, /private const val MAX_STALE_MS = 24 \* 60 \* 60_000L/);
  assert.match(policy, /origin != approvedOrigin/);
});
