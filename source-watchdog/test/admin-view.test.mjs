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


test("dashboard shows only sanctioned runner timeout/error codes",()=>{
 const stats=summarizeSources([
  row("timed-out","degraded","probe_timeout"),
  row("crashed","quarantined","adapter_error"),
  row("untrusted","degraded","SECRET_ADAPTER_EXCEPTION"),
 ],1800000000000);
 assert.equal(stats.rows.find(x=>x.id==="timed-out").error,"probe_timeout");
 assert.equal(stats.rows.find(x=>x.id==="crashed").error,"adapter_error");
 assert.equal(stats.rows.find(x=>x.id==="untrusted").error,"—");
 const html=renderAdminDashboard(stats);
 assert.ok(html.includes("probe_timeout") && html.includes("adapter_error"));
 assert.ok(!html.includes("SECRET_ADAPTER_EXCEPTION"));
});


test("future or corrupted health states are explicit anomalies, not degraded",()=>{
 const stats=summarizeSources([row("unknown","future_unreviewed_status"),
  row("ordinary","degraded")],1800000000000);
 assert.equal(stats.counts.invalid_state,1);
 assert.equal(stats.counts.degraded,1);
 assert.equal(stats.rows.find(x=>x.id==="unknown").status,"invalid_state");
 const html=renderAdminDashboard(stats);
 assert.ok(html.includes("invalid_state"));
 assert.ok(!html.includes("future_unreviewed_status"));
});

test("write-enabled panel exposes revision-bound actions without inline event handlers",()=>{
  const source=row("licensed-demo");
  source.revision=7;
  const html=renderAdminDashboard(summarizeSources([source],1800000000000),
    {editable:true,nonce:"nonce123"});
  assert.match(html,/data-action='disable'/);
  assert.match(html,/data-action='enable'/);
  assert.match(html,/data-action='retest'/);
  assert.match(html,/data-action='rollback'/);
  assert.match(html,/data-revision='7'/);
  assert.match(html,/script nonce='nonce123'/);
  assert.match(html,/expectedRevision:revision/);
  assert.match(html,/x-eafb-admin-action/);
  assert.doesNotMatch(html,/onclick=/);
  assert.doesNotMatch(html,/<form/);
});
test("read-only panel does not include active controls or JavaScript",()=>{
  const source=row("licensed-demo");
  source.revision=8;
  const html=renderAdminDashboard(summarizeSources([source],1800000000000));
  assert.doesNotMatch(html,/<script/);
  assert.doesNotMatch(html,/<button/);
  assert.match(html,/salt okunurdur/);
});
test("untrusted source IDs are escaped in editable controls",()=>{
  const source=row("x' onclick='alert(1)");
  source.revision=3;
  const html=renderAdminDashboard(summarizeSources([source],1800000000000),
    {editable:true,nonce:"safe"});
  assert.doesNotMatch(html,/\sonclick\s*=/);
  assert.match(html,/&#39;/);
});
