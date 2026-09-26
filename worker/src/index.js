import { readBoundedText } from "./bounded-response.mjs";
import { withUpstreamDeadline } from "./upstream-deadline.mjs";
import { parseOmdbRating, enrichmentCacheTtl } from "./ratings-enrichment.mjs";
/**
 * EA-FB public metadata relay. Only TMDb's approved catalog routes are exposed.
 * The TMDB_READ_ACCESS_TOKEN exists exclusively as a Cloudflare Worker secret.
 * Do not add third-party video scraping or arbitrary URL proxying here.
 */
const UPSTREAM = "https://api.themoviedb.org/3";
const LANGUAGES = new Set(["tr-TR", "en-US"]);
const MONETIZATION = new Set(["flatrate", "free", "ads", "rent", "buy"]);
const SORTS = new Set(["popularity.desc", "vote_average.desc", "primary_release_date.desc", "first_air_date.desc"]);
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
  // Reject duplicate/trailing slashes and empty ?/# aliases. The stock
  // Android client always uses canonical paths; aliases waste cache budget.
  if (p !== "/" + parts.join("/") ||
      url.href !== url.origin + p + url.search) return null;
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
    for (const k of ["with_watch_providers", "watch_region", "with_watch_monetization_types", "with_genres", "sort_by", "vote_count.gte"]) {
      const v = q.get(k);
      if (v == null) continue;
      const valid = k === "with_watch_providers" ? /^\d{1,6}(,\d{1,6}){0,4}$/.test(v) :
        k === "watch_region" ? /^[A-Z]{2}$/.test(v) :
        k === "with_watch_monetization_types" ? MONETIZATION.has(v) :
        k === "with_genres" ? /^\d{1,4}(,\d{1,4}){0,4}$/.test(v) :
        k === "vote_count.gte" ? /^\d{1,5}$/.test(v) && +v >= 1 && +v <= 10000 :
        SORTS.has(v) &&
          (v !== "first_air_date.desc" || parts[1] === "tv") &&
          (v !== "primary_release_date.desc" || parts[1] === "movie");
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
    "with_genres", "sort_by", "vote_count.gte", "query"]);
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
    if (url.pathname === "/health" && url.href === url.origin + "/health") {
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
    // Partition metadata caches by enrichment mode. When the private OMDb key
    // is enabled after v5, a warm TMDb-only cache must not hide IMDb ratings.
    // Only the mode, never the private credential, enters the cache key.
    cacheUrl.searchParams.set("_ea_fb_rating", env.OMDB_API_KEY ? "omdb-v1" : "tmdb-v1");
    const cacheKey = new Request(cacheUrl.toString(), { method: "GET" });
    const cache = typeof caches === "undefined" ? null : caches.default;
    if (cache) {
      const cached = await cache.match(cacheKey);
      if (cached) return cached;
    }
    const upstreamUrl = UPSTREAM + catalog.upstreamPath + "?" + catalog.params;
    // Some dashboards copy an optional Bearer prefix. Never send Bearer Bearer.
    const token = String(env.TMDB_READ_ACCESS_TOKEN).trim().replace(/^Bearer\s+/i, "").trim();
    if (!token) return json({ error: "catalog_unconfigured" }, 503);
    let upstream, body;
    try {
      ({upstream,body} = await withUpstreamDeadline(async signal => {
        const upstream = await fetch(upstreamUrl, {
          method: "GET",
          headers: {
            authorization: "Bearer " + token,
            accept: "application/json",
          },
          // No redirect may receive the private Bearer token. Abort also
          // covers a publisher that sends headers then stalls its JSON body.
          redirect: "manual", signal,
        });
        const type = upstream.headers.get("content-type") || "";
        const body = upstream.ok && type.includes("application/json")
          ? await readBoundedText(upstream,2_000_000) : null;
        return {upstream,body};
      },12000));
    } catch (err) {
      if (err?.message === "upstream_too_large") {
        return json({error:"catalog_response_too_large"},502);
      }
      // Only sanitized reason codes leave the Worker; never URLs, tokens,
      // account details, exception messages or upstream response bodies.
      const name = String(err?.name || "");
      const message = String(err?.message || "");
      const reason = name === "TimeoutError" || name === "AbortError" ? "timeout" :
        /abortsignal|unsupported.*signal|invalid.*signal/i.test(message)
        ? "runtime_signal" : /redirect/i.test(message) ? "redirect_error"
        : /dns|resolve/i.test(message) ? "dns_failure"
        : /tls|ssl|certificate/i.test(message) ? "tls_failure"
        : /fetch|network|connect/i.test(message) ? "outbound_network"
        : name === "TypeError" ? "runtime_type_error" : "other";
      console.warn("TMDB_FETCH_FAILURE",reason,name.replace(/[^a-zA-Z]/g,"").slice(0,32));
      return json({error:"tmdb_connection_error",reason},502);
    }
    if (upstream.status >= 300 && upstream.status < 400) {
      // Do not forward Authorization to redirects.
      return json({ error: "tmdb_redirect_rejected" }, 502);
    }
    if (!upstream.ok) {
      // Public, sanitized status only; upstream responses may include account details.
      const code = upstream.status === 401 ? "tmdb_unauthorized" :
        upstream.status === 403 ? "tmdb_forbidden" :
        upstream.status === 429 ? "tmdb_rate_limited" :
        "tmdb_upstream_error";
      return json({ error: code }, upstream.status === 429 ? 429 : 502);
    }
    const contentType = upstream.headers.get("content-type") || "";
    if (!contentType.includes("application/json")) return json({ error: "invalid_catalog_response" }, 502);
    let result;
    try { result = JSON.parse(body); } catch (_) {
      return json({ error: "invalid_catalog_response" }, 502);
    }
    // TMDb endpoints return objects. Never cache an array, null, or primitive
    // as a successful catalog response (they break the Android JSON client).
    if (!result || typeof result !== "object" || Array.isArray(result)) {
      return json({ error: "invalid_catalog_response" }, 502);
    }

    // Optional second rating source, queried ONLY on a single title detail.
    // This server-side key never leaves Cloudflare; clients see ratings only.
    // TMDb's external_ids.imdb_id identifies a title but has NO IMDb score.
    const isDetail = /^\/(movie|tv)\/\d{1,9}$/.test(catalog.upstreamPath);
    const requestedId = isDetail ? Number(catalog.upstreamPath.split("/")[2]) : null;
    if (isDetail && result.id !== undefined && result.id !== requestedId) {
      return json({ error: "invalid_catalog_response" }, 502);
    }
    // Only THIS Worker may attach the reserved IMDb rating field. Do not
    // trust a field with the same name in an upstream metadata response.
    const hadUntrustedRating = Object.hasOwn(result, "ea_fb_ratings");
    if (hadUntrustedRating) delete result.ea_fb_ratings;
    const imdbId = result?.external_ids?.imdb_id;
    let outputBody = hadUntrustedRating ? JSON.stringify(result) : body;
    const omdbAttempted = isDetail && Boolean(env.OMDB_API_KEY) &&
      typeof imdbId === "string" && /^tt\d{7,10}$/.test(imdbId);
    let omdbSettled = false;
    if (omdbAttempted) {
      // The optional second rating service must never hold the TMDb detail
      // hostage. The timeout covers both HTTP and streamed JSON body reads.
      const controller = new AbortController();
      let timer;
      try {
        const enrich = async () => {
          const omdbUrl = "https://www.omdbapi.com/?i=" +
            encodeURIComponent(imdbId) + "&apikey=" + encodeURIComponent(env.OMDB_API_KEY);
          const other = await fetch(omdbUrl, {
            method: "GET", redirect: "manual", signal:controller.signal,
            headers: { accept: "application/json" },
          });
          if (other.ok && (other.headers.get("content-type") || "").includes("application/json")) {
            const text = await readBoundedText(other, 50_000);
            if (text.length < 50_000) {
              const parsed = parseOmdbRating(JSON.parse(text), imdbId);
              omdbSettled = parsed.settled;
              if (parsed.rating !== null) {
                result.ea_fb_ratings = { imdb: parsed.rating, source: "OMDb API" };
                outputBody = JSON.stringify(result);
              }
            }
          }
        };
        await Promise.race([
          enrich(),
          new Promise((_,reject)=>{
            timer=setTimeout(()=>{
              // Reject first: a synchronous abort listener cannot turn an
              // expired request into a late successful enrichment.
              reject(new Error("optional_rating_timeout"));
              controller.abort();
            },3500);
          }),
        ]);
      } catch (_) {
        // Best-effort only. TMDb stays available; retry after short cache TTL.
      } finally {
        if (timer) clearTimeout(timer);
      }
    }
    // Do not poison the OMDb-mode cache for hours with a TMDb-only result
    // caused by a temporary enrichment failure. Preserve TMDb availability,
    // retry enrichment after one minute, and retain normal TTL for real N/A.
    const responseTtl = enrichmentCacheTtl(omdbAttempted,omdbSettled,catalog.ttl);
    const response = new Response(outputBody, {
      status: 200,
      headers: {
        "content-type": "application/json; charset=utf-8",
        "cache-control": "public, max-age=" + responseTtl,
        "x-content-type-options": "nosniff",
        "access-control-allow-origin": "*",
      },
    });
    if (cache && ctx?.waitUntil) ctx.waitUntil(cache.put(cacheKey, response.clone()));
    return response;
  },
};
