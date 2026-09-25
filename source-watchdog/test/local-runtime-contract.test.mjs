import test from "node:test";
import assert from "node:assert/strict";
import {resolve} from "node:path";
import {
  validateLocalWranglerArgs,parseLocalD1Rows,validateFixtureCounters,
} from "../dev/local-runtime-contract.mjs";

const db="ea-fb-watchdog-fixture-local";
const root="/tmp/eafb-isolated-runtime-0001";
const opts=["--local","--config","wrangler.local.jsonc","--persist-to",root];
const migration=["d1","migrations","apply",db,...opts];
const select=["d1","execute",db,...opts,"--command",
  "SELECT COUNT(*) AS n FROM source_registry","--json"];
const seed=["d1","execute",db,...opts,"--file",
  resolve(root,"seed","fixtures.sql")];
const initial={total:30,healthy:0,admin_hold:0,moves:0,runs:0,
  audits:30,revision:30,leases:0};
const final={...initial,healthy:29,admin_hold:1,moves:2,
  runs:59,audits:89,revision:89};

test("ONLY exact scoped migration, read-only queries and private fixture SQL are permitted",()=>{
  assert.equal(validateLocalWranglerArgs(migration,root),true);
  assert.equal(validateLocalWranglerArgs(select,root),true);
  assert.equal(validateLocalWranglerArgs(seed,root),true);
});

test("rejects remote, credentials, unrecognized commands and ambiguous options",()=>{
  const bad=[
    ["d1","create",db,...opts],
    ["deploy",...opts],
    [...migration,"--remote"],
    [...migration,"--env","production"],
    [...migration,"--profile","work"],
    [...migration,"--experimental-provision"],
    [...migration,"--config","wrangler.jsonc"],
    migration.filter(x=>x!=="--local"),
    migration.map(x=>x===db?"ea-fb-catalog":x),
    [...migration,"--json"],
    [...select,"--command","DELETE FROM source_registry"],
    [...select.slice(0,-2),"--command","UPDATE registry_meta SET revision=0"],
    [...select.slice(0,-2),"--command","SELECT 1;DELETE FROM source_registry"],
    [...seed,"--file",resolve(root,"seed","fixtures.sql")],
    seed.map(x=>x===resolve(root,"seed","fixtures.sql")?"/tmp/production.sql":x),
    seed.map(x=>x===root?"/tmp/unreviewed-db":x),
    [...migration,"--config"],
  ];
  bad.forEach((args,index)=>assert.throws(
    ()=>validateLocalWranglerArgs(args,root),Error,
    "dangerous Wrangler invocation #"+index,
  ));
});

test("Wrangler success must be structured and contain exactly one real stats row",()=>{
  const result=parseLocalD1Rows(JSON.stringify([{
    success:true,results:[{n:30}],meta:{duration:1},
  }]));
  assert.deepEqual(result,[{n:30}]);
  assert.deepEqual(parseLocalD1Rows(JSON.stringify({
    success:true,results:[{n:89}],
  })),[{n:89}]);
  for(const output of ["ok","", "false", "null",
    JSON.stringify([{success:false,results:[{n:30}]}]),
    JSON.stringify([{success:true,results:[]}]),
    JSON.stringify([{success:true,results:[{n:30},{n:31}]}]),
  ])assert.throws(()=>parseLocalD1Rows(output),Error);
});

test("exact fixture counts are checked at seed, completion and Cron replay",()=>{
  assert.equal(validateFixtureCounters("initial",initial),true);
  assert.equal(validateFixtureCounters("final",final),true);
  assert.equal(validateFixtureCounters("replay",structuredClone(final),final),true);
  const replay={...final,runs:final.runs+1};
  assert.throws(()=>validateFixtureCounters("replay",replay,final),
    /cron_replay_modified_state/);
  assert.throws(()=>validateFixtureCounters("replay",final),
    /missing_replay_baseline/);
  assert.throws(()=>validateFixtureCounters("initial",{...initial,audits:29}),
    /fixture_counters_disagree/);
  assert.throws(()=>validateFixtureCounters("final",{...final,leases:1}),
    /fixture_counters_disagree/);
  assert.throws(()=>validateFixtureCounters("final",{...final,runs:"59"}),
    /fixture_counters_disagree/);
});
