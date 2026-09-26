import { normalizedHttpsUrl } from "./policy.mjs";
/** Pure, offline final authorization boundary for signed device publication. */
const SOURCE_ID = /^[a-z][a-z0-9-]{2,63}$/;

export function validApprovalReference(value) {
  return typeof value === "string" && value.length >= 8 && value.length <= 160 &&
    /^[A-Za-z0-9_-]+(?:\/[A-Za-z0-9_.-]+)*$/.test(value) &&
    value.split("/").every(part => part !== "." && part !== "..");
}

/** Empty in the tracked v6 build. Explicit human review is needed before any live rights approval. */
export const APPROVED_RIGHTS_REFS = Object.freeze([]);
/** Independently reviewed, release-pinned host/path/version/date scopes. EMPTY in v6. */
export const APPROVED_SOURCE_GRANTS = Object.freeze([]);
const MAX_REVIEW_AGE_MS = 366 * 86_400_000;
const HOST = /^[a-z0-9](?:[a-z0-9-]*[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]*[a-z0-9])?)+$/;
const PATH = /^\\/(?:[a-zA-Z0-9_.-]+(?:\\/[a-zA-Z0-9_.-]+)*)?$/;

function reviewedGrant(grant, at) {
  if (!grant || !SOURCE_ID.test(grant.id) ||
      !validApprovalReference(grant.evidenceReference) ||
      !new RegExp("^rights/[0-9]{4}/" + grant.id + "\\\\.md$").test(grant.evidenceReference) ||
      !["movie","series","both"].includes(grant.mediaKind) ||
      !Number.isSafeInteger(grant.adapterVersion) ||
      grant.adapterVersion < 1 || grant.adapterVersion > 1_000_000 ||
      !Number.isSafeInteger(grant.reviewedAt) || grant.reviewedAt < 0 ||
      !Number.isSafeInteger(grant.validUntil) || grant.validUntil <= at ||
      grant.reviewedAt > at || grant.validUntil <= grant.reviewedAt ||
      grant.validUntil - grant.reviewedAt > MAX_REVIEW_AGE_MS ||
      !Array.isArray(grant.approvedHosts) ||
      grant.approvedHosts.length < 1 || grant.approvedHosts.length > 12 ||
      new Set(grant.approvedHosts).size !== grant.approvedHosts.length ||
      !grant.approvedHosts.every(host => typeof host === "string" &&
        host.length <= 253 && HOST.test(host) &&
        host.split(".").every(label => label.length <= 63) &&
        !/\\.(?:local|localhost|internal|invalid)$/.test(host) &&
        !/^\\d+(?:\\.\\d+){3}$/.test(host)) ||
      typeof grant.approvedPathPrefix !== "string" ||
      !PATH.test(grant.approvedPathPrefix) ||
      grant.approvedPathPrefix.split("/").some(x => x === "." || x === "..")) {
    throw new Error("invalid_reviewed_source_grant");
  }
  return grant;
}


export function assertReviewedPublication(records, snapshot,
    approvedEvidenceRefs = APPROVED_RIGHTS_REFS,
    approvedSourceGrants = APPROVED_SOURCE_GRANTS) {
  if (!Array.isArray(records) || !snapshot || !Array.isArray(snapshot.sources)) {
    throw new Error("unsafe_production_snapshot");
  }
  if (!Array.isArray(approvedEvidenceRefs) ||
      approvedEvidenceRefs.some(ref => !validApprovalReference(ref))) {
    throw new Error("invalid_production_rights_allowlist");
  }
  if (!Number.isSafeInteger(snapshot.generatedAt) || snapshot.generatedAt < 0 ||
      !Array.isArray(approvedSourceGrants)) {
    throw new Error("invalid_reviewed_source_grant");
  }
  const approved = new Set(approvedEvidenceRefs);
  const grants = new Map();
  for (const raw of approvedSourceGrants) {
    const grant = reviewedGrant(raw, snapshot.generatedAt);
    if (grants.has(grant.id)) throw new Error("duplicate_reviewed_source_grant");
    grants.set(grant.id, grant);
  }
  const byId = new Map();
  for (const row of records) {
    if (!row || typeof row.id !== "string" || !SOURCE_ID.test(row.id) ||
        byId.has(row.id)) throw new Error("unsafe_production_registry");
    byId.set(row.id, row.config);
  }
  const published = new Set();
  for (const item of snapshot.sources) {
    if (!item || typeof item.id !== "string" || published.has(item.id)) {
      throw new Error("unsafe_production_snapshot");
    }
    published.add(item.id);
    const config = byId.get(item.id);
    const grant = grants.get(item.id);
    const normalized = normalizedHttpsUrl(item.baseUrl);
    const url = normalized ? new URL(normalized) : null;
    if (!grant || !url || normalized !== item.baseUrl ||
        grant.evidenceReference !== config?.approvalRef ||
        grant.adapterVersion !== item.adapterVersion ||
        grant.mediaKind !== item.mediaKind ||
        !grant.approvedHosts.includes(url.hostname) ||
        !(grant.approvedPathPrefix === "/" ||
          url.pathname === grant.approvedPathPrefix ||
          url.pathname.startsWith(grant.approvedPathPrefix + "/")) ||
        !config || config.id !== item.id ||
        config.enabled !== true || config.integrationApproved !== true ||
        !validApprovalReference(config.approvalRef) ||
        !new RegExp("^rights/[0-9]{4}/" + item.id + "\\.md$").test(config.approvalRef) ||
        !approved.has(config.approvalRef) ||
        config.currentUrl !== item.baseUrl ||
        config.mediaKind !== item.mediaKind ||
        config.adapterVersion !== item.adapterVersion) {
      throw new Error("unreviewed_production_snapshot");
    }
  }
  return true;
}
