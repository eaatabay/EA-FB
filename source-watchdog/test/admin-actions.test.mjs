import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import {fileURLToPath} from "node:url";
import {dirname,join} from "node:path";
import {registerSource,getSource,RegistryConflict} from "../src/registry.mjs";
import {
  AdminMutationError,adminWritesConfigured,validateAdminMutation,
  parseAdminMutationRequest,executeAdminMutation,actorForVerifiedEmail,
} from "../src/admin-actions.mjs";
import {createWatchdogWorker} from "../src/worker.mjs";

const root=join(dirname(fileURLToPath(import.meta.url)),"..");
let DatabaseSync;
try{({DatabaseSync}=await import("node:sqlite"));}catch{}
const sqliteTest=DatabaseSync?test:test.skip;
const now=1_800_000_000_000;
const origin="https://watchdog.example.org";
const env={
  WATCHDOG_MODE:"production",WATCHDOG_FIXTURE_ENABLED:"false",
  WATCHDOG_ADMIN_ENABLED:"true",WATCHDOG_ADMIN_WRITES_ENABLED:"true",
  WATCHDOG_ADMIN_ORIGIN:origin,
  WATCHDOG_ACCESS_TEAM_DOMAIN:"https://test.cloudflareaccess.com",
  WATCHDOG_ACCESS_AUD:"test-audience-0123456789012345",
  WATCHDOG_ADMIN_EMAILS:"admin@example.com",
};
function request(sourceId="licensed-demo",action="disable",revision=0,options={}){
  const url=origin+"/admin/api/sources/"+sourceId;
  const body=JSON.stringify({action,expectedRevision:revision});
  return new Request(url,{
    method:"POST",
    headers:{
      origin,"content-type":"application/json",
      "x-eafb-admin-action":"confirmed",
      ...options.headers,
    },
    body:options.body ?? body,
  });
}
function d1(){
  const raw=new DatabaseSync(":memory:");
  raw.exec(readFileSync(join(root,"migrations/0001_registry.sql"),"utf8"));
  raw.exec(readFileSync(join(root,"migrations/0002_source_leases.sql"),"utf8"));
  return {
    raw,
    prepare(sql) {
      return {bind(...values){
        return {
          first:async()=>raw.prepare(sql).get(...values)??null,
          all:async()=>({results:raw.prepare(sql).all(...values)}),
          run:async()=>{
            const result=raw.prepare(sql).run(...values);
            return {meta:{changes:Number(result.changes)}};
          },
        };
      }};
    },
    close(){raw.close();},
  };
}
const source={
  id:"licensed-demo",enabled:true,integrationApproved:true,adapterVersion:1,
  mediaKind:"movie",currentUrl:"https://licensed.example.com",
  lastKnownGoodUrl:"https://licensed.example.com",
  verifiedDomains:["licensed.example.com"],
  requiredChecks:["reachability","search","detail"],
};
test("writes gate is independent of read-only admin access",()=>{
  assert.equal(adminWritesConfigured(env),true);
  for(const change of [
    {WATCHDOG_ADMIN_WRITES_ENABLED:"false"},
    {WATCHDOG_ADMIN_ENABLED:"false"},
    {WATCHDOG_MODE:"fixture"},
    {WATCHDOG_FIXTURE_ENABLED:"true"},
    {WATCHDOG_FIXTURE_ENABLED:"TRUE"},
    {WATCHDOG_FIXTURE_ENABLED:undefined},
    {WATCHDOG_ADMIN_ORIGIN:"https://localhost"},
    {WATCHDOG_ADMIN_ORIGIN:"http://watchdog.example.org"},
    {WATCHDOG_ADMIN_ORIGIN:"https://-watchdog.example.org"},
    {WATCHDOG_ADMIN_ORIGIN:"https://watchdog-.example.org"},
    {WATCHDOG_ADMIN_ORIGIN:"https://"+"a".repeat(64)+".example.org"},
  ]) assert.equal(adminWritesConfigured({...env,...change}),false);
});
test("strict mutation schema refuses permission grants, unknown keys and stale formats",()=>{
  assert.deepEqual(validateAdminMutation("licensed-demo",{action:"disable",expectedRevision:0}),{
    sourceId:"licensed-demo",action:"disable",expectedRevision:0,
  });
  for(const value of [
    {action:"approve",expectedRevision:0},
    {action:"delete",expectedRevision:0},
    {action:"disable",expectedRevision:-1},
    {action:"disable",expectedRevision:"0"},
    {action:"disable",expectedRevision:0,currentUrl:"https://evil.invalid"},
  ])assert.throws(()=>validateAdminMutation("licensed-demo",value),AdminMutationError);
});
test("origin, custom header, JSON type and actual streaming body size are enforced",async()=>{
  assert.deepEqual(await parseAdminMutationRequest(request(),env),{
    sourceId:"licensed-demo",action:"disable",expectedRevision:0,
  });
  for(const x of [
    {origin:"https://evil.example.com"},
    {"x-eafb-admin-action":"no"},
    {"content-type":"text/plain"},
    {"sec-fetch-site":"cross-site"},
  ])await assert.rejects(parseAdminMutationRequest(request(
    "licensed-demo","disable",0,{headers:x}),env),AdminMutationError);
  const bigBody=JSON.stringify({action:"disable",expectedRevision:0,padding:"x".repeat(1100)});
  await assert.rejects(parseAdminMutationRequest(request(
    "licensed-demo","disable",0,{body:bigBody}),env),
    e=>e instanceof AdminMutationError&&e.status===413);
  await assert.rejects(parseAdminMutationRequest(request(
    "licensed-demo","disable",0,{body:"not json"}),env),AdminMutationError);
});
test("noncanonical admin request URL aliases fail before parsing body",async()=>{
  const suffixes=["?","#","?x=1","#fragment","%2F","/./",
    "%2elicensed-demo"];
  const body=JSON.stringify({action:"disable",expectedRevision:0});
  for(const suffix of suffixes){
    const req=new Request(origin+"/admin/api/sources/licensed-demo"+suffix,{
      method:"POST",headers:{origin,"content-type":"application/json",
        "x-eafb-admin-action":"confirmed"},body,
    });
    await assert.rejects(parseAdminMutationRequest(req,env),
      e=>e instanceof AdminMutationError&&[403,404].includes(e.status),
      suffix);
  }
  // Request() normalizes literal /../ before the handler sees it; test
  // raw parser rejection separately rather than expecting impossible behavior.
  const dotSegment={url:origin+
    "/admin/api/sources/licensed-demo/../licensed-demo",
    method:"POST",headers:new Headers({origin,"content-type":"application/json",
      "x-eafb-admin-action":"confirmed"})};
  await assert.rejects(parseAdminMutationRequest(dotSegment,env),
    e=>e instanceof AdminMutationError&&e.status===403);
  // Request() itself refuses credentialed URLs; a direct parser boundary
  // test uses a request-like object to ensure the check is independent.
  const credential={url:
    "https://user:pass@watchdog.example.org/admin/api/sources/licensed-demo",
    method:"POST",headers:new Headers({origin,"content-type":"application/json",
      "x-eafb-admin-action":"confirmed"})};
  await assert.rejects(parseAdminMutationRequest(credential,env),
    e=>e instanceof AdminMutationError&&e.status===403);
});

