import test from "node:test";
import assert from "node:assert/strict";
import { createWatchdogWorker } from "../src/worker.mjs";
import { verifySnapshot } from "../src/snapshot-crypto.mjs";

const now = 1_800_000_000_000;
const request = () => new Request("https://watchdog.example.org/v1/sources");
const db = {prepare() { return null; }};
const production = {
  WATCHDOG_MODE: "production",
  WATCHDOG_FIXTURE_ENABLED: "false",
  WATCHDOG_SNAPSHOT_ENABLED: "true",
  SOURCES_DB: db,
};
const realRecords = [{id:"licensed-demo",config:{currentUrl:"https://licensed.example.com"}}];
const snapshot = () => ({
  schemaVersion:1,revision:42,generatedAt:now,expiresAt:now+900000,
  sources:[{id:"licensed-demo",mediaKind:"movie",baseUrl:"https://licensed.example.com",adapterVersion:3}],
});
const logger = {info(){},warn(){}};

test("signed endpoint is dark by default; never touches D1 or signing secret",async()=>{
  let read=0;
  const worker=createWatchdogWorker({
    readRegistry:async()=>{read++;return realRecords;},nowMillis:()=>now,logger,
  });
  for(const env of [
    production && {...production,WATCHDOG_SNAPSHOT_ENABLED:"false"},
    {...production,WATCHDOG_MODE:"disabled"},
    {...production,WATCHDOG_FIXTURE_ENABLED:"true"},
  ]) {
    const response=await worker.fetch(request(),env);
    assert.equal(response.status,404);
    assert.equal((await response.json()).error,"not_found");
  }
  assert.equal(read,0);
});

test("missing D1 binding or signing key returns sanitized 503",async()=>{
  const worker=createWatchdogWorker({nowMillis:()=>now,logger});
  const noDb=await worker.fetch(request(),{...production,SOURCES_DB:null});
  assert.equal(noDb.status,503);
  assert.equal((await noDb.json()).error,"source_snapshot_unavailable");
  const noKey=await worker.fetch(request(),production);
  assert.equal(noKey.status,503);
});

test("any test/fixture record blocks production signing BEFORE reading snapshot",async()=>{
  let signed=0,read=0;
  const worker=createWatchdogWorker({
    readRegistry:async()=>[{id:"fixture-healthy-01",
      config:{currentUrl:"https://demo.example.org"}}],
    readSnapshot:async()=>{read++;return snapshot();},
    signSnapshot:async()=>{signed++;return {};},
    nowMillis:()=>now,logger,
  });
  const response=await worker.fetch(request(),{
    ...production,SNAPSHOT_SIGNING_KEY_ID:"eafb-2026",
    SNAPSHOT_SIGNING_PKCS8_B64:"unused-test-value",
  });
  assert.equal(response.status,503);
  assert.equal((await response.json()).error,"source_snapshot_unavailable");
  assert.equal(read,0);
  assert.equal(signed,0);
});

test("unexpected fixture ID inside snapshot is blocked even after registry read",async()=>{
  const worker=createWatchdogWorker({
    readRegistry:async()=>realRecords,
    readSnapshot:async()=>({...snapshot(),sources:[
      {...snapshot().sources[0],id:"fixture-changed"},
    ]}),
    signSnapshot:async(db,env,at,build)=>{await build(db,at);return {signed:true};},
    nowMillis:()=>now,logger,
  });
  const response=await worker.fetch(request(),{
    ...production,SNAPSHOT_SIGNING_KEY_ID:"eafb-2026",
    SNAPSHOT_SIGNING_PKCS8_B64:"unused-test-value",
  });
  assert.equal(response.status,503);
});

test("real Ed25519 private Worker secret signs and verifies public-only snapshot",async()=>{
  const pair=await crypto.subtle.generateKey("Ed25519",true,["sign","verify"]);
  const pkcs8=Buffer.from(await crypto.subtle.exportKey("pkcs8",pair.privateKey)).toString("base64");
  const worker=createWatchdogWorker({
    readRegistry:async()=>realRecords,
    readSnapshot:async()=>snapshot(),
    nowMillis:()=>now,logger,
  });
  const response=await worker.fetch(request(),{
    ...production,SNAPSHOT_SIGNING_KEY_ID:"eafb-2026",
    SNAPSHOT_SIGNING_PKCS8_B64:pkcs8,
  });
  assert.equal(response.status,200);
  assert.equal(response.headers.get("cache-control"),"no-store");
  const signed=await response.json();
  assert.equal(JSON.stringify(signed).includes(pkcs8),false);
  const verified=await verifySnapshot(signed,
    new Map([["eafb-2026",pair.publicKey]]),
    {now,lastRevision:41,installedAdapters:new Map([["licensed-demo",3]])});
  assert.equal(verified.ok,true);
  assert.deepEqual(verified.usableSources.map(x=>x.id),["licensed-demo"]);
});

test("wrong HTTP methods and unrelated routes expose no registry or admin actions",async()=>{
  const worker=createWatchdogWorker({nowMillis:()=>now,logger});
  const post=await worker.fetch(new Request("https://watchdog.example.org/v1/sources",{method:"POST"}),production);
  assert.equal(post.status,405);
  const secret=await worker.fetch(new Request("https://watchdog.example.org/admin/sources"),production);
  assert.equal(secret.status,404);
});
