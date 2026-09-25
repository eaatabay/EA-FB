import {
  setEnabled, retestAfterReview, rollbackToLastHealthy,
} from "./registry.mjs";

/**
 * Deliberately narrow admin mutation boundary. A future reviewed release may
 * enable ONLY enable/disable/retest/rollback. No new domains, approval grant,
 * arbitrary SQL, parser updates, or public source registration through HTTP.
 * D1 trigger-backed audit and exact-revision CAS remain mandatory.
 */
export class AdminMutationError extends Error {
  constructor(code, status = 400) {
    super(code);
    this.name = "AdminMutationError";
    this.status = status;
  }
}
const SOURCE_ID = /^[a-z][a-z0-9-]{2,63}$/;
const ALLOWED_ACTIONS = new Set(["disable", "enable", "retest", "rollback"]);
const ORIGIN = /^https:\/\/[a-z0-9-]+(?:\.[a-z0-9-]+)+$/;
const MAX_BYTES = 1024;

export function adminWritesConfigured(env) {
  return env?.WATCHDOG_ADMIN_WRITES_ENABLED === "true" &&
    env?.WATCHDOG_ADMIN_ENABLED === "true" &&
    env?.WATCHDOG_MODE === "production" &&
    env?.WATCHDOG_FIXTURE_ENABLED !== "true" &&
    typeof env?.WATCHDOG_ADMIN_ORIGIN === "string" &&
    ORIGIN.test(env.WATCHDOG_ADMIN_ORIGIN) &&
    !new URL(env.WATCHDOG_ADMIN_ORIGIN).hostname.endsWith(".localhost") &&
    !new URL(env.WATCHDOG_ADMIN_ORIGIN).hostname.endsWith(".local") &&
    !new URL(env.WATCHDOG_ADMIN_ORIGIN).hostname.endsWith(".internal") &&
    !new URL(env.WATCHDOG_ADMIN_ORIGIN).hostname.endsWith(".invalid") &&
    !/^\d+(?:\.\d+){3}$/.test(new URL(env.WATCHDOG_ADMIN_ORIGIN).hostname);
}

/** Extra/ambiguous keys are rejected, not silently ignored. */
export function validateAdminMutation(sourceId, value) {
  if (typeof sourceId !== "string" || !SOURCE_ID.test(sourceId) ||
      !value || typeof value !== "object" || Array.isArray(value) ||
      Object.keys(value).sort().join(",") !== "action,expectedRevision" ||
      typeof value.action !== "string" || !ALLOWED_ACTIONS.has(value.action) ||
      !Number.isSafeInteger(value.expectedRevision) ||
      value.expectedRevision < 0) {
    throw new AdminMutationError("invalid_admin_action");
  }
  return {sourceId, action: value.action, expectedRevision: value.expectedRevision};
}

/** Limit the actual streaming body, not just the attacker-supplied content-length. */
async function boundedJSON(request) {
  if (!request.body) throw new AdminMutationError("empty_admin_body");
  const reader = request.body.getReader();
  const chunks = [];
  let size = 0;
  try {
    while (true) {
      const {done, value} = await reader.read();
      if (done) break;
      if (!(value instanceof Uint8Array)) {
        throw new AdminMutationError("invalid_admin_body");
      }
      size += value.byteLength;
      if (size > MAX_BYTES) {
        await reader.cancel();
        throw new AdminMutationError("admin_body_too_large", 413);
      }
      chunks.push(value);
    }
  } finally {
    reader.releaseLock();
  }
  const merged = new Uint8Array(size);
  let offset = 0;
  for (const chunk of chunks) {
    merged.set(chunk, offset);
    offset += chunk.length;
  }
  try {
    return JSON.parse(new TextDecoder("utf-8", {fatal: true}).decode(merged));
  } catch {
    throw new AdminMutationError("invalid_admin_json");
  }
}

/** Called ONLY after Cloudflare Access JWT signature + admin allowlist check. */
export async function parseAdminMutationRequest(request, env) {
  if (!adminWritesConfigured(env)) throw new AdminMutationError("not_found", 404);
  if (request?.method !== "POST") throw new AdminMutationError("method_not_allowed", 405);
  const url = new URL(request.url);
  if (url.origin !== env.WATCHDOG_ADMIN_ORIGIN ||
      request.headers.get("origin") !== env.WATCHDOG_ADMIN_ORIGIN ||
      request.headers.get("x-eafb-admin-action") !== "confirmed" ||
      !/^application\/json(?:\s*;\s*charset=utf-8)?$/i.test(
        request.headers.get("content-type") || "") ||
      !["same-origin", "none", null].includes(request.headers.get("sec-fetch-site"))) {
    throw new AdminMutationError("invalid_admin_origin", 403);
  }
  const match = /^\/admin\/api\/sources\/([a-z][a-z0-9-]{2,63})$/.exec(url.pathname);
  if (!match || url.search || url.hash) throw new AdminMutationError("not_found", 404);
  const contentLength = request.headers.get("content-length");
  if (contentLength !== null &&
      (!/^[0-9]+$/.test(contentLength) || Number(contentLength) > MAX_BYTES)) {
    throw new AdminMutationError("admin_body_too_large", 413);
  }
  return validateAdminMutation(match[1], await boundedJSON(request));
}

/** Hash the verified identity into the registry's limited actor field. */
export async function actorForVerifiedEmail(email) {
  if (typeof email !== "string" || email.length > 254 ||
      !/^[^@\s,]+@[^@\s,]+\.[^@\s,]+$/.test(email)) {
    throw new AdminMutationError("invalid_verified_identity", 403);
  }
  const bytes = await crypto.subtle.digest("SHA-256", new TextEncoder()
    .encode("EA-FB/admin/v1\n" + email.toLowerCase()));
  return "admin:" + Array.from(new Uint8Array(bytes)).slice(0, 16)
    .map(x => x.toString(16).padStart(2, "0")).join("");
}

/**
 * Returns no URLs or private state. All mutations use optional exact-revision
 * checks INSIDE the corresponding registry operation before its atomic CAS.
 */
export async function executeAdminMutation(db, action, verifiedEmail, now) {
  if (!db?.prepare || !Number.isSafeInteger(now) || now < 0) {
    throw new AdminMutationError("admin_unavailable", 503);
  }
  const {sourceId, expectedRevision} = validateAdminMutation(action?.sourceId,
    {action: action?.action, expectedRevision: action?.expectedRevision});
  const actor = await actorForVerifiedEmail(verifiedEmail);
  let result;
  switch (action.action) {
    case "disable":
      result = await setEnabled(db, sourceId, false, actor, now, expectedRevision);
      break;
    case "enable":
      result = await setEnabled(db, sourceId, true, actor, now, expectedRevision);
      break;
    case "retest":
      result = await retestAfterReview(db, sourceId, actor, now, expectedRevision);
      break;
    case "rollback":
      result = await rollbackToLastHealthy(db, sourceId, actor, now, expectedRevision);
      break;
    default:
      throw new AdminMutationError("invalid_admin_action");
  }
  return {sourceId, revision: result.record.revision,
    state: result.record.state.status, unchanged: result.unchanged === true};
}
