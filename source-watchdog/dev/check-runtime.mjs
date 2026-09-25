/** Fail release verification rather than silently skipping SQLite tests. */
const major = Number(process.versions.node.split('.')[0]);
if (!Number.isSafeInteger(major) || major < 22) {
  throw new Error('Watchdog release verification requires Node >=22');
}
const {DatabaseSync} = await import('node:sqlite');
if (typeof DatabaseSync !== 'function') throw new Error('node:sqlite unavailable');
const db = new DatabaseSync(':memory:');
try { db.prepare('SELECT 1 AS ok').get(); } finally { db.close(); }
const keys = await crypto.subtle.generateKey('Ed25519', false, ['sign','verify']);
const testData = new TextEncoder().encode('offline capability check');
const sig = await crypto.subtle.sign('Ed25519', keys.privateKey, testData);
if (!await crypto.subtle.verify('Ed25519', keys.publicKey, sig, testData)) {
  throw new Error('Ed25519 WebCrypto unavailable');
}
console.log('PASS Node >=22 + SQLite + WebCrypto Ed25519');
