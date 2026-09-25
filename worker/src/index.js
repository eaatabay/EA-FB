/**
 * EA-FB public metadata relay. Only TMDb's approved catalog routes are exposed.
 * The TMDB_READ_ACCESS_TOKEN exists exclusively as a Cloudflare Worker secret.
 * Do not add third-party video scraping or arbitrary URL proxying here.
 */
const UPSTREAM = "https://api.themoviedb.org/3";
const LANGUAGES = new Set(["tr-TR", "en-US"]);
const MONETIZATION = new Set(["flatrate", "free", "ads", "rent", "buy"]);
const SORTS = new Set(["popularity.desc", "vote_average.desc", "primary_release_date.desc"]);
const APPENDS = {
  movie: new Set(["credits", "recommendations", "external_ids", "images", "similar", "videos", "watch/providers"]),
  tv: new Set(["aggregate_credits", "recommendations", "external_ids", "images", "similar", "videos", "watch/providers"]),
};

function json(object, status = 200, ttl = 0) {
  return new Response(JSON.stringify(object), {
    status,
    headers: {
      "content-type": "application/json; charset=utf-8",
      "cache-control": ttl ? "public, max-age=" + ttl : "no-store",
      "x-content-type-options": "nosniff",
      "access-control-allow-origin": "*",
    },
  });
}

function catalogRequest(url) {
  const p = url.pathname;
  let ttl = 3600;
  const parts = p.split("/").filter(Boolean);
  if (parts.shift() !== "v1") return null;
  if (parts.some((s) => !/^[a-zA-Z0-9_-]+$/.test(s))) return null;

  const q = url.searchParams;
  const accepted = new URLSearchParams();
  const language = q.get("language") || "tr-TR";
  if (!LANGUAGES.has(language)) return null;
  accepted.set("language", language);
  const page = q.get("page") || "1";
  if (!/^\d{1,2}$/.test(page) || +page < 1 || +page > 20) return null;
  accepted.set("page", String(+page));

  let upstreamPath;
  const kind = parts[0];
  if (kind === "trending" && parts.length === 3 &&
      ["all", "movie", "tv"].includes(parts[1]) && ["day", "week"].includes(parts[2])) {
    upstreamPath = "/" + parts.join("/");
    ttl = 1800;
  } else if (["movie", "tv"].includes(kind) && parts.length === 2 &&
      ["popular", "top_rated"].includes(parts[1]) ||
      kind === "movie" && parts.length === 2 &&
      ["now_playing", "upcoming"].includes(parts[1])) {
    upstreamPath = "/" + parts.join("/");
    ttl = 3600;
  } else if (kind === "discover" && parts.length === 2 &&
      ["movie", "tv"].includes(parts[1])) {
    upstreamPath = "/" + parts.join("/");
    for (const k of ["with_watch_providers", "watch_region", "with_watch_monetization_types", "with_genres", "sort_by"]) {
      const v = q.get(k);
      if (v == null) continue;
      const valid = k === "with_watch_providers" ? /^\d{1,6}(,\d{1,6}){0,4}$/.test(v) :
        k === "watch_region" ? /^[A-Z]{2}$/.test(v) :
        k === "with_watch_monetization_types" ? MONETIZATION.has(v) :
        k === "with_genres" ? /^\d{1,4}(,\d{1,4}){0,4}$/.test(v) :
        SORTS.has(v);
      if (!valid) return null;
      accepted.set(k, v);
    }
    ttl = 3600;
  } else if (kind === "search" && parts.length === 2 && parts[1] === "multi") {
    const query = q.get("query")?.trim();
    if (!query || query.length > 120 || /[\u0000-\u001f]/.test(query)) return null;
    accepted.set("query", query);
    upstreamPath = "/search/multi";
    ttl = 900;
  } else if (["movie", "tv"].includes(kind) && parts.length >= 2 &&
      /^\d{1,9}$/.test(parts[1]) && +parts[1] > 0) {
    if (parts.length === 2) {
      const append = q.get("append_to_response");
      if (append) {
        const values = append.split(",");
        if (values.length > 7 || !values.every((v) => APPENDS[kind].has(v))) return null;
        accepted.set("append_to_response", [...new Set(values)].sort().join(","));
      }
      upstreamPath = "/" + parts.join("/");
      ttl = 21600;
    } else if (parts.length === 3 &&
        ["recommendations", "similar", "credits", "external_ids", "watch_providers"].includes(parts[2])) {
      upstreamPath = "/" + kind + "/" + parts[1] + "/" +
        (parts[2] === "watch_providers" ? "watch/providers" : parts[2]);
      ttl = 3600;
    } else if (kind === "tv" && parts.length === 4 && parts[2] === "season" &&
        /^\d{1,3}$/.test(parts[3]) && +parts[3] <= 100) {
      upstreamPath = "/" + parts.join("/");
      ttl = 1800; // Airdates may change.
    }
  } else if (kind === "collection" && parts.length === 2 &&
      /^\d{1,9}$/.test(parts[1]) && +parts[1] > 0) {
    upstreamPath = "/" + parts.join("/");
    ttl = 21600;
  }

  if (!upstreamPath) return null;
  // Do not quietly allow arbitrary upstream query parameters.
  const permitted = new Set([...accepted.keys(), "append_to_response",
    "with_watch_providers", "watch_region", "with_watch_monetization_types",
    "with_genres", "sort_by", "query"]);
  for (const key of q.keys()) {
    if (!permitted.has(key) || !accepted.has(key) || q.getAll(key).length !== 1) return null;
  }
  accepted.sort();
  return { upstreamPath, params: accepted, ttl };
}

