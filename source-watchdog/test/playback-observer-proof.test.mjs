import test from "node:test";
import assert from "node:assert/strict";
import {webcrypto} from "node:crypto";
import {createPlaybackObserverVerifier,playbackObserverMessage}
  from "../src/playback-observer-proof.mjs";

const keyBytes = new Uint8Array(32).fill(42); // TEST ONLY, never deploy.
const nowMs=1_000_000;
const eventBase={
  outcome:"success",
  media:{kind:"series",tmdbId:42,season:3,episode:2},
  source:{sourceId:"source-a",variantId:"tr-1080"},
  proof:{eventId:"abcdefghijklmno0123456789",observedAtMs:999_999},
};
function mockDb() {
  const seen=new Set();
  return {seen,prepare(sql){
    assert.match(sql,/INSERT INTO playback_observer_receipts/);
    return {bind(eventId,expiry){return {async run(){
      assert.equal(expiry,nowMs+60_000);
      if(seen.has(eventId))return {meta:{changes:0}};
      seen.add(eventId);
      return {meta:{changes:1}};
    }};}};
  }};
}
async function signedEvent(event) {
  const key=await webcrypto.subtle.importKey("raw",keyBytes,
    {name:"HMAC",hash:"SHA-256"},false,["sign"]);
  const sig=new Uint8Array(await webcrypto.subtle.sign("HMAC",key,
    new TextEncoder().encode(playbackObserverMessage(event))));
  const signature=Buffer.from(sig).toString("base64url");
  return {...event,proof:{...event.proof,signature}};
}

test("valid independent observer proof is accepted exactly once",async()=>{
  const db=mockDb();
  const verify=await createPlaybackObserverVerifier(db,keyBytes,webcrypto.subtle);
  const event=await signedEvent(eventBase);
  assert.deepEqual(await verify(event,nowMs),{
    verified:true,outcome:"success",sourceId:"source-a",
    variantId:"tr-1080",mediaKind:"series",tmdbId:42,
    season:3,episode:2,observedAtMs:999_999,
  });
  await assert.rejects(verify(event,nowMs),/replayed_observer_message/);
  assert.equal(db.seen.size,1);
});

test("tampered media, variant, outcome or timestamp cannot reuse signature",async()=>{
  const db=mockDb();
  const verify=await createPlaybackObserverVerifier(db,keyBytes,webcrypto.subtle);
  const event=await signedEvent(eventBase);
  for(const changed of [
    {...event,media:{...event.media,episode:3}},
    {...event,source:{...event.source,variantId:"other"}},
    {...event,source:{...event.source,audioLanguage:"tr"}},
    {...event,source:{...event.source,quality:1080}},
    {...event,outcome:"failure"},
    {...event,proof:{...event.proof,observedAtMs:999_998}},
  ]) await assert.rejects(verify(changed,nowMs),/invalid_observer_signature/);
  assert.equal(db.seen.size,0);
});

test("expired, future, malformed and unconfigured observer fails closed",async()=>{
  const db=mockDb();
  const verify=await createPlaybackObserverVerifier(db,keyBytes,webcrypto.subtle);
  const event=await signedEvent(eventBase);
  await assert.rejects(verify(event,nowMs+60_001),
    /expired_observer_message/);
  await assert.rejects(verify(event,999_998),
    /expired_observer_message/);
  await assert.rejects(verify({...event,proof:{...event.proof,
    signature:"not-a-signature"}},nowMs),/invalid_observer_signature/);
  assert.equal(db.seen.size,0);
  await assert.rejects(createPlaybackObserverVerifier(db,
    new Uint8Array(16),webcrypto.subtle),/observer_verifier_not_configured/);
  assert.throws(()=>playbackObserverMessage({
    ...eventBase,proof:{...eventBase.proof,eventId:"short"},
  }),/invalid_observer_message/);
});
