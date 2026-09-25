/**
 * EA-FB Source Watchdog: side-effect-free policy engine.
 *
 * Does not scrape, fetch, deploy, mutate GitHub or trust an unknown redirect.
 * A separate authorized probe runner and durable store will be wired later.
 * Only administrator-approved HTTPS hosts may ever be auto-promoted.
 */
export const HEALTH = Object.freeze({
  HEALTHY: "healthy",
  DEGRADED: "degraded",
  QUARANTINED: "quarantined",
  ADMIN_REQUIRED: "admin_required",
  DISABLED: "disabled",
});

const HOUR = 60 * 60 * 1000;
const BASE_CHECK = 6 * HOUR;
const DEGRADED_CHECK = HOUR;
const QUARANTINE_CHECK = 12 * HOUR;
const RECOVERY_SUCCESSES = 2;
const QUARANTINE_FAILURES = 2;

export function normalizedHttpsUrl(value) {
  if (typeof value !== "string" || value.length > 2048) return null;
  try {
    const url = new URL(value);
    if (url.protocol !== "https:" || url.username || url.password ||
        url.port || url.search || url.hash ||
        !url.hostname.includes(".") ||
        url.hostname === "localhost" ||
        url.hostname.endsWith(".localhost") ||
        url.hostname.endsWith(".local") ||
        url.hostname.endsWith(".internal") ||
        url.hostname.endsWith(".invalid") ||
        /^\d+(\.\d+){3}$/.test(url.hostname) ||
        url.hostname.includes(":")) return null;
    // Keep the approved base URL's path; never trust arbitrary probe-returned paths.
    return url.origin + (url.pathname === "/" ? "" : url.pathname.replace(/\/+$/, ""));
  } catch { return null; }
}

export function validateSource(source) {
  if (!source || typeof source !== "object" ||
      !/^[a-z][a-z0-9-]{2,63}$/.test(source.id) ||
      typeof source.enabled !== "boolean") throw new Error("invalid_source");
  const currentUrl = normalizedHttpsUrl(source.currentUrl);
  const lastKnownGoodUrl = normalizedHttpsUrl(source.lastKnownGoodUrl);
  const verifiedDomains = source.verifiedDomains;
  if (!currentUrl || !lastKnownGoodUrl || !Array.isArray(verifiedDomains) ||
      verifiedDomains.length < 1 || verifiedDomains.length > 12 ||
      !verifiedDomains.every(host => {
        const normalized = normalizedHttpsUrl("https://" + host);
        return normalized && new URL(normalized).hostname === host.toLowerCase();
      })) throw new Error("invalid_source_addresses");
  if (!verifiedDomains.includes(new URL(currentUrl).hostname) ||
      !verifiedDomains.includes(new URL(lastKnownGoodUrl).hostname)) {
    throw new Error("source_host_not_approved");
  }
  const checks = source.requiredChecks;
  if (!Array.isArray(checks) || !checks.includes("search") || !checks.includes("detail") ||
      checks.some(x => !["reachability", "search", "detail", "episode", "playback"].includes(x)) ||
      new Set(checks).size !== checks.length) throw new Error("invalid_required_checks");
  if (["series", "both"].includes(source.mediaKind) && !checks.includes("episode")) {
    throw new Error("series_episode_check_required");
  }
  if (!["movie", "series", "both"].includes(source.mediaKind)) {
    throw new Error("invalid_media_kind");
  }
  return { ...source, currentUrl, lastKnownGoodUrl, verifiedDomains: [...verifiedDomains] };
}

export function initialState(source, now) {
  validateSource(source);
  if (!Number.isFinite(now) || now < 0) throw new Error("invalid_clock");
  return {
    id: source.id,
    status: source.enabled ? HEALTH.DEGRADED : HEALTH.DISABLED,
    consecutiveFailures: 0,
    consecutiveSuccesses: 0,
    currentUrl: source.currentUrl,
    lastKnownGoodUrl: source.lastKnownGoodUrl,
    candidateUrl: null,
    lastFailure: null,
    lastCheckedAt: null,
    nextCheckAt: source.enabled ? now : null,
    revision: 0,
  };
}

/**
 * Probe result comes from a trusted test runner, not from the website itself:
 * { reached, finalUrl, checks: { search, detail, episode?, playback? },
 *   identityVerified, structuralChange? }.
 * A 200 response without content checks is never success.
 */
