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
// Exact calendar date, not Date.parse's permissive rollover (e.g. February 31).
function validIsoDate(value) {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value) || Number(value.slice(0,4)) < 1888 ||
      Number(value.slice(0,4)) > 2100) return false;
  const timestamp = Date.parse(value + "T00:00:00.000Z");
  return Number.isFinite(timestamp) &&
    new Date(timestamp).toISOString().slice(0, 10) === value;
}

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

const TITLE_TRANSLATION_VERSION = "v42-localize-1";
const TITLE_AI_MODEL = "@cf/meta/llama-3.1-8b-instruct";
const titleBatchInFlight = new Set();

function localizedEpisodeTitle(payload, language, preferredRegion = "") {
  const rows = Array.isArray(payload?.translations) ? payload.translations : [];
  let fallback = null;
  for (const row of rows) {
    if (String(row?.iso_639_1 || "").toLowerCase() !== language) continue;
    const name = String(row?.data?.name || "").trim();
    if (!name) continue;
    if (preferredRegion &&
        String(row?.iso_3166_1 || "").toUpperCase() === preferredRegion) return name;
    if (fallback === null) fallback = name;
  }
  return fallback;
}
function turkishEpisodeTitle(payload) {
  return localizedEpisodeTitle(payload, "tr", "TR");
}
function sourceEpisodeTitle(payload, language) {
  const preferred = {
    en:"US", es:"ES", fr:"FR", de:"DE", it:"IT", pt:"BR", tr:"TR",
    ko:"KR", ja:"JP", zh:"CN", ru:"RU", ar:"SA"
  }[language] || "";
  return localizedEpisodeTitle(payload, language, preferred);
}
function normalizedTitle(value) {
  return String(value || "").normalize("NFKC").trim().replace(/\s+/g, " ")
    .toLocaleLowerCase("tr-TR");
}
function sameTitle(a, b) {
  return Boolean(a && b) && normalizedTitle(a) === normalizedTitle(b);
}
function genericEpisodeTitle(value, episode) {
  const normalized = normalizedTitle(value).replace(/[.:]+$/g, "");
  if (!normalized) return true;
  const n = String(episode);
  const words = [
    "episode","bölüm","episodio","episódio","épisode","folge","capítulo",
    "capitulo","odcinek","эпизод","серия","エピソード","에피소드"
  ];
  if (words.some(word => normalized === word + " " + n ||
      normalized === word + " #" + n || normalized === n + " " + word)) return true;
  return normalized === "第" + n + "集";
}
function usableLocalizedTitle(value, episode) {
  const title = String(value || "").trim().replace(/\s+/g, " ");
  if (!title || title.length > 160 || /[\u0000-\u001f]/.test(title) ||
      genericEpisodeTitle(title, episode)) return null;
  return title;
}
function polishTurkishTitle(value) {
  const cleaned = String(value || "").trim().replace(/\s+/g, " ");
  return cleaned.split(" ").map(token =>
    token.replace(/^([^\p{L}]*)(\p{L})/u,
      (_, prefix, letter) => prefix + letter.toLocaleUpperCase("tr-TR"))
  ).join(" ");
}
async function readTitleCache(db, seriesId, season, rows) {
  const out = new Map();
  if (!db?.prepare || !rows.length) return out;
  try {
    const episodes = [...new Set(rows.map(row => row.episode))];
    const marks = episodes.map(() => "?").join(",");
    const result = await db.prepare(
      "SELECT episode,source_language,original_title,turkish_title " +
      "FROM episode_title_cache WHERE series_id=? AND season=? " +
      "AND translation_version=? AND episode IN (" + marks + ")"
    ).bind(seriesId, season, TITLE_TRANSLATION_VERSION, ...episodes).all();
    const wanted = new Map(rows.map(row => [row.episode, row]));
    for (const item of result?.results || []) {
      const row = wanted.get(Number(item.episode));
      const title = usableLocalizedTitle(item.turkish_title, Number(item.episode));
      if (row && title && String(item.source_language || "") === row.sourceLanguage &&
          String(item.original_title || "") === row.original) {
        out.set(row.episode, {title, original: row.original});
      }
    }
  } catch {}
  return out;
}
async function writeTitleCache(db, seriesId, season, sourceLanguage, rows) {
  if (!db?.prepare || !db?.batch || !rows.length) return;
  try {
    const sql =
      "INSERT INTO episode_title_cache " +
      "(series_id,season,episode,source_language,original_title,turkish_title," +
      "translation_version,model,updated_at) VALUES (?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP) " +
      "ON CONFLICT(series_id,season,episode) DO UPDATE SET " +
      "source_language=excluded.source_language,original_title=excluded.original_title," +
      "turkish_title=excluded.turkish_title,translation_version=excluded.translation_version," +
      "model=excluded.model,updated_at=CURRENT_TIMESTAMP";
    await db.batch(rows.map(row => db.prepare(sql).bind(
      seriesId, season, row.episode, sourceLanguage, row.original, row.title,
      TITLE_TRANSLATION_VERSION, TITLE_AI_MODEL
    )));
  } catch {}
}
async function acquireTitleLock(db, key) {
  if (titleBatchInFlight.has(key)) return false;
  titleBatchInFlight.add(key);
  if (!db?.prepare) return true;
  try {
    const now = Math.floor(Date.now() / 1000);
    const row = await db.prepare(
      "INSERT INTO episode_title_locks(lock_key,expires_at) VALUES (?,?) " +
      "ON CONFLICT(lock_key) DO UPDATE SET expires_at=excluded.expires_at " +
      "WHERE episode_title_locks.expires_at<=? RETURNING lock_key"
    ).bind(key, now + 30, now).first();
    if (!row) {
      titleBatchInFlight.delete(key);
      return false;
    }
  } catch {}
  return true;
}
async function releaseTitleLock(db, key) {
  titleBatchInFlight.delete(key);
  if (!db?.prepare) return;
  try {
    await db.prepare("DELETE FROM episode_title_locks WHERE lock_key=?").bind(key).run();
  } catch {}
}
function parseLocalizedAiResponse(result, requested) {
  let payload = result?.response ?? result;
  if (typeof payload === "string") {
    try { payload = JSON.parse(payload); } catch { return []; }
  }
  const items = Array.isArray(payload?.titles) ? payload.titles : [];
  const wanted = new Map(requested.map(row => [row.episode, row]));
  const out = [];
  const seen = new Set();
  for (const item of items) {
    const episode = Number(item?.episode);
    const input = wanted.get(episode);
    if (!input || seen.has(episode)) continue;
    const raw = usableLocalizedTitle(item?.title, episode);
    if (!raw) continue;
    const title = polishTurkishTitle(raw);
    if (!title) continue;
    seen.add(episode);
    out.push({episode, title, original: input.original});
  }
  return out;
}
async function localizeTitleBatch(env, sourceLanguage, rows) {
  if (!env.AI?.run || !rows.length) return [];
  const system =
    "Sen profesyonel bir film ve dizi yerelleştirme çevirmenisin. " +
    "Verilen bölüm başlıklarını belirtilen kaynak dilinden doğrudan Türkçeye yerelleştir; " +
    "İngilizceyi ara dil olarak kullanma. Kelime kelime çeviri yapma. Deyimsel, mecazi, " +
    "kültürel, mizahi ve dramatik anlamı koruyarak Türkiye'deki profesyonel bir dijital " +
    "yayın platformunda kullanılabilecek doğal ve sanatsal başlık üret. Özel isimleri, " +
    "kişi ve yer adlarını gereksiz yere çevirme. Bir deyim veya kelime oyunu varsa Türkçedeki " +
    "en doğal karşılığı kullan. Anlamdan emin değilsen olay veya anlam uydurma. Başlık zaten " +
    "Türkçeyse değiştirme. Episode 7, Bölüm 7 ve benzeri jenerik adlara yeni başlık uydurma. " +
    "Açıklama, gerekçe, tırnak işareti veya bölüm numarası ekleme.";
  const requested = rows.map(row => ({episode:row.episode, title:row.original}));
  const schema = {
    type:"object",
    properties:{
      titles:{
        type:"array",
        items:{
          type:"object",
          properties:{episode:{type:"integer"},title:{type:"string"}},
          required:["episode","title"],
          additionalProperties:false
        }
      }
    },
    required:["titles"],
    additionalProperties:false
  };
  const result = await env.AI.run(TITLE_AI_MODEL, {
    messages:[
      {role:"system",content:system},
      {role:"user",content:JSON.stringify({
        source_language:sourceLanguage,
        episodes:requested
      })}
    ],
    response_format:{type:"json_schema",json_schema:schema},
    temperature:0.25,
    max_tokens:Math.min(700, 120 + rows.length * 50)
  });
  return parseLocalizedAiResponse(result, rows);
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
  // Exact TMDb TV-episode translations endpoint. It accepts no language/page
  // query here; the client selects the Turkish translation from the payload.
  const episodeTranslations = parts[0] === "tv" && parts.length === 7 &&
    /^\d{1,9}$/.test(parts[1]) && +parts[1] > 0 && parts[2] === "season" &&
    /^\d{1,3}$/.test(parts[3]) && +parts[3] <= 100 && parts[4] === "episode" &&
    /^\d{1,4}$/.test(parts[5]) && +parts[5] > 0 && +parts[5] <= 1000 &&
    parts[6] === "translations";
  if (episodeTranslations) {
    if ([...q.keys()].length !== 0) return null;
    return { upstreamPath: "/" + parts.join("/"), params: new URLSearchParams(), ttl: 21600 };
  }

  // V40: a short season is enriched lazily behind one client request.
  const episodeTitleBatch = parts[0] === "tv" && parts.length === 5 &&
    /^\d{1,9}$/.test(parts[1]) && +parts[1] > 0 && parts[2] === "season" &&
    /^\d{1,3}$/.test(parts[3]) && +parts[3] <= 100 &&
    parts[4] === "episode-titles";
  if (episodeTitleBatch) {
    const language = q.get("language") || "tr-TR";
    const sourceLanguage = (q.get("source_language") || "en").toLowerCase();
    const raw = q.get("episodes") || "";
    if (language !== "tr-TR" || q.getAll("language").length > 1 ||
        q.getAll("episodes").length !== 1 || q.getAll("source_language").length > 1 ||
        !/^[a-z]{2,3}$/.test(sourceLanguage) ||
        [...q.keys()].some(k => !["language", "episodes", "source_language"].includes(k))) return null;
    const episodeNumbers = raw.split(",").map(v => Number(v));
    const canonical = [...episodeNumbers].sort((a,b)=>a-b);
    if (episodeNumbers.length < 1 || episodeNumbers.length > 10 ||
        episodeNumbers.some(n => !Number.isInteger(n) || n < 1 || n > 1000) ||
        new Set(episodeNumbers).size !== episodeNumbers.length ||
        raw !== canonical.join(",")) return null;
    const accepted = new URLSearchParams();
    accepted.set("episodes", raw);
    accepted.set("language", "tr-TR");
    accepted.set("source_language", sourceLanguage);
    accepted.sort();
    return {
      upstreamPath: "/" + parts.join("/"),
      params: accepted,
      ttl: 21600,
      batchEpisodeTitles: {
        seriesId: Number(parts[1]),
        season: Number(parts[3]),
        episodes: episodeNumbers,
        sourceLanguage
      }
    };
  }

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
    for (const k of ["with_watch_providers", "watch_region", "with_watch_monetization_types", "with_genres", "sort_by", "vote_count.gte", "first_air_date.lte", "primary_release_date.lte"]) {
      const v = q.get(k);
      if (v == null) continue;
      const valid = k === "with_watch_providers" ? /^\d{1,6}(,\d{1,6}){0,4}$/.test(v) :
        k === "watch_region" ? /^[A-Z]{2}$/.test(v) :
        k === "with_watch_monetization_types" ? MONETIZATION.has(v) :
        k === "with_genres" ? /^\d{1,4}(,\d{1,4}){0,4}$/.test(v) :
        k === "vote_count.gte" ? /^\d{1,5}$/.test(v) && +v >= 1 && +v <= 10000 :
        k === "first_air_date.lte" ? parts[1] === "tv" && validIsoDate(v) :
        k === "primary_release_date.lte" ? parts[1] === "movie" && validIsoDate(v) :
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
    "with_genres", "sort_by", "vote_count.gte", "first_air_date.lte",
    "primary_release_date.lte", "query"]);
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

    const catalogQuery = catalog.params.toString();
    const catalogSuffix = catalogQuery ? "?" + catalogQuery : "";
    const cacheUrl = new URL(url.origin + "/v1" + catalog.upstreamPath + catalogSuffix);
    // Partition metadata caches by enrichment mode. When the private OMDb key
    // is enabled after v5, a warm TMDb-only cache must not hide IMDb ratings.
    // Only the mode, never the private credential, enters the cache key.
    cacheUrl.searchParams.set("_ea_fb_rating", env.OMDB_API_KEY ? "omdb-v1" : "tmdb-v1");
    if (catalog.batchEpisodeTitles) {
      cacheUrl.searchParams.set("_ea_fb_titles",
        env.AI?.run ? "tmdb-plus-ai-v1" : "tmdb-only-v1");
    }
    const cacheKey = new Request(cacheUrl.toString(), { method: "GET" });
    const cache = typeof caches === "undefined" ? null : caches.default;
    if (cache) {
      try {
        // An edge cache lookup that never settles is also optional. Give it
        // 1.5s, then fetch origin under its separate 12s hard deadline.
        const cached = await withUpstreamDeadline(
          () => cache.match(cacheKey),1500);
        if (cached) return cached;
      } catch {
        // Edge cache failure is not a TMDb outage. Fetch metadata normally.
      }
    }
    // Some dashboards copy an optional Bearer prefix. Never send Bearer Bearer.
    const token = String(env.TMDB_READ_ACCESS_TOKEN).trim().replace(/^Bearer\s+/i, "").trim();
    if (!token) return json({ error: "catalog_unconfigured" }, 503);

    if (catalog.batchEpisodeTitles) {
      let validResponses = 0;
      const titles = {};
      const originals = {};
      const skipped = [];
      let rows = [];
      try {
        rows = await withUpstreamDeadline(async signal =>
          Promise.all(catalog.batchEpisodeTitles.episodes.map(async episode => {
            try {
              const upstreamUrl = UPSTREAM + "/tv/" + catalog.batchEpisodeTitles.seriesId +
                "/season/" + catalog.batchEpisodeTitles.season +
                "/episode/" + episode + "/translations";
              const response = await fetch(upstreamUrl, {
                method: "GET",
                headers: {
                  authorization: "Bearer " + token,
                  accept: "application/json"
                },
                redirect: "manual",
                signal
              });
              if (response.status >= 300 && response.status < 400) return null;
              if (!response.ok ||
                  !(response.headers.get("content-type") || "").includes("application/json")) return null;
              const body = await readBoundedText(response, 200_000);
              const payload = JSON.parse(body);
              if (!payload || typeof payload !== "object" || Array.isArray(payload) ||
                  !Array.isArray(payload.translations)) return null;
              validResponses++;
              return {
                episode,
                sourceLanguage: catalog.batchEpisodeTitles.sourceLanguage,
                turkish: turkishEpisodeTitle(payload),
                original: sourceEpisodeTitle(
                  payload, catalog.batchEpisodeTitles.sourceLanguage
                )
              };
            } catch {
              return null;
            }
          })), 6500);

        const candidates = [];
        for (const row of rows) {
          if (!row) continue;
          const official = usableLocalizedTitle(row.turkish, row.episode);
          const original = usableLocalizedTitle(row.original, row.episode);
          const trustworthyOfficial = official && (
            row.sourceLanguage === "tr" || !original || !sameTitle(official, original)
          );
          if (trustworthyOfficial) {
            titles[String(row.episode)] = official;
            continue;
          }
          if (!original) {
            skipped.push(row.episode);
            continue;
          }
          candidates.push({...row, original});
        }

        const cached = await readTitleCache(
          env.TITLE_CACHE,
          catalog.batchEpisodeTitles.seriesId,
          catalog.batchEpisodeTitles.season,
          candidates
        );
        for (const row of candidates) {
          const hit = cached.get(row.episode);
          if (!hit) continue;
          titles[String(row.episode)] = hit.title;
          originals[String(row.episode)] = hit.original;
        }

        const missing = candidates.filter(row => !titles[String(row.episode)]);
        if (missing.length && env.AI?.run) {
          const lockKey = [
            catalog.batchEpisodeTitles.seriesId,
            catalog.batchEpisodeTitles.season,
            catalog.batchEpisodeTitles.sourceLanguage,
            missing.map(row => row.episode).join(",")
          ].join(":");
          const acquired = await acquireTitleLock(env.TITLE_CACHE, lockKey);
          if (acquired) {
            try {
              const translated = await withUpstreamDeadline(
                () => localizeTitleBatch(
                  env, catalog.batchEpisodeTitles.sourceLanguage, missing
                ),
                7000
              );
              await writeTitleCache(
                env.TITLE_CACHE,
                catalog.batchEpisodeTitles.seriesId,
                catalog.batchEpisodeTitles.season,
                catalog.batchEpisodeTitles.sourceLanguage,
                translated
              );
              for (const row of translated) {
                titles[String(row.episode)] = row.title;
                originals[String(row.episode)] = row.original;
              }
            } catch {
              // Best effort. Existing original title stays visible.
            } finally {
              await releaseTitleLock(env.TITLE_CACHE, lockKey);
            }
          } else {
            await new Promise(resolve => setTimeout(resolve, 350));
            const late = await readTitleCache(
              env.TITLE_CACHE,
              catalog.batchEpisodeTitles.seriesId,
              catalog.batchEpisodeTitles.season,
              missing
            );
            for (const row of missing) {
              const hit = late.get(row.episode);
              if (!hit) continue;
              titles[String(row.episode)] = hit.title;
              originals[String(row.episode)] = hit.original;
            }
          }
        }
      } catch {
        // Background enrichment must never break the detail response.
      }

      const resolvedCount = Object.keys(titles).length + skipped.length;
      const responseTtl =
        validResponses === catalog.batchEpisodeTitles.episodes.length &&
        resolvedCount === catalog.batchEpisodeTitles.episodes.length
          ? catalog.ttl : 60;
      const payload = { titles };
      if (Object.keys(originals).length) payload.originals = originals;
      if (skipped.length) payload.skipped = skipped.sort((x,y)=>x-y);
      const response = json(payload, 200, responseTtl);
      if (cache && ctx?.waitUntil) {
        try {
          ctx.waitUntil(Promise.resolve(cache.put(cacheKey,response.clone())).catch(()=>{}));
        } catch {}
      }
      return response;
    }

    const upstreamUrl = UPSTREAM + catalog.upstreamPath + catalogSuffix;
    let upstream, body, upstreamPhase = "fetch";
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
        upstreamPhase = "body";
        const body = upstream.ok && type.includes("application/json")
          ? await readBoundedText(upstream,2_000_000) : null;
        return {upstream,body};
      },12000));
    } catch (err) {
      if (err?.message === "upstream_too_large") {
        return json({error:"catalog_response_too_large"},502);
      }
      if (upstreamPhase === "body" && err?.name !== "TimeoutError" &&
          err?.name !== "AbortError") {
        return json({error:"invalid_catalog_response"},502);
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
    if (cache && ctx?.waitUntil) {
      try {
        // A failed background cache write must never fail the user request.
        ctx.waitUntil(Promise.resolve(cache.put(cacheKey,response.clone()))
          .catch(()=>{}));
      } catch { /* Cache unavailable; serve the successful metadata. */ }
    }
    return response;
  },
};
