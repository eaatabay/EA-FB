import {signSnapshot} from './snapshot-crypto.mjs';

/**
 * Private Worker-only boundary. Dependency-injected buildSnapshot is the
 * unpublished, transactionally read D1 snapshot producer. No HTTP route.
 * Import PKCS#8 from a Cloudflare secret, never commit or log the key.
 */
export async function publishSignedSnapshot(db, env, now, buildSnapshot) {
  if (!db?.prepare || typeof buildSnapshot !== 'function' ||
      !Number.isSafeInteger(now) || now < 0 ||
      typeof env?.SNAPSHOT_SIGNING_KEY_ID !== 'string' ||
      typeof env?.SNAPSHOT_SIGNING_PKCS8_B64 !== 'string' ||
      env.SNAPSHOT_SIGNING_PKCS8_B64.length > 8192 ||
      !/^[A-Za-z0-9+/]+={0,2}$/.test(env.SNAPSHOT_SIGNING_PKCS8_B64)) {
    throw new Error('snapshot_signing_unconfigured');
  }
  let key;
  try {
    const binary = atob(env.SNAPSHOT_SIGNING_PKCS8_B64);
    const bytes = Uint8Array.from(binary, c => c.charCodeAt(0));
    key = await crypto.subtle.importKey('pkcs8', bytes, 'Ed25519', false, ['sign']);
  } catch {
    throw new Error('invalid_snapshot_signing_secret');
  }
  // Do not sign unknown, unreviewed, disabled or stale sources. The D1
  // builder must filter all these and return a versioned pure snapshot.
  const snapshot = await buildSnapshot(db, now);
  return signSnapshot(snapshot, key, env.SNAPSHOT_SIGNING_KEY_ID, now);
}
