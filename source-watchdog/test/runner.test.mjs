import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";
import { registerSource, getSource } from "../src/registry.mjs";
import { runDueChecks, runIncidentCheck } from "../src/runner.mjs";
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
    assert.equal(result.find(x=>x.sourceId==="source-one").status,"runner_error");
    assert.equal(result.find(x=>x.sourceId==="source-two").status,"committed");
    assert.equal((await getSource(db,"source-one")).state.revision,0);
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
