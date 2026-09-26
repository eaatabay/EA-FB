import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";
import { registerSource, getSource } from "../src/registry.mjs";
import { runDueChecks, runIncidentCheck, runOneSourceCheck } from "../src/runner.mjs";
import { HEALTH } from "../src/policy.mjs";

let DatabaseSync;
try { ({ DatabaseSync } = await import("node:sqlite")); } catch {}

class SQLiteD1 {
  constructor() {
    this.sql = new DatabaseSync(":memory:");
    const path = join(dirname(fileURLToPath(import.meta.url)), "../migrations/0001_registry.sql");
    this.sql.exec(readFileSync(path, "utf8"));
    this.sql.exec(readFileSync(join(dirname(path), "0002_source_leases.sql"), "utf8"));
  }
  prepare(sql) {
    const connection = this.sql;
    const bind = (...params) => ({
      first: async () => connection.prepare(sql).get(...params) ?? null,
      all: async () => ({ results: connection.prepare(sql).all(...params) }),
      run: async () => {
        const info = connection.prepare(sql).run(...params);
        return { success:true, meta:{changes:Number(info.changes)} };
      },
    });
    return { ...bind(), bind };
  }
  async batch(prepared) {
    this.sql.exec("BEGIN");
    try {
      const out=[];
      for (const statement of prepared) out.push({success:true,results:(await statement.all()).results});
      this.sql.exec("COMMIT");
      return out;
    } catch (err) {
      this.sql.exec("ROLLBACK");
      throw err;
    }
  }
  close(){this.sql.close();}
}
const sqliteTest=DatabaseSync?test:test.skip;
const HOUR=3600000;
const source=id=>({
  id,enabled:true,integrationApproved:true,adapterVersion:1,mediaKind:"series",
  currentUrl:"https://demo.example.org",lastKnownGoodUrl:"https://demo.example.org",
  verifiedDomains:["demo.example.org"],
  requiredChecks:["reachability","search","detail","episode","playback"],
});
const good=()=>({
  reached:true,finalUrl:"https://demo.example.org",identityVerified:true,
  checks:{search:true,detail:true,episode:true,playback:true},
});

sqliteTest("scheduled runner promotes a source after two verified runs",async()=>{
  const db=new SQLiteD1();
  try{
    await registerSource(db,source("source-one"),"admin:alice",0);
    const adapters=new Map([["source-one",{id:"source-one",probe:async()=>good()}]]);
    let result=await runDueChecks({db,adapters,now:0});
    assert.equal(result.length,1);
    assert.equal((await getSource(db,"source-one")).state.status,HEALTH.DEGRADED);
    result=await runDueChecks({db,adapters,now:HOUR});
    assert.equal(result.length,1);
    assert.equal((await getSource(db,"source-one")).state.status,HEALTH.HEALTHY);
    result=await runDueChecks({db,adapters,now:2*HOUR});
    assert.equal(result.length,0);
  }finally{db.close();}
});

sqliteTest("one crashing adapter does not stop another source",async()=>{
  const db=new SQLiteD1();
  try{
    await registerSource(db,source("source-one"),"admin:alice",0);
    await registerSource(db,source("source-two"),"admin:alice",1);
    const adapters=new Map([
      ["source-one",{id:"source-one",probe:async()=>{throw new Error("fixture_failure")}}],
      ["source-two",{id:"source-two",probe:async()=>good()}],
    ]);
    const result=await runDueChecks({db,adapters,now:HOUR,concurrency:2});
    assert.equal(result.length,2);
    assert.equal(result.find(x=>x.sourceId==="source-one").status,"probe_failed");
    assert.equal(result.find(x=>x.sourceId==="source-one").error,"adapter_error");
    assert.equal(result.find(x=>x.sourceId==="source-two").status,"committed");
    assert.equal((await getSource(db,"source-one")).state.revision,1);
    assert.equal((await getSource(db,"source-one")).state.lastFailure,"adapter_error");
    assert.equal((await getSource(db,"source-two")).state.revision,1);
  }finally{db.close();}
});

