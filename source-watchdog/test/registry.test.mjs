import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";
import {
  registerSource, getSource, commitProbe, setEnabled,
  setIntegrationApproval, retestAfterReview, rollbackToLastHealthy,
  buildUnpublishedSnapshot, RegistryConflict,
} from "../src/registry.mjs";
import { HEALTH } from "../src/policy.mjs";
import { claimProbeLease } from "../src/lease.mjs";

// node:sqlite is included on Node >=22. No live D1/database/network is touched.
// Pure policy/snapshot tests still run independently on older Node runtimes.
let DatabaseSync;
try { ({ DatabaseSync } = await import("node:sqlite")); } catch { /* skip below */ }

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
        return { success: true, meta: { changes: Number(info.changes) } };
      },
    });
    return { ...bind(), bind };
  }
  async batch(prepared) {
    this.sql.exec("BEGIN");
    try {
      const results = [];
      for (const statement of prepared) {
        // D1's batch() returns SELECT result arrays in the same order.
        // The registry currently batches read statements only.
        results.push({ success: true, results: (await statement.all()).results });
      }
      this.sql.exec("COMMIT");
      return results;
    } catch (err) {
      this.sql.exec("ROLLBACK");
      throw err;
    }
  }
  close() { this.sql.close(); }
}
const HOUR = 3_600_000;
const source = (id="licensed-demo") => ({
  id, enabled:true, integrationApproved:true, adapterVersion:1,
  mediaKind:"series", currentUrl:"https://demo.example.org",
  lastKnownGoodUrl:"https://demo.example.org",
  verifiedDomains:["demo.example.org","new.example.org"],
  requiredChecks:["reachability","search","detail","episode","playback"],
});
const good = (url="https://demo.example.org") => ({
  reached:true, finalUrl:url, identityVerified:true,
  checks:{search:true, detail:true, episode:true, playback:true},
});
const sqliteTest = DatabaseSync ? test : test.skip;

sqliteTest("D1-compatible registration and 2 probes publish only healthy sources", async () => {
  const db = new SQLiteD1();
  try {
    const created = await registerSource(db,source(),"admin:alice",0);
    assert.equal(created.state.status,HEALTH.DEGRADED);
    assert.equal((await buildUnpublishedSnapshot(db,0)).sources.length,0);
    await commitProbe(db,"licensed-demo","probe-00000001",good(),HOUR);
    assert.equal((await buildUnpublishedSnapshot(db,HOUR)).sources.length,0);
    await commitProbe(db,"licensed-demo","probe-00000002",good(),2*HOUR);
    const snapshot = await buildUnpublishedSnapshot(db,2*HOUR);
    assert.equal(snapshot.sources.length,1);
    assert.equal(snapshot.sources[0].baseUrl,"https://demo.example.org");
    assert.equal(snapshot.revision,3); // provision + 2 checks
    const retry = await commitProbe(db,"licensed-demo","probe-00000001",good(),3*HOUR);
    assert.deepEqual(retry,{duplicate:true,revision:1});
    assert.equal((await buildUnpublishedSnapshot(db,3*HOUR)).revision,3);
  } finally { db.close(); }
});

sqliteTest("one broken and one admin-blocked source never hide 27 healthy sources",async()=>{
  const db = new SQLiteD1();
  try {
    // 30 different *authorized fixture* configurations, no external requests.
    for(let i=1;i<=30;i++){
      const id="source-"+String(i).padStart(2,"0");
      await registerSource(db,source(id),"admin:alice",i);
      if(i<=27){
        await commitProbe(db,id,"probe-"+id+"-00001",good(),HOUR);
        await commitProbe(db,id,"probe-"+id+"-00002",good(),2*HOUR);
      } else if(i===30){
        await commitProbe(db,id,"probe-"+id+"-00001",good("https://unknown.example.org"),HOUR);
      }
    }
    const snapshot=await buildUnpublishedSnapshot(db,2*HOUR);
    assert.equal(snapshot.sources.length,27);
    assert.equal((await getSource(db,"source-30")).state.status,HEALTH.ADMIN_REQUIRED);
  }finally{db.close();}
});

