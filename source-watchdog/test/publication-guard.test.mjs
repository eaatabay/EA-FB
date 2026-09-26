import test from "node:test";
import assert from "node:assert/strict";
import {validApprovalReference,assertReviewedPublication,APPROVED_RIGHTS_REFS,APPROVED_SOURCE_GRANTS} from "../src/publication-guard.mjs";
const approved=["rights/2026/licensed-demo.md"];
const now=1_800_000_000_000;
const grant=()=>({id:"licensed-demo",evidenceReference:approved[0],
  mediaKind:"movie",adapterVersion:3,approvedHosts:["licensed.example.com"],
  approvedPathPrefix:"/",reviewedAt:now-86_400_000,validUntil:now+7*86_400_000});
const grants=[grant()];

const row=()=>({id:"licensed-demo",config:{
  id:"licensed-demo",enabled:true,integrationApproved:true,
  approvalRef:"rights/2026/licensed-demo.md",
  currentUrl:"https://licensed.example.com",mediaKind:"movie",adapterVersion:3,
}});
const item=()=>({id:"licensed-demo",baseUrl:"https://licensed.example.com",
  mediaKind:"movie",adapterVersion:3});
const snapshot=()=>({generatedAt:now,expiresAt:now+900_000,sources:[item()]});

test("only canonical internal rights evidence references are accepted",()=>{
  for(const ref of ["rights/2026/licensed-demo.md","rights_2026/demo.md"])
    assert.equal(validApprovalReference(ref),true,ref);
  for(const ref of [null,"", "https://evil.example.org/rights",
    "../rights/demo.md","rights/../fake.md","rights//demo.md",
    "/rights/demo.md","rights/./demo.md","rights\\demo.md"])
    assert.equal(validApprovalReference(ref),false,String(ref));
});

test("signed publication requires rights and exact source/config identity",()=>{
  assert.deepEqual(APPROVED_RIGHTS_REFS,[]);
  assert.deepEqual(APPROVED_SOURCE_GRANTS,[]);
  assert.throws(()=>assertReviewedPublication([row()],snapshot()),
    /unreviewed_production_snapshot/);
  assert.equal(assertReviewedPublication([row()],snapshot(),approved,grants),true);
  for(const change of [
    {approvalRef:null},{approvalRef:"https://evil.example.org/rights"},
    {approvalRef:"rights/2026/other-source.md"},
    {enabled:false},{integrationApproved:false},
    {currentUrl:"https://other.example.com"},{mediaKind:"series"},
    {adapterVersion:4},{id:"other-source"},
  ])assert.throws(()=>assertReviewedPublication([
    {...row(),config:{...row().config,...change}},
  ],snapshot(),approved,grants),/unreviewed_production_snapshot/);
});

test("a separately approved rights record cannot be borrowed by another source",()=>{
  const forged={...row(),config:{...row().config,
    approvalRef:"rights/2026/other-source.md"}};
  assert.throws(()=>assertReviewedPublication([forged],snapshot(),
    [...approved,"rights/2026/other-source.md"],grants),
    /unreviewed_production_snapshot/);
});

test("missing and duplicated registry/snapshot identities fail closed",()=>{
  assert.throws(()=>assertReviewedPublication([],snapshot(),approved,grants),
    /unreviewed_production_snapshot/);
  assert.throws(()=>assertReviewedPublication([row(),row()],snapshot(),approved,grants),
    /unsafe_production_registry/);
  assert.throws(()=>assertReviewedPublication([row()],
    {generatedAt:now,expiresAt:now+900_000,sources:[item(),item()]},approved,grants),/unsafe_production_snapshot/);
  assert.throws(()=>assertReviewedPublication([row()],
    {generatedAt:now,expiresAt:now+900_000,sources:[{...item(),id:"other-source"}]},approved,grants),
    /unreviewed_production_snapshot/);
  assert.equal(assertReviewedPublication([row()],{generatedAt:now,expiresAt:now+900_000,sources:[]},approved,grants),true);
});


test("reviewed release grants pin expiry, host, path, media and adapter",()=>{
  const cases=[
    [{validUntil:now},"expired"],[{reviewedAt:now+1},"future"],
    [{validUntil:now+367*86_400_000},"overlong"],
    [{approvedHosts:["other.example.com"]},"wrong host"],
    [{approvedHosts:["127.0.0.1"]},"IP host"],
    [{approvedHosts:["host.internal"]},"internal host"],
    [{approvedPathPrefix:"/licensed"},"wrong path"],
    [{approvedPathPrefix:"/../private"},"unsafe path"],
    [{mediaKind:"series"},"wrong media"],
    [{adapterVersion:4},"wrong adapter"],
    [{evidenceReference:"rights/2026/other-source.md"},"borrowed evidence"],
  ];
  for(const [change,reason] of cases){
    const candidate={...grant(),...change};
    assert.throws(()=>assertReviewedPublication([row()],snapshot(),approved,
      [candidate]),/invalid_reviewed_source_grant|unreviewed_production_snapshot/,reason);
  }
  assert.throws(()=>assertReviewedPublication([row()],snapshot(),approved,
    [grant(),grant()]),/duplicate_reviewed_source_grant/);
  assert.throws(()=>assertReviewedPublication([row()],snapshot(),approved,[]),
    /unreviewed_production_snapshot/);
});

test("reviewed URL path requires a full segment, not a string prefix",()=>{
  const candidate={...grant(),approvedPathPrefix:"/public"};
  const valid={...row(),config:{...row().config,
    currentUrl:"https://licensed.example.com/public/child"}};
  const snap={...snapshot(),sources:[{...item(),
    baseUrl:"https://licensed.example.com/public/child"}]};
  assert.equal(assertReviewedPublication([valid],snap,approved,[candidate]),true);
  const sibling={...valid,config:{...valid.config,
    currentUrl:"https://licensed.example.com/publicity"}};
  const wrong={...snap,sources:[{...item(),
    baseUrl:"https://licensed.example.com/publicity"}]};
  assert.throws(()=>assertReviewedPublication([sibling],wrong,approved,[candidate]),
    /unreviewed_production_snapshot/);
});


test("a signed snapshot cannot remain valid after its reviewed rights expire",()=>{
  const expiresBeforeSnapshot={...grant(),validUntil:now+300_000};
  assert.throws(()=>assertReviewedPublication([row()],snapshot(),approved,
    [expiresBeforeSnapshot]),/unreviewed_production_snapshot/);
  assert.equal(assertReviewedPublication([row()],snapshot(),approved,
    [{...grant(),validUntil:now+900_000}]),true);
  assert.throws(()=>assertReviewedPublication([row()],
    {...snapshot(),expiresAt:now},approved,grants),
    /invalid_reviewed_source_grant/);
});
