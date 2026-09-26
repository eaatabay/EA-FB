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

/**
 * Copy only OWN primitive data properties into a fresh object. Never hand a
 * third-party adapter's mutable object, prototype or getter to the D1 policy
 * after validation (otherwise async commit opens a validation/use race).
 * A null result is structural schema drift and must be held for admin.
 */
export function sanitizeAdapterProbe(raw, config) {
  try {
  if (!plainObject(raw) || Reflect.ownKeys(raw).some(key => typeof key !== "string")) {
    return null;
  }
  const fields = Object.getOwnPropertyDescriptors(raw);
  if (Object.keys(fields).some(key => !PROBE_KEYS.has(key) ||
      !Object.hasOwn(fields[key], "value")) ||
      !Object.hasOwn(fields,"checks")) return null;
  const rawChecks = fields.checks.value;
  if (!plainObject(rawChecks) ||
      Reflect.ownKeys(rawChecks).some(key => typeof key !== "string")) return null;
  const checkFields = Object.getOwnPropertyDescriptors(rawChecks);
  if (Object.keys(checkFields).some(key => !CHECK_KEYS.has(key) ||
      !Object.hasOwn(checkFields[key], "value"))) return null;
  const probe = {
    reached:fields.reached?.value,
    finalUrl:fields.finalUrl?.value,
    identityVerified:fields.identityVerified?.value,
    checks:Object.fromEntries(Object.entries(checkFields)
      .map(([key,descriptor])=>[key,descriptor.value])),
  };
  if (Object.hasOwn(fields,"structuralChange")) {
    probe.structuralChange=fields.structuralChange.value;
  }
  return validAdapterProbe(probe,config) ? probe : null;
  } catch {
    // Proxy traps and hostile property descriptors are schema anomalies,
    // not evidence that the publisher itself is unreachable.
    return null;
  }
}
