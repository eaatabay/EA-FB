import test from "node:test";
import assert from "node:assert/strict";
import {acceptTrustedPlaybackObservation}
  from "../src/playback-observer-service.mjs";

const db={prepare(){}};
const keyBytes=new Uint8Array(32).fill(17); // TEST ONLY
test("observer disabled without server secret or rights grant",async()=>{
  let called=0;
  const createVerifier=async()=>{called++;return async()=>({verified:true});};
  for(const options of [
    {db,approvedSourceIds:["source-a"]},
    {db,keyBytes,approvedSourceIds:[]},
    {db,keyBytes,approvedSourceIds:null},
    {db,keyBytes:new Uint8Array(8),approvedSourceIds:["source-a"]},
  ]) await assert.rejects(acceptTrustedPlaybackObservation({
    ...options,createVerifier,
  }),/playback_observer_disabled/);
  assert.equal(called,0);
});

test("valid internal wiring forwards verifier and exact event unchanged",async()=>{
  const event={outcome:"success"};
  const verifyEvidence=async()=>({verified:true});
  let verifierBuilt=0;
  const result=await acceptTrustedPlaybackObservation({
    db,keyBytes,approvedSourceIds:["source-a"],event,nowMs:1000,
    createVerifier:async(actualDb,actualKey)=>{
      assert.equal(actualDb,db);assert.equal(actualKey,keyBytes);
      verifierBuilt++;return verifyEvidence;
    },
    recordEvent:async args=>{
      assert.equal(args.verifyEvidence,verifyEvidence);
      assert.equal(args.event,event);
      assert.equal(args.nowMs,1000);
      assert.deepEqual(args.approvedSourceIds,["source-a"]);
      return {stored:true};
    },
  });
  assert.deepEqual(result,{stored:true});
  assert.equal(verifierBuilt,1);
});
