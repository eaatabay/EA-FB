import test from "node:test";
import assert from "node:assert/strict";
import {recordVerifiedPlayback} from "../src/trusted-playback-recorder.mjs";

const media={kind:"series",tmdbId:42,season:3,episode:2};
const source={sourceId:"source-a",variantId:"tr-1080",audioLanguage:"tr",quality:1080};
const event={media,source,outcome:"success"};
const nowMs=100_000;
const db={prepare() {}};
const record={id:"source-a",
  config:{id:"source-a",enabled:true,integrationApproved:true,mediaKind:"series"},
  state:{id:"source-a",status:"healthy"}};
const proof={verified:true,outcome:"success",sourceId:"source-a",
  variantId:"tr-1080",mediaKind:"series",tmdbId:42,season:3,
  episode:2,observedAtMs:99_999};
const opts={db,event,nowMs,approvedSourceIds:["source-a"],
  verifyEvidence:async()=>proof,readSource:async()=>record};

test("verified playback success writes only after proof and healthy rights grant",async()=>{
  let writes=0;
  const result=await recordVerifiedPlayback({...opts,
    writeSuccess:async(_db,m,s,at)=>{
      writes++;assert.equal(m,media);assert.equal(s,source);
      assert.equal(at,nowMs);return {stored:true};
    }});
  assert.deepEqual(result,{stored:true});
  assert.equal(writes,1);
});

test("no verifier, no rights grant, no client assertion",async()=>{
  let reads=0;
  const readSource=async()=>{reads++;return record;};
  await assert.rejects(recordVerifiedPlayback({...opts,verifyEvidence:undefined,
    readSource}),/untrusted_playback_event/);
  await assert.rejects(recordVerifiedPlayback({...opts,approvedSourceIds:[],
    readSource}),/source_not_permitted/);
  await assert.rejects(recordVerifiedPlayback({...opts,approvedSourceIds:["fixture-a"],
    readSource}),/untrusted_playback_event/);
  assert.equal(reads,0);
});

test("proof mismatch, stale or future evidence fails before DB lookup",async()=>{
  let reads=0;
  for(const patch of [
    {verified:false},{sourceId:"source-b"},{variantId:"other"},
    {episode:3},{tmdbId:43},{mediaKind:"movie"},
    {observedAtMs:39_999},{observedAtMs:100_001},
    {outcome:"failure"},
  ]) {
    await assert.rejects(recordVerifiedPlayback({...opts,
      verifyEvidence:async()=>({...proof,...patch}),
      readSource:async()=>{reads++;return record;},
    }),/untrusted_playback_evidence/);
  }
  assert.equal(reads,0);
});

test("disabled, degraded or mismatched source never writes",async()=>{
  let writes=0;
  for(const changed of [
    {config:{...record.config,enabled:false}},
    {config:{...record.config,integrationApproved:false}},
    {config:{...record.config,mediaKind:"movie"}},
    {state:{...record.state,status:"degraded"}},
    {id:"source-b"},
  ]) {
    await assert.rejects(recordVerifiedPlayback({...opts,
      readSource:async()=>({...record,...changed}),
      writeSuccess:async()=>{writes++;},
    }),/source_not_healthy/);
  }
  assert.equal(writes,0);
});

test("trusted failure only expires the exact candidate",async()=>{
  let successWrites=0;
  const result=await recordVerifiedPlayback({...opts,
    event:{...event,outcome:"failure"},
    verifyEvidence:async()=>({...proof,outcome:"failure"}),
    writeSuccess:async()=>{successWrites++;},
    expireCandidate:async(_db,m,s,at)=>{
      assert.equal(m,media);assert.equal(s,source);
      assert.equal(at,nowMs);return {expired:true};
    },
  });
  assert.deepEqual(result,{expired:true});
  assert.equal(successWrites,0);
});