sqliteTest("admin disable and revocation immediately remove a healthy source",async()=>{
  const db=new SQLiteD1();
  try{
    await registerSource(db,source(),"admin:alice",0);
    await commitProbe(db,"licensed-demo","probe-00000001",good(),HOUR);
    await commitProbe(db,"licensed-demo","probe-00000002",good(),2*HOUR);
    await setEnabled(db,"licensed-demo",false,"admin:alice",3*HOUR);
    assert.equal((await buildUnpublishedSnapshot(db,3*HOUR)).sources.length,0);
    await setEnabled(db,"licensed-demo",true,"admin:alice",4*HOUR);
    await commitProbe(db,"licensed-demo","probe-00000003",good(),5*HOUR);
    await commitProbe(db,"licensed-demo","probe-00000004",good(),6*HOUR);
    assert.equal((await buildUnpublishedSnapshot(db,6*HOUR)).sources.length,1);
    await setIntegrationApproval(db,"licensed-demo",false,"ticket/12345","admin:alice",7*HOUR);
    assert.equal((await buildUnpublishedSnapshot(db,7*HOUR)).sources.length,0);
  }finally{db.close();}
});

sqliteTest("approval references reject URLs, traversal and aliases without DB writes",async()=>{
  const db=new SQLiteD1();
  try{
    await registerSource(db,source(),"admin:alice",0);
    const before=db.sql.prepare("SELECT COUNT(*) AS n FROM source_audit").get().n;
    for(const ref of ["https://evil.example.org/permit", "../rights/2026/demo.md",
      "rights/../fake.md", "rights//2026/demo.md", "/rights/2026/demo.md",
      "rights/./demo.md"]) {
      await assert.rejects(setIntegrationApproval(db,"licensed-demo",false,
        ref,"admin:alice",HOUR),/invalid_approval_evidence/,ref);
      await assert.rejects(registerSource(db,{...source("another-demo"),
        approvalRef:ref},"admin:alice",HOUR),/invalid_approval_reference/,ref);
    }
    assert.equal(db.sql.prepare("SELECT COUNT(*) AS n FROM source_audit").get().n,before);
    assert.equal((await getSource(db,"licensed-demo")).revision,0);
  }finally{db.close();}
});

sqliteTest("admin review cannot be bypassed; explicit release requires retesting",async()=>{
  const db=new SQLiteD1();
  try{
    await registerSource(db,source(),"admin:alice",0);
    await commitProbe(db,"licensed-demo","probe-00000001",
      good("https://unapproved.example.org"),HOUR);
    assert.equal((await getSource(db,"licensed-demo")).state.status,HEALTH.ADMIN_REQUIRED);
    const skipped=await commitProbe(db,"licensed-demo","probe-00000002",good(),2*HOUR);
    assert.equal(skipped.skipped,true);
    assert.equal((await buildUnpublishedSnapshot(db,2*HOUR)).sources.length,0);
    await retestAfterReview(db,"licensed-demo","admin:alice",3*HOUR);
    assert.equal((await getSource(db,"licensed-demo")).state.status,HEALTH.DEGRADED);
    await commitProbe(db,"licensed-demo","probe-00000003",good(),4*HOUR);
    assert.equal((await buildUnpublishedSnapshot(db,4*HOUR)).sources.length,0);
    await commitProbe(db,"licensed-demo","probe-00000004",good(),5*HOUR);
    assert.equal((await buildUnpublishedSnapshot(db,5*HOUR)).sources.length,1);
  }finally{db.close();}
});

