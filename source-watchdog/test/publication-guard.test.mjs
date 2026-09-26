import test from "node:test";
import assert from "node:assert/strict";
import {validApprovalReference,assertReviewedPublication} from "../src/publication-guard.mjs";

const row=()=>({id:"licensed-demo",config:{
  id:"licensed-demo",enabled:true,integrationApproved:true,
  approvalRef:"rights/test/fixture-license.md",
  currentUrl:"https://licensed.example.com",mediaKind:"movie",adapterVersion:3,
}});
const item=()=>({id:"licensed-demo",baseUrl:"https://licensed.example.com",
  mediaKind:"movie",adapterVersion:3});
const snapshot=()=>({sources:[item()]});

test("only canonical internal rights evidence references are accepted",()=>{
  for(const ref of ["rights/test/fixture-license.md","rights_2026/demo.md"])
    assert.equal(validApprovalReference(ref),true,ref);
  for(const ref of [null,"", "https://evil.example.org/rights",
    "../rights/demo.md","rights/../fake.md","rights//demo.md",
    "/rights/demo.md","rights/./demo.md","rights\\demo.md"])
    assert.equal(validApprovalReference(ref),false,String(ref));
});

test("signed publication requires rights and exact source/config identity",()=>{
  assert.equal(assertReviewedPublication([row()],snapshot()),true);
  for(const change of [
    {approvalRef:null},{approvalRef:"https://evil.example.org/rights"},
    {enabled:false},{integrationApproved:false},
    {currentUrl:"https://other.example.com"},{mediaKind:"series"},
    {adapterVersion:4},{id:"other-source"},
  ])assert.throws(()=>assertReviewedPublication([
    {...row(),config:{...row().config,...change}},
  ],snapshot()),/unreviewed_production_snapshot/);
});

test("missing and duplicated registry/snapshot identities fail closed",()=>{
  assert.throws(()=>assertReviewedPublication([],snapshot()),
    /unreviewed_production_snapshot/);
  assert.throws(()=>assertReviewedPublication([row(),row()],snapshot()),
    /unsafe_production_registry/);
  assert.throws(()=>assertReviewedPublication([row()],
    {sources:[item(),item()]}),/unsafe_production_snapshot/);
  assert.throws(()=>assertReviewedPublication([row()],
    {sources:[{...item(),id:"other-source"}]}),
    /unreviewed_production_snapshot/);
  assert.equal(assertReviewedPublication([row()],{sources:[]}),true);
});
