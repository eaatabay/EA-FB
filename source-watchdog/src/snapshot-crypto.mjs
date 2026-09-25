/**
 * EA-FB Source Watchdog signed read-only snapshot contract.
 * Pure WebCrypto/ESM: no network, secrets, storage, Worker endpoints or keys in repo.
 * The PRIVATE signing key belongs only in a future isolated Worker secret.
 * The Android client must pin the corresponding public key and its adapter IDs.
 */
const CONTEXT = 'EA-FB/SOURCE-SNAPSHOT/V1\n';
const MAX_TTL_MS = 60 * 60_000;
const CLOCK_SKEW_MS = 5 * 60_000;
const encoder = new TextEncoder();
const ownKeys = (obj, fields) => obj && typeof obj === 'object' &&
  !Array.isArray(obj) && Object.keys(obj).sort().join('|') === [...fields].sort().join('|');

function validHttpsBaseUrl(value) {
  if (typeof value !== 'string' || value.length > 2048) return false;
  try {
    const u = new URL(value);
    const host = u.hostname;
    return u.protocol === 'https:' && u.username === '' && u.password === '' &&
      u.port === '' && u.search === '' && u.hash === '' &&
      /^[a-z0-9-]+(?:\.[a-z0-9-]+)+$/.test(host) &&
      !/^\d+(?:\.\d+){3}$/.test(host) &&
      !/\.(?:local|localhost|internal|invalid)$/.test(host) &&
      u.origin + (u.pathname === '/' ? '' : u.pathname.replace(/\/+$/, '')) === value;
  } catch { return false; }
}

export function validSnapshot(snapshot, now) {
  if (!Number.isSafeInteger(now) || now < 0 ||
      !ownKeys(snapshot, ['schemaVersion', 'revision', 'generatedAt', 'expiresAt', 'sources']) ||
      snapshot.schemaVersion !== 1 || !Number.isSafeInteger(snapshot.revision) ||
      snapshot.revision < 0 || !Number.isSafeInteger(snapshot.generatedAt) ||
      snapshot.generatedAt < 0 || snapshot.generatedAt > now + CLOCK_SKEW_MS ||
      !Number.isSafeInteger(snapshot.expiresAt) ||
      snapshot.expiresAt <= now || snapshot.expiresAt <= snapshot.generatedAt ||
      snapshot.expiresAt - snapshot.generatedAt > MAX_TTL_MS ||
      !Array.isArray(snapshot.sources) || snapshot.sources.length > 256) return false;
  const ids = new Set();
  for (const source of snapshot.sources) {
    if (!ownKeys(source, ['id', 'mediaKind', 'baseUrl', 'adapterVersion']) ||
        typeof source.id !== 'string' || !/^[a-z][a-z0-9-]{2,63}$/.test(source.id) ||
        ids.has(source.id) || !['movie', 'series', 'both'].includes(source.mediaKind) ||
        !validHttpsBaseUrl(source.baseUrl) ||
        !Number.isSafeInteger(source.adapterVersion) ||
        source.adapterVersion < 1 || source.adapterVersion > 1_000_000) return false;
    ids.add(source.id);
  }
  return true;
}

function canonical(value) {
  if (value === null || typeof value !== 'object') return JSON.stringify(value);
  if (Array.isArray(value)) return '[' + value.map(canonical).join(',') + ']';
  return '{' + Object.keys(value).sort().map(key =>
    JSON.stringify(key) + ':' + canonical(value[key])).join(',') + '}';
}
const payloadBytes = snapshot => encoder.encode(CONTEXT + canonical(snapshot));
function toBase64Url(bytes) {
  let binary = '';
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/g, '');
}
function fromBase64Url(value) {
  if (typeof value !== 'string' || !/^[A-Za-z0-9_-]{86}$/.test(value)) return null;
  try {
    const binary = atob(value.replace(/-/g, '+').replace(/_/g, '/') + '==');
    if (binary.length !== 64) return null;
    return Uint8Array.from(binary, char => char.charCodeAt(0));
  } catch { return null; }
}

export async function signSnapshot(snapshot, privateKey, keyId, now) {
  if (!validSnapshot(snapshot, now) || typeof keyId !== 'string' ||
      !/^[a-zA-Z0-9_-]{3,48}$/.test(keyId) ||
      privateKey?.algorithm?.name !== 'Ed25519' ||
      privateKey.type !== 'private' || !privateKey.usages.includes('sign')) {
    throw new Error('invalid_snapshot_sign_request');
  }
  const signature = await crypto.subtle.sign('Ed25519', privateKey, payloadBytes(snapshot));
  return { envelopeVersion: 1, algorithm: 'Ed25519', keyId,
    payload: snapshot, signature: toBase64Url(new Uint8Array(signature)) };
}

/**
 * publicKeys must be a trusted, app-pinned Map<keyId, Ed25519 CryptoKey>.
 * installedAdapters is Map<id, exact bundled adapterVersion>; unknown versions
 * are excluded, never fetched or dynamically executed.
 */
export async function verifySnapshot(envelope, publicKeys, {
  now, lastRevision = -1, lastGeneratedAt = -1, installedAdapters,
} = {}) {
  const reject = reason => ({ok: false, reason});
  if (!ownKeys(envelope, ['envelopeVersion', 'algorithm', 'keyId', 'payload', 'signature']) ||
      envelope.envelopeVersion !== 1 || envelope.algorithm !== 'Ed25519' ||
      typeof envelope.keyId !== 'string' || !/^[a-zA-Z0-9_-]{3,48}$/.test(envelope.keyId) ||
      !(publicKeys instanceof Map) || !(installedAdapters instanceof Map) ||
      !Number.isSafeInteger(lastRevision) || lastRevision < -1 ||
      !Number.isSafeInteger(lastGeneratedAt) || lastGeneratedAt < -1 ||
      !validSnapshot(envelope.payload, now)) return reject('invalid_envelope');
  if (envelope.payload.revision < lastRevision ||
      (envelope.payload.revision === lastRevision &&
       (lastGeneratedAt < 0 || envelope.payload.generatedAt <= lastGeneratedAt))) {
    return reject('stale_revision');
  }
  const key = publicKeys.get(envelope.keyId);
  if (!key || key.algorithm?.name !== 'Ed25519' || key.type !== 'public' ||
      !key.usages.includes('verify')) return reject('unknown_signing_key');
  const bytes = fromBase64Url(envelope.signature);
  if (!bytes) return reject('invalid_signature');
  let verified = false;
  try {
    verified = await crypto.subtle.verify('Ed25519', key, bytes,
      payloadBytes(envelope.payload));
  } catch { /* Reject malformed keys or unsupported runtime. */ }
  if (!verified) return reject('invalid_signature');
  const usableSources = envelope.payload.sources.filter(source =>
    installedAdapters.get(source.id) === source.adapterVersion);
  return {ok: true, revision: envelope.payload.revision,
    generatedAt: envelope.payload.generatedAt, expiresAt: envelope.payload.expiresAt, usableSources};
}
