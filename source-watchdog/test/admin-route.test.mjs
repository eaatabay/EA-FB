import test from "node:test";
import assert from "node:assert/strict";
import {createWatchdogWorker} from "../src/worker.mjs";
const env={
 WATCHDOG_MODE:"production",WATCHDOG_ADMIN_ENABLED:"true",
 WATCHDOG_FIXTURE_ENABLED:"false",
 WATCHDOG_ACCESS_TEAM_DOMAIN:"https://test.cloudflareaccess.com",
 WATCHDOG_ACCESS_AUD:"test-audience-0123456789012345",
 WATCHDOG_ADMIN_EMAILS:"admin@example.com",
 SOURCES_DB:{prepare(){}},
};
const request=(path="/admin",method="GET")=>
 new Request("https://watchdog.example.org"+path,{method});
const row={id:"licensed-demo",config:{id:"licensed-demo",
 currentUrl:"https://licensed.example.org"},state:{id:"licensed-demo",
 status:"healthy",lastCheckedAt:1800000000000,nextCheckAt:1800000900000,
 lastFailure:null}};
test("admin endpoint is absent in tracked-off and fixture modes",async()=>{
 let verified=0,dbRead=0;
 const worker=createWatchdogWorker({verifyAdmin:async()=>{verified++;return "admin@example.com";},
   readRegistry:async()=>{dbRead++;return [row];}});
 for(const e of [
  {...env,WATCHDOG_ADMIN_ENABLED:"false"},
  {...env,WATCHDOG_MODE:"disabled"},
  {...env,WATCHDOG_FIXTURE_ENABLED:"true"},
    {...env,WATCHDOG_FIXTURE_ENABLED:"TRUE"},
    {...env,WATCHDOG_FIXTURE_ENABLED:undefined},
  {...env,WATCHDOG_ACCESS_TEAM_DOMAIN:"http://127.0.0.1"},
 ]) {
   const res=await worker.fetch(request(),e);
   assert.equal(res.status,404);
 }
 assert.equal(verified,0);
 assert.equal(dbRead,0);
});
test("invalid Access identity cannot read source registry",async()=>{
 let dbRead=0;
 const worker=createWatchdogWorker({verifyAdmin:async()=>null,
   readRegistry:async()=>{dbRead++;return [row];}});
 const res=await worker.fetch(request(),env);
 assert.equal(res.status,403);
 assert.equal(dbRead,0);
});
test("verified Access admin sees no-store CSP-protected read-only navy/yellow overview",async()=>{
 const worker=createWatchdogWorker({verifyAdmin:async()=> "admin@example.com",
   readRegistry:async()=>[row],nowMillis:()=>1800000000000});
 const res=await worker.fetch(request(),env);
 assert.equal(res.status,200);
 assert.match(res.headers.get("content-type"),/text\/html/);
 assert.equal(res.headers.get("cache-control"),"no-store");
 assert.match(res.headers.get("content-security-policy"),/default-src 'none'/);
 assert.equal(res.headers.get("x-frame-options"),"DENY");
 const html=await res.text();
 assert.ok(html.includes("#f4cb36"));
 assert.ok(html.includes("licensed-demo"));
 assert.ok(!html.includes("<form"));
});
test("no database returns 503 and no admin writes exist",async()=>{
 const worker=createWatchdogWorker({verifyAdmin:async()=>"admin@example.com"});
 const missing=await worker.fetch(request(),{...env,SOURCES_DB:null});
 assert.equal(missing.status,503);
 const post=await worker.fetch(request("/admin","POST"),env);
 assert.equal(post.status,405);
 const edit=await worker.fetch(request("/admin/update"),env);
 assert.equal(edit.status,404);
});
