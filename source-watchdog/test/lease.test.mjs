import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import {fileURLToPath} from "node:url";
import {dirname,join} from "node:path";
import {claimProbeLease,stillOwnsProbeLease,releaseProbeLease} from "../src/lease.mjs";

let DatabaseSync;
try { ({DatabaseSync} = await import("node:sqlite")); } catch {}
const sql=readFileSync(join(dirname(fileURLToPath(import.meta.url)),
  "../migrations/0002_source_leases.sql"),"utf8");
function fixture() {
  const raw=new DatabaseSync(":memory:");
  raw.exec("PRAGMA foreign_keys=ON; CREATE TABLE source_registry(id TEXT PRIMARY KEY); " +
    "INSERT INTO source_registry VALUES('fixture-source');");
  raw.exec(sql);
  const db={prepare(query){return {bind(...params){return {
    async run(){const result=raw.prepare(query).run(...params);
      return {meta:{changes:Number(result.changes)}};},
    async first(){return raw.prepare(query).get(...params)??null;},
  };}};}};
  return {db,raw};
}
const sqliteTest=DatabaseSync?test:test.skip;

sqliteTest("only one runner owns a source; an expired lease can be claimed",async()=>{
  const {db,raw}=fixture();try{
    assert.equal(await claimProbeLease(db,"fixture-source","probe-token-one",1000,45000),true);
    assert.equal(await claimProbeLease(db,"fixture-source","probe-token-two",2000,45000),false);
    assert.equal(await stillOwnsProbeLease(db,"fixture-source","probe-token-one",2000),true);
    assert.equal(await claimProbeLease(db,"fixture-source","probe-token-two",46000,45000),true);
    assert.equal(await stillOwnsProbeLease(db,"fixture-source","probe-token-one",46000),false);
    assert.equal(await stillOwnsProbeLease(db,"fixture-source","probe-token-two",46000),true);
  }finally{raw.close();}
});
sqliteTest("a previous runner cannot release the new owner's lease",async()=>{
  const {db,raw}=fixture();try{
    await claimProbeLease(db,"fixture-source","probe-token-one",1000,45000);
    await claimProbeLease(db,"fixture-source","probe-token-two",46000,45000);
    assert.equal(await releaseProbeLease(db,"fixture-source","probe-token-one"),false);
    assert.equal(await stillOwnsProbeLease(db,"fixture-source","probe-token-two",47000),true);
    assert.equal(await releaseProbeLease(db,"fixture-source","probe-token-two"),true);
    assert.equal(await releaseProbeLease(db,"fixture-source","probe-token-two"),false);
  }finally{raw.close();}
});
sqliteTest("bad identities, excessive lease TTL and unknown sources fail closed",async()=>{
  const {db,raw}=fixture();try{
    await assert.rejects(claimProbeLease(db,"127.0.0.1","probe-token-one",1000),
      /invalid_probe_lease_identity/);
    await assert.rejects(claimProbeLease(db,"fixture-source","short",1000),
      /invalid_probe_lease_identity/);
    await assert.rejects(claimProbeLease(db,"fixture-source","probe-token-one",1000,121000),
      /invalid_probe_lease_duration/);
    await assert.rejects(claimProbeLease(db,"fixture-other","probe-token-one",1000),
      /FOREIGN KEY/);
  }finally{raw.close();}
});
sqliteTest("a second migration application leaves existing leases intact",async()=>{
  const {db,raw}=fixture();try{
    await claimProbeLease(db,"fixture-source","probe-token-one",1000,45000);
    raw.exec(sql);
    assert.equal(await stillOwnsProbeLease(db,"fixture-source","probe-token-one",2000),true);
  }finally{raw.close();}
});
