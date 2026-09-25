import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import {fileURLToPath} from "node:url";
import {dirname,resolve} from "node:path";
import {makeLocalConfig} from "../dev/make-local-config.mjs";

const root=resolve(dirname(fileURLToPath(import.meta.url)),"..");
const tracked=JSON.parse(readFileSync(resolve(root,"wrangler.jsonc"),"utf8"));
test("local config is fixture-only with fake local D1 and never enables snapshot HTTP",()=>{
  const local=makeLocalConfig(tracked);
  assert.equal(local.name,"ea-fb-source-watchdog-local-fixtures");
  assert.equal(local.d1_databases[0].binding,"SOURCES_DB");
  assert.equal(local.d1_databases[0].database_id,"00000000-0000-0000-0000-000000000000");
  assert.equal(local.d1_databases[0].preview_database_id,"ea-fb-watchdog-fixture-local");
  assert.equal(local.vars.WATCHDOG_MODE,"fixture");
  assert.equal(local.vars.WATCHDOG_CRON_ENABLED,"true");
  assert.equal(local.vars.WATCHDOG_SNAPSHOT_ENABLED,"false");
  assert.equal(local.vars.WATCHDOG_ADMIN_ENABLED,"false");
  assert.equal(local.vars.WATCHDOG_ADMIN_WRITES_ENABLED,"false");
  assert.equal(local.workers_dev,false);
  assert.equal(tracked.vars.WATCHDOG_MODE,"disabled"); // no tracked mutation
});
test("config generator refuses tracked config with real D1 or active production",()=>{
  assert.throws(()=>makeLocalConfig({...tracked,d1_databases:[{}]}),/not_inert/);
  assert.throws(()=>makeLocalConfig({...tracked,vars:{...tracked.vars,WATCHDOG_MODE:"production"}}),/not_inert/);
});
test("offline runner contains no remote D1 provisioning or deployment",()=>{
  const script=readFileSync(resolve(root,"dev/run-local-fixtures.sh"),"utf8");
  const executable=script.split("\n").filter(s=>!s.trimStart().startsWith("#")).join("\n");
  assert.ok(executable.includes("--local --config wrangler.local.jsonc"));
  assert.ok(!/wrangler\s+d1\s+create|--remote\b|wrangler\s+deploy/.test(executable));
});
