import test from "node:test";
import assert from "node:assert/strict";
import { execFileSync } from "node:child_process";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";
import { createWatchdogWorker } from "../src/worker.mjs";
import { createFixtureAdapters } from "../src/fixtures.mjs";
import { getSource, retestAfterReview, buildUnpublishedSnapshot } from "../src/registry.mjs";
import { HEALTH } from "../src/policy.mjs";

let DatabaseSync;
try { ({ DatabaseSync } = await import("node:sqlite")); } catch {}
const root = join(dirname(fileURLToPath(import.meta.url)), "..");
class LocalD1 {
  constructor() {
    this.sql = new DatabaseSync(":memory:");
    this.sql.exec(readFileSync(join(root,"migrations/0001_registry.sql"),"utf8"));
    this.sql.exec(readFileSync(join(root,"migrations/0002_source_leases.sql"),"utf8"));
    const seed=execFileSync(process.execPath,[join(root,"dev/generate-fixture-seed.mjs")],
      {encoding:"utf8"});
    this.sql.exec(seed);
  }
  prepare(sql) {
    const raw=this.sql;
    function bind(...params) {
      return {
        first:async()=>raw.prepare(sql).get(...params)??null,
        all:async()=>({results:raw.prepare(sql).all(...params)}),
        run:async()=>{
          const info=raw.prepare(sql).run(...params);
          return {success:true,meta:{changes:Number(info.changes)}};
        },
      };
    }
    return {...bind(),bind};
  }
  async batch(statements) {
    this.sql.exec("BEGIN");
    try {
      const result=[];
      for(const statement of statements) result.push({results:(await statement.all()).results});
      this.sql.exec("COMMIT");
      return result;
    }catch(error){this.sql.exec("ROLLBACK");throw error;}
  }
  close(){this.sql.close();}
}
const sqliteTest=DatabaseSync?test:test.skip;
const QUARTER=15*60*1000, HOUR=60*60*1000;
const env=db=>({
  SOURCES_DB:db,
  WATCHDOG_MODE:"fixture",
  WATCHDOG_CRON_ENABLED:"true",
  WATCHDOG_FIXTURE_ENABLED:"true",
});

sqliteTest("full offline 30-source Cron sequence keeps structural failure isolated until admin fixes it",
  async()=>{
  const db=new LocalD1();
  try {
    let repaired=false;
    const worker=createWatchdogWorker({
      logger:{info(){}},
      makeAdapters(records) {
        const adapters=createFixtureAdapters(records);
        if(repaired) adapters.set("fixture-structural",{
          id:"fixture-structural",
          async probe({source}) {
            return {reached:true,finalUrl:source.currentUrl,identityVerified:true,
              checks:{search:true,detail:true,episode:true,playback:true}};
          },
        });
        return adapters;
      },
    });
    const all=env(db);
    for(let tick=0;tick<8;tick++){
      const result=await worker.scheduled({scheduledTime:tick*QUARTER},all);
      assert.equal(result.status,"fixture_checked");
      assert.ok(result.checked<=8);
      assert.equal(result.totals.runner_error,0);
    }
    const atSeven=7*QUARTER;
    const snapshot=await buildUnpublishedSnapshot(db,atSeven);
    assert.equal(snapshot.sources.length,29);
    assert.equal(snapshot.sources.filter(s=>s.baseUrl==="https://moved.example.org").length,2);
    const held=await getSource(db,"fixture-structural");
    assert.equal(held.state.status,HEALTH.ADMIN_REQUIRED);
    assert.equal(held.state.currentUrl,"https://demo.example.org");
    const revisionBefore=snapshot.revision;
    const oldCheck=await worker.scheduled({scheduledTime:atSeven},all);
    assert.equal(oldCheck.checked,0); // duplicate Cron tick cannot double-apply checks
    assert.equal((await buildUnpublishedSnapshot(db,atSeven)).revision,revisionBefore);

    repaired=true; // simulates human-reviewed adapter code being fixed
    await retestAfterReview(db,"fixture-structural","admin:alice",2*HOUR);
    assert.equal((await buildUnpublishedSnapshot(db,2*HOUR)).sources.length,29);
    assert.equal((await worker.scheduled({scheduledTime:2*HOUR},all)).checked,1);
    assert.equal((await buildUnpublishedSnapshot(db,2*HOUR)).sources.length,29);
    assert.equal((await worker.scheduled({scheduledTime:3*HOUR},all)).checked,1);
    const recovered=await buildUnpublishedSnapshot(db,3*HOUR);
    assert.equal(recovered.sources.length,30);
    assert.equal((await getSource(db,"fixture-structural")).state.status,HEALTH.HEALTHY);

    const audit=db.sql.prepare("SELECT COUNT(*) n FROM source_audit").get().n;
    const sourceRuns=db.sql.prepare("SELECT COUNT(*) n FROM source_probe_runs").get().n;
    const leased=db.sql.prepare("SELECT COUNT(*) n FROM source_probe_leases").get().n;
    assert.equal(audit,recovered.revision);
    assert.equal(sourceRuns,61); // 27*2 + 2*2 + 1 initial structural + 2 after repair
    assert.equal(leased,0);
  }finally{db.close();}
});
