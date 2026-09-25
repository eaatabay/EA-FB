import test from "node:test";
import assert from "node:assert/strict";
import { createWatchdogWorker } from "../src/worker.mjs";
import { assertIsolatedFixtureRegistry, createFixtureAdapters } from "../src/fixtures.mjs";

const source = (id, host = "demo.example.org") => ({
  id,
  config: {
    id,
    enabled: true,
    integrationApproved: true,
    mediaKind: "both",
    currentUrl: "https://" + host,
    lastKnownGoodUrl: "https://" + host,
    verifiedDomains: [host, "moved.example.org"],
    requiredChecks: ["reachability", "search", "detail", "episode", "playback"],
    adapterVersion: 1,
  },
  state: { status: "degraded", nextCheckAt: 0 },
});
const env = { WATCHDOG_MODE: "fixture", WATCHDOG_CRON_ENABLED: "true",
  WATCHDOG_FIXTURE_ENABLED: "true", SOURCES_DB: { prepare() {} } };
const event = { scheduledTime: 12345678, cron: "*/15 * * * *" };
const logger = { info() {} };

test("safe defaults: scheduled handler never reads D1 or runs a probe", async () => {
  let called = false;
  const worker = createWatchdogWorker({
    readRegistry: async () => { called = true; return []; }, logger,
  });
  assert.deepEqual(await worker.scheduled(event, {...env, WATCHDOG_CRON_ENABLED:"false"}),
    {status:"disabled",checked:0});
  assert.deepEqual(await worker.scheduled(event, {...env, WATCHDOG_MODE:"production"}),
    {status:"disabled",checked:0});
  assert.deepEqual(await worker.scheduled(event, {...env, WATCHDOG_FIXTURE_ENABLED:"false"}),
    {status:"disabled",checked:0});
  assert.equal(called, false);
});

test("no D1 binding means no fixture writes", async () => {
  const worker = createWatchdogWorker({logger});
  await assert.rejects(worker.scheduled(event, {...env,SOURCES_DB:null}),
    /fixture_d1_not_configured/);
});

test("mixed or non-fixture registry fails closed BEFORE the runner", async () => {
  let runs = 0;
  const worker = createWatchdogWorker({
    readRegistry:async()=>[source("fixture-good"),source("real-source","real.example.com")],
    runChecks:async()=>{ runs++; return []; },logger,
  });
  await assert.rejects(worker.scheduled(event,env),/unsafe_fixture_registry/);
  assert.equal(runs,0);
});

test("empty registry remains harmless", async () => {
  const worker = createWatchdogWorker({readRegistry:async()=>[],logger});
  assert.deepEqual(await worker.scheduled(event,env),
    {status:"empty_fixture_registry",checked:0});
});

test("cron fixture mode processes at most 8 due sources with max concurrency 4", async () => {
  const records = Array.from({length:30}, (_,i) => source(
    i<27 ? "fixture-healthy-" + String(i+1).padStart(2,"0") :
    ["fixture-move-a","fixture-move-b","fixture-structural"][i-27],
  ));
  let options;
  const worker = createWatchdogWorker({
    readRegistry:async()=>records,
    runChecks:async input => { options=input; return [
      {status:"committed"},{status:"committed"},{status:"runner_error"},
    ]; }, logger,
  });
  const result = await worker.scheduled(event,env);
  assert.equal(result.status,"fixture_checked");
  assert.equal(result.checked,3);
  assert.equal(result.totals.committed,2);
  assert.equal(result.totals.runner_error,1);
  assert.equal(options.now,event.scheduledTime);
  assert.equal(options.adapters.size,30);
  assert.equal(options.limit,8);
  assert.equal(options.concurrency,4);
});

test("fixture adapters are deterministic, offline, and model two moves/one review", async () => {
  const rows=[source("fixture-healthy-01"), source("fixture-move-a"),
    source("fixture-move-b"),source("fixture-structural"),source("fixture-down")];
  assert.equal(assertIsolatedFixtureRegistry(rows),true);
  const adapters=createFixtureAdapters(rows);
  const healthy=await adapters.get("fixture-healthy-01").probe({source:rows[0].config});
  assert.equal(healthy.reached,true);
  assert.equal(healthy.finalUrl,rows[0].config.currentUrl);
  for (const index of [1,2]) {
    const value=await adapters.get(rows[index].id).probe({source:rows[index].config});
    assert.equal(value.finalUrl,"https://moved.example.org");
    assert.equal(value.checks.search,true);
  }
  assert.equal((await adapters.get("fixture-structural").probe({source:rows[3].config})).structuralChange,true);
  assert.equal((await adapters.get("fixture-down").probe({source:rows[4].config})).reached,false);
  await assert.rejects(adapters.get("fixture-healthy-01").probe({source:rows[1].config}),
    /fixture_identity_mismatch/);
});

test("public interface exposes only minimal health, never source data or admin operations",async()=>{
  const worker=createWatchdogWorker({logger});
  const health=await worker.fetch(new Request("https://watchdog.example.org/health"));
  assert.equal(health.status,200);
  assert.equal(health.headers.get("cache-control"),"no-store");
  const body=await health.json();
  assert.equal(body.service,"EA-FB Source Watchdog");
  const registry=await worker.fetch(new Request("https://watchdog.example.org/registry"));
  assert.equal(registry.status,404);
  const admin=await worker.fetch(new Request("https://watchdog.example.org/admin",{method:"POST"}));
  assert.equal(admin.status,405);
});

test("committed Wrangler config cannot accidentally start fixture jobs",async()=>{
  const { readFileSync }=await import("node:fs");
  const { fileURLToPath }=await import("node:url");
  const { dirname,join }=await import("node:path");
  const p=join(dirname(fileURLToPath(import.meta.url)),"../wrangler.jsonc");
  const config=JSON.parse(readFileSync(p,"utf8"));
  assert.equal(config.name,"ea-fb-source-watchdog-dev");
  assert.equal(config.workers_dev,false);
  assert.equal(config.vars.WATCHDOG_MODE,"disabled");
  assert.equal(config.vars.WATCHDOG_CRON_ENABLED,"false");
  assert.equal(config.vars.WATCHDOG_FIXTURE_ENABLED,"false");
  assert.equal(config.vars.WATCHDOG_SNAPSHOT_ENABLED,"false");
  assert.equal(config.vars.WATCHDOG_ADMIN_ENABLED,"false");
  assert.deepEqual(config.triggers.crons,["*/15 * * * *"]);
  assert.equal(config.d1_databases,undefined); // deliberate deployment blocker
});
