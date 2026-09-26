/**
 * Pre-flight destination validation for individually reviewed source adapters.
 * This is NOT an HTTP transport and performs NO outbound requests. Callers
 * MUST separately pin/validate the actual remote IP (including redirects)
 * before enabling live probes; DNS pre-flight alone cannot stop rebinding.
 */
const HOST = /^(?=.{4,253}$)[a-z0-9](?:[a-z0-9-]*[a-z0-9])?(?:\.[a-z0-9](?:[a-z0-9-]*[a-z0-9])?)+$/;
const MAX_HOPS = 3;

function normalizedTarget(value) {
  if (typeof value !== 'string' || value.length > 2048) return null;
  try {
    const url = new URL(value);
    // Match the literal authority: URL() otherwise normalizes uppercase,
    // percent-encoded hostnames and explicit default :443 ports.
    const authority = value.startsWith('https://')
      ? value.slice('https://'.length).split(/[/?#]/, 1)[0] : '';
    if (authority !== url.hostname || url.protocol !== 'https:' || url.username || url.password ||
        // URL.search/hash are empty for bare '?' and '#'; reject those too.
        value.includes('?') || value.includes('#') ||
        url.port || url.search || url.hash || url.hostname.includes(':') ||
        (!HOST.test(url.hostname) ||
         url.hostname.split('.').some(label => label.length > 63)) ||
        url.hostname.endsWith('.local') ||
        url.hostname.endsWith('.internal') ||
        url.hostname.endsWith('.localhost') ||
        url.hostname.endsWith('.invalid') ||
        /^\d+(?:\.\d+){3}$/.test(url.hostname)) return null;
    return url;
  } catch { return null; }
}

/** Conservative list: only globally routable IPv4; no IPv6 support yet. */
export function publicIPv4(ip) {
  if (typeof ip !== 'string' || !/^(?:\d{1,3}\.){3}\d{1,3}$/.test(ip)) return false;
  const parts = ip.split('.');
  if (parts.some(s => (s.length > 1 && s[0] === '0') || Number(s) > 255)) return false;
  const [a,b,c] = parts.map(Number);
  if (a === 0 || a === 10 || a === 127 || a >= 224 ||
      (a === 100 && b >= 64 && b <= 127) ||
      (a === 169 && b === 254) ||
      (a === 172 && b >= 16 && b <= 31) ||
      (a === 192 && b === 168) ||
      (a === 192 && b === 0 && c === 0) ||
      (a === 192 && b === 0 && c === 2) ||
      // Deprecated 6to4 relay anycast: not an approved direct origin target.
      (a === 192 && b === 88 && c === 99) ||
      (a === 198 && (b === 18 || b === 19)) ||
      (a === 198 && b === 51 && c === 100) ||
      (a === 203 && b === 0 && c === 113)) return false;
  return true;
}

/**
 * resolveAddresses(host) must come from trusted infrastructure, never a site
 * response. ALL A/AAAA answers must be vetted; even one private answer fails.
 * We reject IPv6 entirely pending an explicitly pinned IPv6 transport.
 */
export async function preflightTarget(source, targetUrl, resolveAddresses) {
  const url = normalizedTarget(targetUrl);
  if (!url) return {allowed:false, reason:'invalid_https_target'};
  const hosts = source?.verifiedDomains;
  if (!Array.isArray(hosts) || hosts.length < 1 || hosts.length > 12 ||
      !hosts.every(x => typeof x === 'string' && x === x.toLowerCase() &&
        HOST.test(x) && x.split('.').every(label => label.length <= 63) &&
        !/^(?:\d+\.){3}\d+$/.test(x))) {
    return {allowed:false, reason:'invalid_host_allowlist'};
  }
  if (!hosts.includes(url.hostname)) {
    return {allowed:false, reason:'admin_domain_approval_required'};
  }
  if (typeof resolveAddresses !== 'function') {
    return {allowed:false, reason:'resolver_required'};
  }
  try {
    const addresses = await resolveAddresses(url.hostname);
    if (!Array.isArray(addresses) || !addresses.length || addresses.length > 16 ||
        !addresses.every(publicIPv4)) {
      return {allowed:false, reason:'untrusted_dns_answer'};
    }
    return {allowed:true, hostname:url.hostname, publicAddresses:[...new Set(addresses)]};
  } catch {
    return {allowed:false, reason:'dns_failure'};
  }
}

/**
 * Reject unknown-domain redirects before any next-hop request, even if HTTP
 * has been 200 elsewhere. Re-check the destination's DNS on EVERY hop.
 * A future real transport must fetch with redirect:'manual' and pin the
 * target connection to the vetted IP; this function never calls fetch.
 */
export async function inspectRedirectPath(source, urls, resolveAddresses) {
  if (!Array.isArray(urls) || urls.length < 1 || urls.length > MAX_HOPS + 1) {
    return {allowed:false, reason:'redirect_limit'};
  }
  const seen = new Set();
  for (const [index, raw] of urls.entries()) {
    const url = normalizedTarget(raw);
    if (!url) return {allowed:false, reason:'invalid_https_target', hop:index};
    const id = url.toString();
    if (seen.has(id)) return {allowed:false, reason:'redirect_loop', hop:index};
    seen.add(id);
    const check = await preflightTarget(source, raw, resolveAddresses);
    if (!check.allowed) return {...check, hop:index};
  }
  return {allowed:true, hops:urls.length - 1};
}
