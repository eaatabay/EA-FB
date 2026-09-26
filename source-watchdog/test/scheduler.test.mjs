import test from "node:test";
import assert from "node:assert/strict";
import { HEALTH } from "../src/policy.mjs";
import { dueSources, incidentEligible } from "../src/scheduler.mjs";

const record=(id,status,nextCheckAt,lastCheckedAt=null)=>({
  id,
  config:{id,enabled:true,integrationApproved:true},
  state:{id,status,nextCheckAt,lastCheckedAt},
});

test("due scheduling prioritizes degraded, then quarantined, then healthy",()=>{
  const now=1000;
  const rows=[
    record("healthy-a",HEALTH.HEALTHY,100),
    record("quarantine-a",HEALTH.QUARANTINED,100),
    record("degraded-b",HEALTH.DEGRADED,200),
    record("degraded-a",HEALTH.DEGRADED,100),
  ];
  assert.deepEqual(dueSources(rows,now).map(x=>x.id),
    ["degraded-a","degraded-b","quarantine-a","healthy-a"]);
});

test("admin-required, disabled, future and unapproved sources are excluded",()=>{
  const now=1000;
  const rows=[
    record("admin",HEALTH.ADMIN_REQUIRED,0),
    record("disabled",HEALTH.DISABLED,0),
    record("future",HEALTH.HEALTHY,2000),
    record("good",HEALTH.HEALTHY,0),
  ];
  rows.push({...record("unapproved",HEALTH.HEALTHY,0),
    config:{id:"unapproved",enabled:true,integrationApproved:false}});
  assert.deepEqual(dueSources(rows,now).map(x=>x.id),["good"]);
});

test("incident check can bypass six-hour cadence without spamming",()=>{
  const now=10*60*1000;
  const row=record("one",HEALTH.HEALTHY,999999,0);
  assert.equal(incidentEligible(row,now),true);
  row.state.lastCheckedAt=8*60*1000;
  assert.equal(incidentEligible(row,now),false);
  row.state.status=HEALTH.ADMIN_REQUIRED;
  row.state.lastCheckedAt=0;
  assert.equal(incidentEligible(row,now),false);
});


test("unknown health states and malformed clocks never enter scheduled or incident checks",()=>{
  const unknown=record("unknown","future_unreviewed_status",0);
  const fractional=record("fractional",HEALTH.DEGRADED,0.5);
  const negative=record("negative",HEALTH.HEALTHY,-1);
  const infinite=record("infinite",HEALTH.HEALTHY,Infinity);
  assert.deepEqual(dueSources([unknown,fractional,negative,infinite],1000),[]);
  for(const row of [unknown,fractional,negative,infinite]){
    if(row===unknown) assert.equal(incidentEligible(row,1000),false);
  }
  const malformed=record("bad-clock",HEALTH.DEGRADED,0,1.5);
  assert.equal(incidentEligible(malformed,1000),false);
  malformed.state.lastCheckedAt=-1;
  assert.equal(incidentEligible(malformed,1000),false);
});