sqliteTest("rollback only to audited previously healthy approved domain",async()=>{
  const db=new SQLiteD1();
  try{
    await registerSource(db,source(),"admin:alice",0);
    await commitProbe(db,"licensed-demo","probe-00000001",good(),HOUR);
    await commitProbe(db,"licensed-demo","probe-00000002",good(),2*HOUR);
    await commitProbe(db,"licensed-demo","probe-00000003",good("https://new.example.org"),3*HOUR);
    await commitProbe(db,"licensed-demo","probe-00000004",good("https://new.example.org"),4*HOUR);
    assert.equal((await getSource(db,"licensed-demo")).config.currentUrl,"https://new.example.org");
    await rollbackToLastHealthy(db,"licensed-demo","admin:alice",5*HOUR);
    const record=await getSource(db,"licensed-demo");
    assert.equal(record.config.currentUrl,"https://demo.example.org");
    assert.equal(record.state.status,HEALTH.DEGRADED);
    assert.equal((await buildUnpublishedSnapshot(db,5*HOUR)).sources.length,0);
  }finally{db.close();}
});

sqliteTest("the registry never persists unexpected credentials in config or audit",async()=>{
  const db=new SQLiteD1();
  try{
    await registerSource(db,{...source(),apiKey:"DONT_SAVE_ME",cookies:"also_private"},
      "admin:alice",0);
    const stored=db.sql.prepare("SELECT config_json FROM source_registry").get().config_json;
    const audited=db.sql.prepare("SELECT new_config_json FROM source_audit").get().new_config_json;
    assert.ok(!stored.includes("DONT_SAVE_ME")&&!audited.includes("also_private"));
    assert.equal(JSON.parse(stored).currentUrl,"https://demo.example.org");
  }finally{db.close();}
});

sqliteTest("stale CAS does not publish or advance audit revision",async()=>{
  const db=new SQLiteD1();
  try{
    await registerSource(db,source(),"admin:alice",0);
    const revision=await getSource(db,"licensed-demo");
    await commitProbe(db,"licensed-demo","probe-00000001",good(),HOUR);
    const result=db.sql.prepare("UPDATE source_registry SET revision=revision+1 WHERE id=? AND revision=?")
      .run("licensed-demo",revision.revision);
    assert.equal(Number(result.changes),0);
    assert.equal((await buildUnpublishedSnapshot(db,HOUR)).revision,2);
    assert.equal(db.sql.prepare("SELECT COUNT(*) n FROM source_audit").get().n,2);
    await assert.rejects(registerSource(db,source(),"admin:alice",2*HOUR),RegistryConflict);
  }finally{db.close();}
});

sqliteTest("late probe after lease takeover cannot write health, audit or global revision",async()=>{
  const db=new SQLiteD1();
  try{
    await registerSource(db,source(),"admin:alice",0);
    const ownerA="probe-token-owner-a",ownerB="probe-token-owner-b";
    assert.equal(await claimProbeLease(db,"licensed-demo",ownerA,1000,30000),true);
    assert.equal(await claimProbeLease(db,"licensed-demo",ownerB,31000,30000),true);
    await assert.rejects(
      commitProbe(db,"licensed-demo","probe-00000021",good(),HOUR,
        {token:ownerA,checkedAtMs:32000}),
      RegistryConflict,
    );
    assert.equal((await getSource(db,"licensed-demo")).revision,0);
    assert.equal((await buildUnpublishedSnapshot(db,HOUR)).revision,1);
    assert.equal(db.sql.prepare("SELECT COUNT(*) n FROM source_audit").get().n,1);
    const result=await commitProbe(db,"licensed-demo","probe-00000022",good(),HOUR,
      {token:ownerB,checkedAtMs:32000});
    assert.equal(result.duplicate,false);
    assert.equal((await getSource(db,"licensed-demo")).revision,1);
    assert.equal(db.sql.prepare("SELECT COUNT(*) n FROM source_audit").get().n,2);
  }finally{db.close();}
});