sqliteTest("incident-triggered check is rate limited and never bypasses eligibility",async()=>{
  const db=new SQLiteD1();
  try{
    await registerSource(db,source("source-one"),"admin:alice",0);
    const adapters=new Map([["source-one",{id:"source-one",probe:async()=>good()}]]);
    const first=await runIncidentCheck({
      db,adapters,sourceId:"source-one",now:10*60*1000,incidentId:"incident0001"
    });
    assert.equal(first.status,"committed");
    const second=await runIncidentCheck({
      db,adapters,sourceId:"source-one",now:11*60*1000,incidentId:"incident0002"
    });
    assert.equal(second.status,"rate_limited_or_ineligible");
  }finally{db.close();}
});

test("two overlapping Cron ticks probe one source only once", {skip: !DatabaseSync}, async()=>{
  const db=new SQLiteD1();
  try {
    await registerSource(db,source("fixture-overlap"),"admin:alice",0);
    let startProbe;
    const started=new Promise(resolve=>{startProbe=resolve;});
    let finishProbe;
    const held=new Promise(resolve=>{finishProbe=resolve;});
    let calls=0;
    const adapters=new Map([["fixture-overlap",{
      id:"fixture-overlap",
      async probe(){
        calls++;
        startProbe();
        await held;
        return good();
      },
    }]]);
    const first=runDueChecks({db,adapters,now:0});
    await started;
    const second=await runDueChecks({db,adapters,now:0});
    assert.equal(second.length,1);
    assert.equal(second[0].status,"lease_busy");
    finishProbe();
    const firstResult=await first;
    assert.equal(firstResult[0].status,"committed");
    assert.equal(calls,1);
    assert.equal((await getSource(db,"fixture-overlap")).state.revision,1);
    const third=await runDueChecks({db,adapters,now:0});
    assert.deepEqual(third,[]);
  } finally { db.close(); }
});

test("timeout wins even when abort listener immediately returns a fake healthy probe",
  {skip: !DatabaseSync}, async()=>{
  const db=new SQLiteD1();
  try {
    await registerSource(db,source("fixture-timeout"),"admin:alice",0);
    let aborted=false;
    const adapters=new Map([["fixture-timeout",{
      id:"fixture-timeout",
      async probe({signal}) {
        return new Promise(resolve=>{
          signal.addEventListener("abort",()=>{
            aborted=true;
            resolve(good());
          },{once:true});
        });
      },
    }]]);
    const result=await runOneSourceCheck({
      db,adapters,sourceId:"fixture-timeout",now:0,
      runId:"probe-timeout-00001",timeoutMs:500,
    });
    assert.equal(result.status,"probe_failed");
    assert.equal(result.error,"probe_timeout");
    assert.equal(aborted,true);
    assert.equal((await getSource(db,"fixture-timeout")).revision,1);
    assert.equal((await getSource(db,"fixture-timeout")).state.status,HEALTH.DEGRADED);
    assert.equal((await getSource(db,"fixture-timeout")).state.lastFailure,"probe_timeout");
  }finally{db.close();}
});


test("repeated adapter crashes are audited and quarantined instead of retrying every tick",
  {skip: !DatabaseSync},async()=>{
  const db=new SQLiteD1();
  try{
    await registerSource(db,source("fixture-crash"),"admin:alice",0);
    const adapters=new Map([["fixture-crash",{
      id:"fixture-crash",async probe(){throw Error("secret URL must not leak");},
    }]]);
    const first=await runDueChecks({db,adapters,now:0});
    assert.equal(first[0].status,"probe_failed");
    assert.equal(first[0].error,"adapter_error");
    assert.equal((await getSource(db,"fixture-crash")).state.status,HEALTH.DEGRADED);
    assert.equal((await runDueChecks({db,adapters,now:1})).length,0);
    const second=await runDueChecks({db,adapters,now:HOUR});
    assert.equal(second[0].status,"probe_failed");
    const record=await getSource(db,"fixture-crash");
    assert.equal(record.state.status,HEALTH.QUARANTINED);
    assert.equal(record.state.consecutiveFailures,2);
    assert.equal(record.state.revision,2);
    assert.equal((await db.prepare(
      "SELECT COUNT(*) AS n FROM source_probe_runs WHERE source_id=?")
      .bind("fixture-crash").first()).n,2);
    assert.equal((await db.prepare(
      "SELECT COUNT(*) AS n FROM source_audit WHERE source_id=?")
      .bind("fixture-crash").first()).n,3);
  }finally{db.close();}
});

