import test from "node:test";
import assert from "node:assert/strict";
import {validApprovalReference,assertReviewedPublication,APPROVED_RIGHTS_REFS} from "../src/publication-guard.mjs";
const approved=["rights/2026/licensed-demo.md"];

const row=()=>({id:"licensed-demo",config:{
  id:"licensed-demo",enabled:true,integrationApproved:true,
  approvalRef:"rights/2026/licensed-demo.md",
  currentUrl:"https://licensed.example.com",mediaKind:"movie",adapterVersion:3,
}});
const item=()=>({id:"licensed-demo",baseUrl:"https://licensed.example.com",
  mediaKind:"movie",adapterVersion:3});
const snapshot=()=>({sources:[item()]});

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
  assert.throws(()=>assertReviewedPublication([row()],snapshot()),
    /unreviewed_production_snapshot/);
  assert.equal(assertReviewedPublication([row()],snapshot(),approved),true);
  for(const change of [
    {approvalRef:null},{approvalRef:"https://evil.example.org/rights"},
    {approvalRef:"rights/2026/other-source.md"},
    {enabled:false},{integrationApproved:false},
    {currentUrl:"https://other.example.com"},{mediaKind:"series"},
    {adapterVersion:4},{id:"other-source"},
  ])assert.throws(()=>assertReviewedPublication([
    {...row(),config:{...row().config,...change}},
  ],snapshot(),approved),/unreviewed_production_snapshot/);
});

test("a separately approved rights record cannot be borrowed by another source",()=>{
  const forged={...row(),config:{...row().config,
    approvalRef:"rights/2026/other-source.md"}};
  assert.throws(()=>assertReviewedPublication([forged],snapshot(),
    [...approved,"rights/2026/other-source.md"]),
    /unreviewed_production_snapshot/);
});

test("missing and duplicated registry/snapshot identities fail closed",()=>{
  assert.throws(()=>assertReviewedPublication([],snapshot(),approved),
    /unreviewed_production_snapshot/);
  assert.throws(()=>assertReviewedPublication([row(),row()],snapshot(),approved),
    /unsafe_production_registry/);
  assert.throws(()=>assertReviewedPublication([row()],
    {sources:[item(),item()]},approved),/unsafe_production_snapshot/);
  assert.throws(()=>assertReviewedPublication([row()],
    {sources:[{...item(),id:"other-source"}]},approved),
    /unreviewed_production_snapshot/);
  assert.equal(assertReviewedPublication([row()],{sources:[]},approved),true);
});