test("admin body limit remains 413 when a malicious stream cancel throws",async()=>{
  const stream=new ReadableStream({
    start(controller){controller.enqueue(new Uint8Array(1100));},
    cancel(){throw new Error("UNTRUSTED_CANCEL_ERROR");},
  });
  const req=new Request(origin+"/admin/api/sources/licensed-demo",{
    method:"POST",duplex:"half",body:stream,
    headers:{origin,"content-type":"application/json",
      "x-eafb-admin-action":"confirmed"},
  });
  await assert.rejects(parseAdminMutationRequest(req,env),
    e=>e instanceof AdminMutationError&&e.status===413);
});

test("never-settling cancel cannot hang an oversized admin POST",async()=>{
  let cancelled=false;
  const stream=new ReadableStream({
    start(controller){controller.enqueue(new Uint8Array(1100));},
    cancel(){cancelled=true;return new Promise(()=>{});},
  });
  const req=new Request(origin+"/admin/api/sources/licensed-demo",{
    method:"POST",duplex:"half",body:stream,
    headers:{origin,"content-type":"application/json",
      "x-eafb-admin-action":"confirmed"},
  });
  const result=await Promise.race([
    parseAdminMutationRequest(req,env).then(()=>"unexpected_success",
      e=>e instanceof AdminMutationError ? e.status : "wrong_error"),
    new Promise(resolve=>setTimeout(()=>resolve("hung_cancel"),150)),
  ]);
  assert.equal(result,413);
  assert.equal(cancelled,true);
});

