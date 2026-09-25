import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {fileURLToPath} from 'node:url';
import {dirname,resolve} from 'node:path';
import {verifySnapshot} from '../src/snapshot-crypto.mjs';

/** PUBLIC Node-generated test vector also verified by core-tests Kotlin/BC. */
const path=resolve(dirname(fileURLToPath(import.meta.url)),
  '../../core-tests/fixtures/signed-source-snapshot-vector.json');
const fixture=JSON.parse(readFileSync(path,'utf8'));
const {note, publicKeyBase64, ...envelope}=fixture;
const now=envelope.payload.generatedAt;

test('the same signed fixture is understood by the JS and Android/Kotlin contract',async()=>{
  assert.match(note,/PUBLIC TEST VECTOR/);
  const publicKey=await crypto.subtle.importKey(
    'raw',Buffer.from(publicKeyBase64,'base64'),'Ed25519',false,['verify']);
  const checked=await verifySnapshot(envelope,
    new Map([['test-only-2026',publicKey]]),
    {now,lastRevision:41,installedAdapters:new Map([['licensed-demo',3]])});
  assert.equal(checked.ok,true);
  assert.deepEqual(checked.usableSources.map(s=>s.id),['licensed-demo']);
  const changed=structuredClone(envelope);
  changed.payload.sources[0].baseUrl='https://attacker.example.org';
  assert.equal((await verifySnapshot(changed,new Map([['test-only-2026',publicKey]]),
    {now,lastRevision:41,installedAdapters:new Map([['licensed-demo',3]])})).reason,
    'invalid_signature');
});
