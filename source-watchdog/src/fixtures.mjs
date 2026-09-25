/**
 * Deterministic OFFLINE fixture adapters. They never fetch any URL.
 * The entire fixture registry must contain only *.example.org hosts and
 * fixture-* IDs. Do not mix fixture/test entries with real source records.
 */
const FIXTURE_ID = /^fixture-[a-z0-9-]{3,56}$/;

function exampleHost(value) {
  try {
    const u = new URL(value);
    return u.protocol === "https:" && u.username === "" && u.password === "" &&
      u.port === "" && u.search === "" && u.hash === "" &&
      u.hostname.endsWith(".example.org") && u.hostname !== "example.org";
  } catch { return false; }
}

export function assertIsolatedFixtureRegistry(records) {
  if (!Array.isArray(records)) throw new Error("invalid_fixture_registry");
  const seen = new Set();
  for (const item of records) {
    const config = item?.config;
    const state = item?.state;
    if (!config || !state || state.id !== config.id ||
        !["healthy","degraded","quarantined","admin_required","disabled"].includes(state.status) ||
        !exampleHost(state.currentUrl) || !exampleHost(state.lastKnownGoodUrl) ||
        (state.candidateUrl != null && !exampleHost(state.candidateUrl)) ||
        typeof config.id !== "string" ||
        item.id !== config.id || !FIXTURE_ID.test(config.id) || seen.has(config.id) ||
        !exampleHost(config.currentUrl) ||
        !exampleHost(config.lastKnownGoodUrl) ||
        !Array.isArray(config.verifiedDomains) ||
        !config.verifiedDomains.length ||
        !config.verifiedDomains.every(host =>
          typeof host === "string" && exampleHost("https://" + host) &&
          new URL("https://" + host).hostname === host)) {
      throw new Error("unsafe_fixture_registry");
    }
    const currentHost = new URL(config.currentUrl).hostname;
    const stateHosts = [config.lastKnownGoodUrl, state.currentUrl,
      state.lastKnownGoodUrl, state.candidateUrl].filter(Boolean)
      .map(value => new URL(value).hostname);
    if (!config.verifiedDomains.includes(currentHost) ||
        !stateHosts.every(host => config.verifiedDomains.includes(host))) {
      throw new Error("unsafe_fixture_registry");
    }
    seen.add(config.id);
  }
  return true;
}

export function createFixtureAdapters(records) {
  assertIsolatedFixtureRegistry(records);
  return new Map(records.map(item => {
    const id = item.id;
    return [id, {
      id,
      async probe({source}) {
        // No network. Only the explicit scenario IDs below change behavior.
        if (source.id !== id || !FIXTURE_ID.test(id)) {
          throw new Error("fixture_identity_mismatch");
        }
        const structuralChange = id === "fixture-structural";
        const unreachable = id === "fixture-down";
        const moved = id === "fixture-move-a" || id === "fixture-move-b";
        const target = moved ? "https://moved.example.org" : source.currentUrl;
        return {
          reached: !unreachable,
          finalUrl: target,
          identityVerified: !unreachable,
          structuralChange,
          checks: {
            search: !unreachable && !structuralChange,
            detail: !unreachable && !structuralChange,
            episode: !unreachable && !structuralChange,
            playback: !unreachable && !structuralChange,
          },
        };
      },
    }];
  }));
}
