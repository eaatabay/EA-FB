import test from "node:test";
import assert from "node:assert/strict";
import {
  HEALTH, normalizedHttpsUrl, validateSource, initialState, evaluateProbe,
  applyProbe, releaseForRetest, searchableSourceIds,
} from "../src/policy.mjs";

const HOUR = 3_600_000;
const source = {
  id: "licensed-demo", enabled: true, mediaKind: "series",
  currentUrl: "https://demo.example.org",
  lastKnownGoodUrl: "https://demo.example.org",
  verifiedDomains: ["demo.example.org", "new.example.org"],
  requiredChecks: ["reachability", "search", "detail", "episode", "playback"],
};
const good = (finalUrl=source.currentUrl) => ({
  reached: true, finalUrl, identityVerified: true,
  checks: {search:true, detail:true, episode:true, playback:true},
});

test("normalizes HTTPS; rejects private/local URLs and URL credentials", () => {
  assert.equal(normalizedHttpsUrl("https://Demo.Example.org/"), "https://demo.example.org");
  for (const url of ["http://demo.example.org", "https://localhost",
    "https://192.168.1.1", "https://demo.example.org:8443",
    "https://user:pass@demo.example.org", "https://demo.example.org?a=1",
    "https://demo.example.org#x", "https://host.internal",
    "https://demo.example.org:443", "https://demo%2eexample.org",
    "https://demo.example.org?", "https://demo.example.org#",
    "https://demo.example.org/%2e%2e/private",
    "https://demo.example.org//private",
    "https://demo.example.org/../private",
    "https://-bad.example.org", "https://bad-.example.org",
    "https://"+"a".repeat(64)+".example.org"]) {
    assert.equal(normalizedHttpsUrl(url), null, url);
  }
});
test("configuration requires approved hosts and real search/detail checks", () => {
  assert.equal(validateSource(source).id, source.id);
  assert.throws(() => validateSource({...source, currentUrl:"https://other.example.org"}));
  assert.throws(() => validateSource({...source, verifiedDomains:["demo.example.org","demo.example.org"]}));
  assert.throws(() => validateSource({...source, verifiedDomains:["demo.example.org","Bad.example.org"]}));
  assert.throws(() => validateSource({...source, requiredChecks:["reachability"]}));
  assert.throws(() => validateSource({...source, requiredChecks:["search","detail"]}));
});
test("HTTP 200 with empty search is a failure", () => {
  const result = evaluateProbe(source, {...good(), checks:{...good().checks,search:false}});
  assert.equal(result.ok, false);
  assert.equal(result.reason, "functional_check_failed");
});
test("a suspicious redirect requires admin, no automatic address change", () => {
  const result = evaluateProbe(source, good("https://not-approved.example.org"));
  assert.equal(result.needsAdmin, true);
  let state = applyProbe(source, initialState(source, 0),
    good("https://not-approved.example.org"), HOUR);
  assert.equal(state.status, HEALTH.ADMIN_REQUIRED);
  assert.equal(state.currentUrl, source.currentUrl);
  assert.equal(state.nextCheckAt, null);
  state = applyProbe(source, state, good(), 2 * HOUR);
  assert.equal(state.status, HEALTH.ADMIN_REQUIRED);
});
test("the first transient failure degrades, the second quarantines", () => {
  let state = initialState(source, 0);
  const fail = {...good(), reached:false};
  state = applyProbe(source, state, fail, HOUR);
  assert.equal(state.status, HEALTH.DEGRADED);
  assert.equal(state.nextCheckAt, 2*HOUR);
  state = applyProbe(source, state, fail, 2*HOUR);
  assert.equal(state.status, HEALTH.QUARANTINED);
  assert.equal(state.nextCheckAt, 14*HOUR);
  assert.equal(state.lastKnownGoodUrl, source.currentUrl);
});
test("a returning source joins searches only after two successful checks", () => {
  let state = initialState(source, 0);
  assert.deepEqual(searchableSourceIds([state]), []);
  state = applyProbe(source, state, good(), HOUR);
  assert.equal(state.status, HEALTH.DEGRADED);
  state = applyProbe(source, state, good(), 2*HOUR);
  assert.equal(state.status, HEALTH.HEALTHY);
  assert.equal(state.nextCheckAt, 8*HOUR);
  assert.deepEqual(searchableSourceIds([state]), [source.id]);
});
test("healthy source stays healthy across subsequent checks", () => {
  let state = initialState(source, 0);
  state = applyProbe(source, state, good(), HOUR);
  state = applyProbe(source, state, good(), 2 * HOUR);
  state = applyProbe(source, state, good(), 8 * HOUR);
  assert.equal(state.status, HEALTH.HEALTHY);
  assert.equal(state.nextCheckAt, 14 * HOUR);
});

test("a verified domain move needs two consistent successful probes", () => {
  let state = initialState(source, 0);
  state = applyProbe(source,state,good("https://new.example.org"),HOUR);
  assert.equal(state.currentUrl,source.currentUrl);
  assert.equal(state.candidateUrl,"https://new.example.org");
  state = applyProbe(source,state,good("https://new.example.org"),2*HOUR);
  assert.equal(state.currentUrl,"https://new.example.org");
  assert.equal(state.lastKnownGoodUrl,"https://new.example.org");
  assert.equal(state.candidateUrl,null);
});
test("flapping redirects do not accumulate approval", () => {
  let state = initialState(source,0);
  state = applyProbe(source,state,good("https://new.example.org"),HOUR);
  state = applyProbe(source,state,good(),2*HOUR);
  assert.equal(state.currentUrl, source.currentUrl);
  assert.equal(state.consecutiveSuccesses,1);
  assert.equal(state.candidateUrl,null);
});
test("structural changes request operator review; manual release starts retest", () => {
  let state = applyProbe(source,initialState(source,0),
    {...good(),structuralChange:true},HOUR);
  assert.equal(state.status,HEALTH.ADMIN_REQUIRED);
  state = releaseForRetest(source,state,2*HOUR);
  assert.equal(state.status,HEALTH.DEGRADED);
  assert.equal(state.nextCheckAt,2*HOUR);
});
test("disabled source cannot be scheduled or returned as searchable", () => {
  const disabled = {...source,enabled:false};
  const state = initialState(disabled,0);
  assert.equal(state.status,HEALTH.DISABLED);
  assert.equal(state.nextCheckAt,null);
  assert.deepEqual(searchableSourceIds([state]),[]);
});
test("one broken source never suppresses independent healthy sources", () => {
  const ok = {...initialState(source,0),status:HEALTH.HEALTHY,id:"one"};
  const broken = {...ok,status:HEALTH.QUARANTINED,id:"two"};
  const admin = {...ok,status:HEALTH.ADMIN_REQUIRED,id:"three"};
  assert.deepEqual(searchableSourceIds([broken,ok,admin]),["one"]);
});
