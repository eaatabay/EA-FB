/** Persistent, per-source D1 lease; no external requests or public endpoints. */
const SOURCE_ID = /^[a-z][a-z0-9-]{2,63}$/;
const TOKEN = /^[a-zA-Z0-9][a-zA-Z0-9_.:-]{11,95}$/;

function validate(id, token) {
  if (typeof id !== "string" || !SOURCE_ID.test(id) ||
      typeof token !== "string" || !TOKEN.test(token)) {
    throw new Error("invalid_probe_lease_identity");
  }
}

/** Claim expires in 30-120 seconds. An expired owner cannot release its successor's lease. */
export async function claimProbeLease(db, sourceId, token, now, leaseMs = 45_000) {
  validate(sourceId, token);
  if (!db?.prepare || !Number.isSafeInteger(now) || now < 0 ||
      !Number.isSafeInteger(leaseMs) || leaseMs < 30_000 || leaseMs > 120_000 ||
      !Number.isSafeInteger(now + leaseMs)) throw new Error("invalid_probe_lease_duration");
  const result = await db.prepare(
    "INSERT INTO source_probe_leases(source_id,lease_token,acquired_at_ms,expires_at_ms) " +
    "VALUES(?,?,?,?) ON CONFLICT(source_id) DO UPDATE SET " +
    "lease_token=excluded.lease_token, acquired_at_ms=excluded.acquired_at_ms, " +
    "expires_at_ms=excluded.expires_at_ms " +
    "WHERE source_probe_leases.expires_at_ms <= excluded.acquired_at_ms"
  ).bind(sourceId, token, now, now + leaseMs).run();
  return result?.meta?.changes === 1;
}

export async function stillOwnsProbeLease(db, sourceId, token, now) {
  validate(sourceId, token);
  if (!Number.isSafeInteger(now) || now < 0) throw new Error("invalid_clock");
  const result = await db.prepare(
    "SELECT 1 AS owned FROM source_probe_leases " +
    "WHERE source_id=? AND lease_token=? AND expires_at_ms>?"
  ).bind(sourceId, token, now).first();
  return result?.owned === 1;
}

/** Idempotent and token-scoped; cannot delete another runner's lease. */
export async function releaseProbeLease(db, sourceId, token) {
  validate(sourceId, token);
  const result = await db.prepare(
    "DELETE FROM source_probe_leases WHERE source_id=? AND lease_token=?"
  ).bind(sourceId, token).run();
  return result?.meta?.changes === 1;
}