export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);
    if (request.method !== "GET") return json({ error: "method_not_allowed" }, 405);
    if (url.pathname === "/health") {
      return json({ status: env.TMDB_READ_ACCESS_TOKEN ? "ready" : "unconfigured", service: "EA-FB catalog", version: 1 },
        env.TMDB_READ_ACCESS_TOKEN ? 200 : 503);
    }
    const catalog = catalogRequest(url);
    if (!catalog) return json({ error: "unsupported_catalog_request" }, 400);
    if (!env.TMDB_READ_ACCESS_TOKEN) return json({ error: "catalog_unconfigured" }, 503);

    // Public relay: bounds cost and upstream key abuse, but does NOT authenticate users.
    if (env.PER_CLIENT_LIMIT) {
      const ip = request.headers.get("cf-connecting-ip") || "unknown";
      if (!(await env.PER_CLIENT_LIMIT.limit({ key: ip })).success) {
        return json({ error: "too_many_requests" }, 429);
      }
    }
    if (env.GLOBAL_LIMIT && !(await env.GLOBAL_LIMIT.limit({ key: "all" })).success) {
      return json({ error: "catalog_busy" }, 429);
    }

    const cacheUrl = new URL(url.origin + "/v1" + catalog.upstreamPath + "?" + catalog.params);
    const cacheKey = new Request(cacheUrl.toString(), { method: "GET" });
    const cache = typeof caches === "undefined" ? null : caches.default;
    if (cache) {
      const cached = await cache.match(cacheKey);
      if (cached) return cached;
    }
    const upstreamUrl = UPSTREAM + catalog.upstreamPath + "?" + catalog.params;
    let upstream;
    try {
      upstream = await fetch(upstreamUrl, {
        method: "GET",
        headers: {
          authorization: "Bearer " + env.TMDB_READ_ACCESS_TOKEN,
          accept: "application/json",
        },
        redirect: "error",
        signal: AbortSignal.timeout(9000),
      });
    } catch (_) {
      return json({ error: "catalog_temporarily_unavailable" }, 502);
    }
    if (!upstream.ok) {
      // Never forward headers or upstream body: no tokens or account diagnostics.
      return json({ error: upstream.status === 429 ? "catalog_rate_limited" : "catalog_upstream_error" },
        upstream.status === 429 ? 429 : 502);
    }
    const contentType = upstream.headers.get("content-type") || "";
    if (!contentType.includes("application/json")) return json({ error: "invalid_catalog_response" }, 502);
    const body = await upstream.text();
    if (body.length > 2_000_000) return json({ error: "catalog_response_too_large" }, 502);
    try { JSON.parse(body); } catch (_) { return json({ error: "invalid_catalog_response" }, 502); }
    const response = new Response(body, {
      status: 200,
      headers: {
        "content-type": "application/json; charset=utf-8",
        "cache-control": "public, max-age=" + catalog.ttl,
        "x-content-type-options": "nosniff",
        "access-control-allow-origin": "*",
      },
    });
    if (cache && ctx?.waitUntil) ctx.waitUntil(cache.put(cacheKey, response.clone()));
    return response;
  },
};