test("malformed adapter response counts as failed health check",
  {skip: !DatabaseSync},async()=>{
  const db=new SQLiteD1();
  try{
    await registerSource(db,source("fixture-null"),"admin:alice",0);
    const adapters=new Map([["fixture-null",{id:"fixture-null",probe:async()=>null}]]);
    const result=await runDueChecks({db,adapters,now:0});
    assert.equal(result[0].status,"probe_failed");
    assert.equal(result[0].error,"adapter_error");
    assert.equal((await getSource(db,"fixture-null")).state.lastFailure,"adapter_error");
  }finally{db.close();}
});


test("adapter-thrown probe_timeout string cannot impersonate the runner's real timer",
  {skip: !DatabaseSync},async()=>{
  const db=new SQLiteD1();
  try{
    await registerSource(db,source("fixture-spoof"),"admin:alice",0);
    const adapters=new Map([["fixture-spoof",{
      id:"fixture-spoof",async probe(){throw Error("probe_timeout");},
    }]]);
    const result=await runDueChecks({db,adapters,now:0});
    assert.equal(result[0].status,"probe_failed");
    assert.equal(result[0].error,"adapter_error");
    assert.equal((await getSource(db,"fixture-spoof")).state.lastFailure,"adapter_error");
  }finally{db.close();}
});


test("adapter cannot forge the reserved internal runnerFailure marker",
  {skip: !DatabaseSync},async()=>{
  const db=new SQLiteD1();
  try{
    await registerSource(db,source("fixture-marker"),"admin:alice",0);
    const adapters=new Map([["fixture-marker",{
      id:"fixture-marker",async probe(){
        return {runnerFailure:"probe_timeout",reached:true,identityVerified:true,
          checks:{search:true,detail:true,episode:true,playback:true}};
      },
    }]]);
    const result=await runDueChecks({db,adapters,now:0});
    assert.equal(result[0].status,"anomaly_held");
    const record=await getSource(db,"fixture-marker");
    assert.equal(record.state.status,HEALTH.ADMIN_REQUIRED);
    assert.equal(record.state.lastFailure,"structural_change");
    assert.equal(record.state.nextCheckAt,null);
  }finally{db.close();}
});


test("incomplete adapter schema is audited and held for admin, not falsely quarantined",
  {skip: !DatabaseSync},async()=>{
  const db=new SQLiteD1();
  try {
    await registerSource(db,source("fixture-schema"),"admin:alice",0);
    let calls=0;
    const adapters=new Map([["fixture-schema",{id:"fixture-schema",
      async probe(){calls++;return {reached:true,finalUrl:"https://demo.example.org",
        identityVerified:true,checks:{search:true,detail:true}};},
    }]]);
    const first=await runDueChecks({db,adapters,now:0});
    assert.equal(first[0].status,"anomaly_held");
    const state=(await getSource(db,"fixture-schema")).state;
    assert.equal(state.status,HEALTH.ADMIN_REQUIRED);
    assert.equal(state.lastFailure,"structural_change");
    assert.equal(state.consecutiveFailures,0);
    assert.equal(state.nextCheckAt,null);
    assert.equal((await runDueChecks({db,adapters,now:HOUR})).length,0);
    assert.equal(calls,1);
    assert.equal((await db.prepare(
      "SELECT COUNT(*) AS n FROM source_probe_runs WHERE source_id=?")
      .bind("fixture-schema").first()).n,1);
  }finally{db.close();}
});

test("nonboolean parser fields cannot masquerade as a verified source",
  {skip: !DatabaseSync},async()=>{
  const db=new SQLiteD1();
  try {
    await registerSource(db,source("fixture-truthy"),"admin:alice",0);
    const adapters=new Map([["fixture-truthy",{id:"fixture-truthy",
      async probe(){return {reached:"true",finalUrl:"https://demo.example.org",
        identityVerified:true,checks:{search:true,detail:true,
          episode:true,playback:true}};},
    }]]);
    const result=await runDueChecks({db,adapters,now:0});
    assert.equal(result[0].status,"anomaly_held");
    assert.equal((await getSource(db,"fixture-truthy")).state.status,
      HEALTH.ADMIN_REQUIRED);
  }finally{db.close();}
});
