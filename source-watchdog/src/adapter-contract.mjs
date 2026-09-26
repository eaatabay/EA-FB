/**
 * Pure schema boundary between an approved, bundled probe adapter and D1.
 * Incomplete parser output means SCHEMA DRIFT (admin hold), not a publisher
 * outage. No URLs or untrusted error messages are logged by this module.
 */
const PROBE_KEYS = new Set(["reached","finalUrl","identityVerified","structuralChange","checks"]);
const CHECK_KEYS = new Set(["search","detail","episode","playback","reachability"]);

function plainObject(value) {
  if (!value || typeof value !== "object" || Array.isArray(value)) return false;
  const proto = Object.getPrototypeOf(value);
  return proto === Object.prototype || proto === null;
}

export function validAdapterProbe(probe, config) {
  if (!plainObject(probe) || !plainObject(probe.checks) ||
      Object.keys(probe).some(key => !PROBE_KEYS.has(key)) ||
      Object.keys(probe.checks).some(key => !CHECK_KEYS.has(key)) ||
      "runnerFailure" in probe ||
      typeof probe.reached !== "boolean" ||
      typeof probe.finalUrl !== "string" ||
      typeof probe.identityVerified !== "boolean" ||
      (probe.structuralChange !== undefined &&
       typeof probe.structuralChange !== "boolean") ||
      !Array.isArray(config?.requiredChecks)) return false;
  return config.requiredChecks.every(check =>
    check === "reachability" || typeof probe.checks[check] === "boolean");
}
