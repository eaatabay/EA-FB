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

test("signed same-revision refresh requires a strictly newer generation",()=>{
  const a=source("source-one");
  const current=buildClientSnapshot([a],[healthy(a.id)],42,now);
  const fresh=buildClientSnapshot([a],[healthy(a.id)],42,now+60_000);
  assert.equal(canReplaceSnapshot(current,fresh,now+60_000,true),true);
  assert.equal(canReplaceSnapshot(current,fresh,now+60_000,false),false);
  assert.equal(canReplaceSnapshot(fresh,fresh,now+60_000,true),false);
  assert.equal(canReplaceSnapshot({...current,generatedAt:null},fresh,now+60_000,true),false);
  assert.equal(canReplaceSnapshot({...current,revision:43},fresh,now+60_000,true),false);
  assert.equal(canReplaceSnapshot(null,fresh,now+60_000,true),true);
});

test("signed refresh rejects malformed payload, duplicate IDs and future or stale times",()=>{
  const a=source("source-one");
  const current=buildClientSnapshot([a],[healthy(a.id)],42,now);
  const makeFresh=()=>buildClientSnapshot([a],[healthy(a.id)],43,now+60_000);
  const mutations=[
    s=>s.sources.push({...s.sources[0]}),
    s=>s.sources[0].baseUrl="http://licensed.example.org",
    s=>s.sources[0].baseUrl="https://127.0.0.1",
    s=>s.sources[0].mediaKind="unknown",
    s=>s.sources[0].adapterVersion=0,
    s=>s.sources[0].secret="leak",
    s=>s.debug="leak",
    s=>s.generatedAt=now+600_000,
    s=>s.expiresAt=s.generatedAt+3_600_001,
    s=>s.revision=-1,
    s=>s.schemaVersion=2,
  ];
  for(const mutate of mutations){
    const incoming=makeFresh();
    mutate(incoming);
    assert.equal(canReplaceSnapshot(current,incoming,now+60_000,true),false,
      JSON.stringify(incoming));
  }
  const expired=makeFresh();
  assert.equal(canReplaceSnapshot(current,expired,expired.expiresAt,true),false);
});


test("publisher rejects fractional, negative and overflowing snapshot clocks",()=>{
  const a=source("source-one");
  for(const clock of [-1,0.5,Infinity,Number.MAX_SAFE_INTEGER]){
    assert.throws(()=>buildClientSnapshot([a],[healthy(a.id)],1,clock),
      /invalid_snapshot_(input|lifetime)/,String(clock));
  }
  for(const options of [
    {ttlMs:0.5},{ttlMs:Infinity},{ttlMs:3_600_001},
    {maxHealthAgeMs:0.5},{maxHealthAgeMs:Infinity},
    {maxHealthAgeMs:48*3_600_000+1},
  ])assert.throws(()=>buildClientSnapshot([a],[healthy(a.id)],1,now,options),
    /invalid_snapshot_lifetime/,JSON.stringify(options));
});

test("publisher never emits a healthy state whose URL differs from approved config",()=>{
  const a=source("source-one");
  const changed={...healthy(a.id),currentUrl:"https://new.example.org"};
  assert.equal(buildClientSnapshot([a],[changed],1,now).sources.length,0);
  const b={...a,verifiedDomains:[...a.verifiedDomains,"new.example.org"]};
  assert.equal(buildClientSnapshot([b],[changed],1,now).sources.length,0);
  const approved={...b,currentUrl:"https://new.example.org"};
  assert.equal(buildClientSnapshot([approved],[changed],1,now).sources.length,1);
});

test("publisher rejects noninteger health times and unsupported adapter versions",()=>{
  const a=source("source-one");
  for(const bad of [-1,0.5,Infinity]){
    const state={...healthy(a.id),lastCheckedAt:bad};
    assert.equal(buildClientSnapshot([a],[state],1,now).sources.length,0);
  }
  assert.throws(()=>buildClientSnapshot([{...a,adapterVersion:1_000_001}],
    [healthy(a.id)],1,now),/invalid_adapter_version/);
});
