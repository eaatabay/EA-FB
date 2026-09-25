import test from "node:test";
import assert from "node:assert/strict";
import {summarizeSources,renderAdminDashboard} from "../src/admin-view.mjs";
const row=(id,status="healthy",failure=null)=>({
 id,config:{id,currentUrl:"https://demo.example.org/watch?v=<script>alert(1)</script>"},
 state:{id,status,lastCheckedAt:1800000000000,nextCheckAt:1800000900000,
   lastFailure:failure},
});
test("counts all statuses and sorts source rows without mutating originals",()=>{
 const records=[row("b","quarantined","unreachable"),row("a","healthy"),
   row("c","admin_required","structural_change")];
 const stats=summarizeSources(records,1800000000000);
 assert.equal(stats.counts.healthy,1);
 assert.equal(stats.counts.quarantined,1);
 assert.equal(stats.counts.admin_required,1);
 assert.deepEqual(stats.rows.map(x=>x.id),["a","b","c"]);
 assert.equal(records[0].id,"b");
});
test("escapes every D1-derived HTML value; no scripts or source-edit forms",()=>{
 const id="z<img src=x onerror=alert(1)>";
 const html=renderAdminDashboard(summarizeSources([row(id)],1800000000000));
 assert.ok(html.includes("&lt;img"));
 assert.ok(html.includes("&lt;script&gt;"));
 assert.ok(!html.includes("<script>"));
 assert.ok(!html.includes("<form"));
 assert.ok(!html.includes("onclick="));
 assert.ok(html.includes("EA-FB Kaynak Bekçisi"));
});
test("malformed records fail rather than silently displaying wrong identity",()=>{
 assert.throws(()=>summarizeSources([{id:"wrong",config:{id:"x"},state:{id:"x"}}],1800000000000),
   /corrupt_overview_record/);
 assert.throws(()=>summarizeSources([], -1),/invalid_overview/);
});
test("untrusted arbitrary failure text is not rendered",()=>{
 const html=renderAdminDashboard(summarizeSources([
  row("licensed-demo","healthy","SECRET_ERROR_<img>"),
 ],1800000000000));
 assert.ok(!html.includes("SECRET_ERROR"));
});
