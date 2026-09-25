import test from "node:test";
import assert from "node:assert/strict";
import { HEALTH, initialState } from "../src/policy.mjs";
import { buildClientSnapshot, canReplaceSnapshot } from "../src/snapshot.mjs";

const now=20 * 3_600_000;
const source=(id)=>({
  id, enabled:true, integrationApproved:true, mediaKind:"movie",
  currentUrl:"https://licensed.example.org",
  lastKnownGoodUrl:"https://licensed.example.org",
  verifiedDomains:["licensed.example.org"],
  requiredChecks:["reachability","search","detail","playback"],
  adapterVersion:1,
});
const healthy=(id)=>({
  ...initialState(source(id),0),
  status:HEALTH.HEALTHY,
  lastCheckedAt:now-3_600_000,
});

test("27 healthy sources remain available while two are broken and one awaits admin",()=>{
  const sources=Array.from({length:30},(_,i)=>source("source-"+String(i+1).padStart(2,"0")));
  const states=sources.map(x=>healthy(x.id));
  states[27].status=HEALTH.QUARANTINED;
  states[28].status=HEALTH.DEGRADED;
  states[29].status=HEALTH.ADMIN_REQUIRED;
  const snapshot=buildClientSnapshot(sources,states,4,now);
  assert.equal(snapshot.sources.length,27);
  assert.equal(snapshot.revision,4);
  assert.equal(snapshot.expiresAt,now+900_000);
  assert.ok(!snapshot.sources.some(x=>["source-28","source-29","source-30"].includes(x.id)));
});
test("returning a source to healthy makes it available in the next snapshot",()=>{
  const a=source("source-one");
  const state={...healthy(a.id),status:HEALTH.ADMIN_REQUIRED};
  assert.equal(buildClientSnapshot([a],[state],1,now).sources.length,0);
  state.status=HEALTH.HEALTHY;
  assert.equal(buildClientSnapshot([a],[state],2,now).sources.length,1);
});
test("never publish unapproved adapters or stale health results",()=>{
  const a=source("source-one"),b=source("source-two");
  b.integrationApproved=false;
  const old=healthy(a.id);old.lastCheckedAt=now-13*3_600_000;
  assert.deepEqual(buildClientSnapshot([a,b],[old,healthy(b.id)],1,now).sources,[]);
});
test("snapshot leaks no failure details or unapproved redirect candidates",()=>{
  const a=source("source-one");
  const state={...healthy(a.id),candidateUrl:"https://unapproved.example.org",
    lastFailure:"private diagnostic string"};
  const serialized=JSON.stringify(buildClientSnapshot([a],[state],1,now));
  assert.ok(!serialized.includes("candidateUrl"));
  assert.ok(!serialized.includes("private diagnostic"));
  assert.ok(!serialized.includes("unapproved.example.org"));
});
test("requires distinct source IDs and immutable monotonic revisions",()=>{
  const a=source("source-one");
  assert.throws(()=>buildClientSnapshot([a,a],[healthy(a.id)],1,now));
  const snap=buildClientSnapshot([a],[healthy(a.id)],4,now);
  assert.equal(canReplaceSnapshot(null,snap,now),false); // unsigned snapshot cannot replace
  assert.equal(canReplaceSnapshot(null,snap,now,true),true);
  assert.equal(canReplaceSnapshot(snap,snap,now,true),false);
  assert.equal(canReplaceSnapshot(null,snap,snap.expiresAt,true),false);
});