export function evaluateProbe(source, probe) {
  const config = validateSource(source);
  if (!probe || typeof probe !== "object") throw new Error("invalid_probe");
  const finalUrl = normalizedHttpsUrl(probe.finalUrl);
  const hostname = finalUrl ? new URL(finalUrl).hostname : null;
  const approvedHost = hostname !== null && config.verifiedDomains.includes(hostname);
  const testsPass = probe.reached === true && probe.identityVerified === true &&
    config.requiredChecks.every(check =>
      check === "reachability" ? probe.reached === true : probe.checks?.[check] === true
    );
  const redirects = finalUrl !== config.currentUrl;
  if (probe.structuralChange === true) {
    return { ok: false, reason: "structural_change", needsAdmin: true, proposedUrl: finalUrl };
  }
  if (redirects && !approvedHost && finalUrl !== null) {
    return { ok: false, reason: "unapproved_redirect", needsAdmin: true, proposedUrl: finalUrl };
  }
  if (!testsPass) {
    return {
      ok: false, reason: !probe.reached ? "unreachable" :
        !approvedHost ? "unapproved_or_invalid_target" :
        !probe.identityVerified ? "identity_unverified" : "functional_check_failed",
      needsAdmin: false, proposedUrl: null,
    };
  }
  if (!approvedHost) {
    return { ok: false, reason: "unapproved_or_invalid_target", needsAdmin: true, proposedUrl: finalUrl };
  }
  return { ok: true, reason: "verified", needsAdmin: false,
    proposedUrl: redirects ? finalUrl : null };
}

/**
 * State changes require two healthy checks for recovery and for promoting a
 * previously unseen verified candidate URL. Failed proposed changes never
 * replace lastKnownGoodUrl. A maintenance operator may separately approve a
 * new hostname after ownership verification, then run two fresh probes.
 */
export function applyProbe(source, previous, probe, now) {
  const config = validateSource(source);
  if (previous?.id !== config.id || !Number.isFinite(now) ||
      (previous.lastCheckedAt !== null && now < previous.lastCheckedAt)) {
    throw new Error("invalid_watchdog_state");
  }
  if (!config.enabled) {
    return { ...previous, status: HEALTH.DISABLED, nextCheckAt: null };
  }
  if (previous.status === HEALTH.ADMIN_REQUIRED) {
    // Requires an explicit operator release. Scheduler may not silently override it.
    return { ...previous, lastCheckedAt: now };
  }
  const result = evaluateProbe({ ...config, currentUrl: previous.currentUrl }, probe);
  const base = { ...previous, lastCheckedAt: now, revision: previous.revision + 1 };
  if (result.needsAdmin) {
    return { ...base, status: HEALTH.ADMIN_REQUIRED, candidateUrl: result.proposedUrl,
      consecutiveSuccesses: 0, lastFailure: result.reason, nextCheckAt: null };
  }
  if (!result.ok) {
    const failures = previous.consecutiveFailures + 1;
    const quarantine = failures >= QUARANTINE_FAILURES;
    return { ...base, status: quarantine ? HEALTH.QUARANTINED : HEALTH.DEGRADED,
      consecutiveFailures: failures, consecutiveSuccesses: 0, candidateUrl: null,
      lastFailure: result.reason, nextCheckAt: now +
        (quarantine ? Math.min(24 * HOUR, QUARANTINE_CHECK * 2 ** Math.min(failures - 2, 1))
                    : DEGRADED_CHECK) };
  }
  const candidate = result.proposedUrl ?? null;
  // Two consistent checks for a redirect. Switching candidates resets evidence.
  const sameCandidate = candidate === (previous.candidateUrl ?? null);
  const successes = sameCandidate ? previous.consecutiveSuccesses + 1 : 1;
  const recovered = successes >= RECOVERY_SUCCESSES;
  const currentUrl = recovered && candidate ? candidate : previous.currentUrl;
  return { ...base, status: recovered ? HEALTH.HEALTHY : HEALTH.DEGRADED,
    currentUrl, lastKnownGoodUrl: recovered ? currentUrl : previous.lastKnownGoodUrl,
    candidateUrl: recovered ? null : candidate, consecutiveSuccesses: recovered ? RECOVERY_SUCCESSES : successes,
    consecutiveFailures: 0, lastFailure: null,
    nextCheckAt: now + (recovered ? BASE_CHECK : DEGRADED_CHECK) };
}

/** Manual release must follow trusted configuration changes and audit logging. */
export function releaseForRetest(source, previous, now) {
  validateSource(source);
  if (previous?.status !== HEALTH.ADMIN_REQUIRED || previous?.id !== source.id ||
      !Number.isFinite(now)) throw new Error("invalid_admin_release");
  return { ...previous, status: HEALTH.DEGRADED, candidateUrl: null,
    consecutiveFailures: 0, consecutiveSuccesses: 0, lastFailure: null,
    nextCheckAt: now, revision: previous.revision + 1 };
}

/** Only healthy sources enter new searches; already playing links are not killed. */
export function searchableSourceIds(states) {
  return states.filter(x => x.status === HEALTH.HEALTHY).map(x => x.id);
}
