import test from 'node:test';
import assert from 'node:assert/strict';
import {publishSignedSnapshot} from '../src/snapshot-publisher.mjs';
import {verifySnapshot} from '../src/snapshot-crypto.mjs';

const now=1_800_000_000_000;
const payload={schemaVersion:1,revision:5,generatedAt:now,expiresAt:now+900000,
  sources:[{id:'licensed-demo',mediaKind:'movie',baseUrl:'https://licensed.example.org',adapterVersion:1}]};
const keypair=()=>crypto.subtle.generateKey('Ed25519',true,['sign','verify']);
const credential=async privateKey=>({SNAPSHOT_SIGNING_KEY_ID:'eafb-2026',
  SNAPSHOT_SIGNING_PKCS8_B64:Buffer.from(await crypto.subtle.exportKey('pkcs8',privateKey)).toString('base64')});

test('Worker secret signs unpublished D1 snapshot; public key validates without private key',async()=>{
  const {privateKey,publicKey}=await keypair(),env=await credential(privateKey);
  const envelope=await publishSignedSnapshot({prepare(){}},env,now,async()=>payload);
  assert.equal(envelope.signature.length,86);
  assert.equal(JSON.stringify(envelope).includes(env.SNAPSHOT_SIGNING_PKCS8_B64),false);
  const result=await verifySnapshot(envelope,new Map([['eafb-2026',publicKey]]),
    {now,lastRevision:4,installedAdapters:new Map([['licensed-demo',1]])});
  assert.equal(result.ok,true);
  assert.equal(result.usableSources.length,1);
});

test('missing or invalid private key fails closed without exposing secrets',async()=>{
  const {privateKey}=await keypair();
  const env=await credential(privateKey);
  await assert.rejects(publishSignedSnapshot({prepare(){}},{},now,async()=>payload),
    /snapshot_signing_unconfigured/);
  await assert.rejects(publishSignedSnapshot({prepare(){}},{...env,SNAPSHOT_SIGNING_PKCS8_B64:'YWJj'},now,async()=>payload),
    /invalid_snapshot_signing_secret/);
});

test('publisher rejects invalid D1 snapshot, not signable even with valid secret',async()=>{
  const {privateKey}=await keypair();
  const env=await credential(privateKey);
  await assert.rejects(publishSignedSnapshot({prepare(){}},env,now,
    async()=>({...payload, sources:[{id:'invalid',baseUrl:'http://local',mediaKind:'movie',adapterVersion:1}]})),
    /invalid_snapshot_sign_request/);
});
