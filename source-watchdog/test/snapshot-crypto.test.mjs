import test from 'node:test';
import assert from 'node:assert/strict';
import {signSnapshot, verifySnapshot, validSnapshot} from '../src/snapshot-crypto.mjs';

const now = 1_800_000_000_000;
const base = () => ({schemaVersion:1, revision:42, generatedAt:now,
  expiresAt:now+15*60_000, sources:[
    {id:'licensed-demo',mediaKind:'movie',baseUrl:'https://licensed.example.org',adapterVersion:3},
    {id:'new-adapter',mediaKind:'series',baseUrl:'https://series.example.org',adapterVersion:1},
  ]});
const keypair = () => crypto.subtle.generateKey('Ed25519',true,['sign','verify']);
const verify = (envelope, key, options={}) => verifySnapshot(envelope,
  new Map([['eafb-2026',key]]), {now,lastRevision:41,
    installedAdapters:new Map([['licensed-demo',3]]),...options});

test('valid signature authorizes ONLY matching bundled source adapters',async()=>{
  const {privateKey,publicKey}=await keypair();
  const envelope=await signSnapshot(base(),privateKey,'eafb-2026',now);
  assert.equal(envelope.algorithm,'Ed25519');
  assert.equal(envelope.signature.length,86);
  const checked=await verify(envelope,publicKey);
  assert.equal(checked.ok,true);
  assert.deepEqual(checked.usableSources.map(s=>s.id),['licensed-demo']);
  assert.equal(checked.revision,42);
});

test('tampered domain or revision is rejected after signing',async()=>{
  const {privateKey,publicKey}=await keypair();
  const envelope=await signSnapshot(base(),privateKey,'eafb-2026',now);
  const forged=structuredClone(envelope);
  forged.payload.sources[0].baseUrl='https://attacker.example.org';
  assert.equal((await verify(forged,publicKey)).reason,'invalid_signature');
  const replay=structuredClone(envelope);
  replay.payload.revision=43;
  assert.equal((await verify(replay,publicKey)).reason,'invalid_signature');
});

test('replay, expiry, unknown key, wrong key and unsigned envelope all fail closed',async()=>{
  const a=await keypair(), b=await keypair();
  const envelope=await signSnapshot(base(),a.privateKey,'eafb-2026',now);
  assert.equal((await verify(envelope,a.publicKey,{lastRevision:42})).reason,'stale_revision');
  assert.equal((await verify(envelope,a.publicKey,{now:now+15*60_000})).reason,'invalid_envelope');
  assert.equal((await verify(envelope,b.publicKey)).reason,'invalid_signature');
  assert.equal((await verifySnapshot(envelope,new Map(),{now,lastRevision:41,installedAdapters:new Map()})).reason,'unknown_signing_key');
  assert.equal((await verify({...envelope,signature:''},a.publicKey)).reason,'invalid_signature');
});

test('canonical encoding permits harmless object key reordering',async()=>{
  const {privateKey,publicKey}=await keypair();
  const envelope=await signSnapshot(base(),privateKey,'eafb-2026',now);
  const reordered=structuredClone(envelope);
  reordered.payload={sources:reordered.payload.sources.map(s=>({adapterVersion:s.adapterVersion,
    baseUrl:s.baseUrl,id:s.id,mediaKind:s.mediaKind})), expiresAt:now+900000,
    generatedAt:now,revision:42,schemaVersion:1};
  assert.equal((await verify(reordered,publicKey)).ok,true);
});

test('rejects duplicate IDs, credentials, local IP, HTTP, and unexpected fields',async()=>{
  const invalid=[
    snap=>snap.sources.push({...snap.sources[0]}),
    snap=>snap.sources[0].baseUrl='https://user:pass@licensed.example.org',
    snap=>snap.sources[0].baseUrl='https://127.0.0.1',
    snap=>snap.sources[0].baseUrl='https://host.internal',
    snap=>snap.sources[0].baseUrl='https://-invalid.example.org',
    snap=>snap.sources[0].baseUrl='https://invalid-.example.org',
    snap=>snap.sources[0].baseUrl='https://licensed.example.org/%20private',
    snap=>snap.sources[0].baseUrl='https://licensed.example.org//private',
    snap=>snap.sources[0].baseUrl='https://'+'a'.repeat(64)+'.example.org',
    snap=>snap.sources[0].baseUrl='http://licensed.example.org',
    snap=>snap.sources[0].cookie='private-token',
    snap=>snap.debug='secret',
    snap=>snap.expiresAt=now+2*60*60_000,
    snap=>snap.generatedAt=now+6*60_000,
  ];
  const {privateKey}=await keypair();
  for(const mutate of invalid){
    const snapshot=base();mutate(snapshot);
    assert.equal(validSnapshot(snapshot,now),false,JSON.stringify(snapshot));
    await assert.rejects(signSnapshot(snapshot,privateKey,'eafb-2026',now),/invalid_snapshot_sign_request/);
  }
});

test('signature from unpinned key cannot silently activate any source',async()=>{
  const {privateKey,publicKey}=await keypair();
  const env=await signSnapshot(base(),privateKey,'unknown-key',now);
  assert.equal((await verify(env,publicKey)).reason,'unknown_signing_key');
});


test('same registry revision refresh needs a newer signed generation time',async()=>{
  const {privateKey,publicKey}=await keypair();
  const fresh=base();
  fresh.generatedAt=now+120000;
  fresh.expiresAt=now+1020000;
  const envelope=await signSnapshot(fresh,privateKey,'eafb-2026',now+120000);
  const valid=await verify(envelope,publicKey,
    {now:now+120000,lastRevision:42,lastGeneratedAt:now});
  assert.equal(valid.ok,true);
  assert.equal(valid.generatedAt,now+120000);
  const replay=await verify(envelope,publicKey,
    {now:now+120000,lastRevision:42,lastGeneratedAt:now+120000});
  assert.equal(replay.reason,'stale_revision');
  const noTimestamp=await verify(envelope,publicKey,
    {now:now+120000,lastRevision:42});
  assert.equal(noTimestamp.reason,'stale_revision');
});
