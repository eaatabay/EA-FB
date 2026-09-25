import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import {fileURLToPath} from "node:url";
import {dirname,resolve} from "node:path";
import {makeLocalConfig,verifyLocalConfig} from "../dev/make-local-config.mjs";

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
test("legacy local command uses ephemeral release gate, never an old persisted DB",()=>{
  const wrapper=readFileSync(resolve(root,"dev/run-local-fixtures.sh"),"utf8");
  assert.match(wrapper,/bash dev\/run-release-validation\.sh/);
  assert.doesNotMatch(wrapper,/wrangler\s+d1\s+execute|generated-fixtures\.sql/);
  const release=readFileSync(resolve(root,"dev/run-release-validation.sh"),"utf8");
  assert.match(release,/node dev\/verify-local-wrangler\.mjs/);
  const smoke=readFileSync(resolve(root,"dev/verify-local-wrangler.mjs"),"utf8");
  assert.match(smoke,/"--local"/);
  assert.match(smoke,/"--persist-to"/);
  assert.doesNotMatch(smoke,/wrangler\(\s*\[\s*"d1"\s*,\s*"create"/);
});

test("exact local config refuses injected route, real D1, changed worker and credentials",()=>{
  const valid=makeLocalConfig(tracked);
  assert.equal(verifyLocalConfig(tracked,valid),true);
  const modifications=[
    local=>{local.main="./src/unreviewed.mjs";},
    local=>{local.routes=[{pattern:"watchdog.example.org/*"}];},
    local=>{local.d1_databases[0].database_id="real-production-uuid";},
    local=>{local.d1_databases[0].preview_database_id="real-db";},
    local=>{local.vars.WATCHDOG_ADMIN_WRITES_ENABLED="true";},
    local=>{local.vars.SECRET_TOKEN="leaked";},
    local=>{local.triggers.crons=["* * * * *"];},
  ];
  for(const change of modifications){
    const local=makeLocalConfig(tracked); change(local);
    assert.throws(()=>verifyLocalConfig(tracked,local),/unsafe_local_configuration/);
  }
  assert.throws(()=>makeLocalConfig({...tracked,routes:[{}]}),/not_inert/);
  assert.throws(()=>makeLocalConfig({...tracked,vars:{...tracked.vars,SECRET_TOKEN:"disabled"}}),/not_inert/);
  assert.throws(()=>makeLocalConfig({...tracked,main:"./src/unsafe.mjs"}),/not_inert/);
});