sqliteTest("verified actor, disable, stale replay and enable use D1 CAS + audit",async()=>{
  const db=d1();try{
    await registerSource(db,source,"admin:fixture",0);
    const parsed=await parseAdminMutationRequest(request(),env);
    const result=await executeAdminMutation(db,parsed,"admin@example.com",now);
    assert.equal(result.state,"disabled");
    assert.equal(result.revision,1);
    await assert.rejects(executeAdminMutation(db,parsed,"admin@example.com",now+1),
      RegistryConflict);
    const reopened=await executeAdminMutation(db,{
      sourceId:"licensed-demo",action:"enable",expectedRevision:1,
    },"admin@example.com",now+2);
    assert.equal(reopened.state,"degraded");
    assert.equal(reopened.revision,2);
    assert.equal((await getSource(db,"licensed-demo")).config.currentUrl,
      "https://licensed.example.com");
    const audit=db.raw.prepare("SELECT changed_by,reason FROM source_audit ORDER BY event_id DESC LIMIT 1").get();
    assert.match(audit.changed_by,/^admin:[a-f0-9]{32}$/);
    assert.equal(audit.reason,"admin:enabled");
    assert.equal(db.raw.prepare("SELECT revision FROM registry_meta WHERE singleton=1").get().revision,3);
    assert.equal(await actorForVerifiedEmail("admin@example.com"),
      await actorForVerifiedEmail("ADMIN@example.com"));
  }finally{db.close();}
});
test("Worker writes remain 404 even if read-only admin is enabled",async()=>{
  const worker=createWatchdogWorker({verifyAdmin:async()=>"admin@example.com"});
  const res=await worker.fetch(request(),{...env,WATCHDOG_ADMIN_WRITES_ENABLED:"false"});
  assert.equal(res.status,404);
});
test("Worker write route checks Access identity first and returns sanitized conflicts",async()=>{
  const missing=createWatchdogWorker({verifyAdmin:async()=>null});
  const denied=await missing.fetch(request(),{...env,SOURCES_DB:{prepare(){}}});
  assert.equal(denied.status,403);
  let observed=null;
  const worker=createWatchdogWorker({
    verifyAdmin:async()=>"admin@example.com",
    performAdminMutation:async (db,parsed,email)=>{
      observed={parsed,email};return {sourceId:parsed.sourceId,revision:1,state:"disabled"};
    },
    nowMillis:()=>now,
  });
  const database={prepare(){}};
  const yes=await worker.fetch(request(),{...env,SOURCES_DB:database});
  assert.equal(yes.status,200);
  assert.equal(yes.headers.get("cache-control"),"no-store");
  assert.deepEqual(observed.parsed,{sourceId:"licensed-demo",action:"disable",expectedRevision:0});
  assert.equal(observed.email,"admin@example.com");
  const stale=createWatchdogWorker({
    verifyAdmin:async()=>"admin@example.com",
    performAdminMutation:async()=>{throw new RegistryConflict();},
  });
  const conflict=await stale.fetch(request(),{...env,SOURCES_DB:database});
  assert.equal(conflict.status,409);
  assert.equal((await conflict.json()).error,"revision_conflict");
  const notFound=await worker.fetch(new Request(origin+"/admin/api/sources/unknown",{
    method:"GET",
  }),{...env,SOURCES_DB:database});
  assert.equal(notFound.status,405);
});


test("valid admin POST with a stalled body is bounded to four seconds",async()=>{
 let cancelled=false;
 const stream=new ReadableStream({
  start(c){c.enqueue(new TextEncoder().encode("{"));},
  cancel(){cancelled=true;return new Promise(()=>{});},
 });
 const req=new Request(origin+"/admin/api/sources/licensed-demo",{
  method:"POST",duplex:"half",body:stream,
  headers:{origin,"content-type":"application/json",
   "x-eafb-admin-action":"confirmed"},
 });
 const started=Date.now();
 await assert.rejects(parseAdminMutationRequest(req,env),
  e=>e instanceof AdminMutationError && e.status===408 &&
   e.message==="admin_body_timeout");
 assert.equal(cancelled,true);
 assert.ok(Date.now()-started<6500);
});
