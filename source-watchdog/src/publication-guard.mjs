/** Pure, offline final authorization boundary for signed device publication. */
const SOURCE_ID = /^[a-z][a-z0-9-]{2,63}$/;

export function validApprovalReference(value) {
  return typeof value === "string" && value.length >= 8 && value.length <= 160 &&
    /^[A-Za-z0-9_-]+(?:\/[A-Za-z0-9_.-]+)*$/.test(value) &&
    value.split("/").every(part => part !== "." && part !== "..");
}

/** Empty in the tracked v6 build. Explicit human review is needed before any live rights approval. */
export const APPROVED_RIGHTS_REFS = Object.freeze([]);

export function assertReviewedPublication(records, snapshot,
    approvedEvidenceRefs = APPROVED_RIGHTS_REFS) {
  if (!Array.isArray(records) || !snapshot || !Array.isArray(snapshot.sources)) {
    throw new Error("unsafe_production_snapshot");
  }
  if (!Array.isArray(approvedEvidenceRefs) ||
      approvedEvidenceRefs.some(ref => !validApprovalReference(ref))) {
    throw new Error("invalid_production_rights_allowlist");
  }
  const approved = new Set(approvedEvidenceRefs);
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
    if (!config || config.id !== item.id ||
        config.enabled !== true || config.integrationApproved !== true ||
        !validApprovalReference(config.approvalRef) ||
        !approved.has(config.approvalRef) ||
        config.currentUrl !== item.baseUrl ||
        config.mediaKind !== item.mediaKind ||
        config.adapterVersion !== item.adapterVersion) {
      throw new Error("unreviewed_production_snapshot");
    }
  }
  return true;
}
