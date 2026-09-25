import test from "node:test";
import assert from "node:assert/strict";
import gateway from "../src/index.js";

function setup() {
  const calls = [];
  let cache = new Map();
  globalThis.caches = { default: {
    match: async (req) => cache.get(req.url)?.clone(),
    put: async (req, res) => { cache.set(req.url, res.clone()); },
  }};
  globalThis.fetch = async (url, opts) => {
    calls.push({ url, opts });
    return new Response(JSON.stringify({ ok: true, url }), {
      headers: { "content-type": "application/json" },
    });
  };
  const env = {
    TMDB_READ_ACCESS_TOKEN: "PRIVATE_TEST_TOKEN_NEVER_RETURNED",
    PER_CLIENT_LIMIT: { limit: async () => ({ success: true }) },
    GLOBAL_LIMIT: { limit: async () => ({ success: true }) },
  };
  const ctx = { waitUntil: (x) => x };
  return { env, ctx, calls };
}

test("health never discloses secret", async () => {
  const { env, ctx } = setup();
  const res = await gateway.fetch(new Request("https://example.workers.dev/health"), env, ctx);
  assert.equal(res.status, 200);
  assert.deepEqual(await res.json(), {status:"ready",service:"EA-FB catalog",version:1});
  assert.ok(!(await (await gateway.fetch(new Request("https://example.workers.dev/health"), env, ctx)).text()).includes(env.TMDB_READ_ACCESS_TOKEN));
});

test("catalog token remains in upstream header, not client response", async () => {
  const { env, ctx, calls } = setup();
  const res = await gateway.fetch(new Request("https://example.workers.dev/v1/tv/42?append_to_response=aggregate_credits,recommendations,external_ids&language=tr-TR"), env, ctx);
  assert.equal(res.status, 200);
  assert.equal(calls.length, 1);
  assert.equal(calls[0].opts.headers.authorization, "Bearer " + env.TMDB_READ_ACCESS_TOKEN);
  assert.ok(!calls[0].url.includes(env.TMDB_READ_ACCESS_TOKEN));
  assert.ok(!(await res.text()).includes(env.TMDB_READ_ACCESS_TOKEN));
});

test("rejects upstream arbitrary URLs and unexpected parameters", async () => {
  const { env, ctx, calls } = setup();
  for (const url of [
    "/v1/https://evil.example",
    "/v1/movie/1?api_key=STOLEN",
    "/v1/movie/1?append_to_response=account",
    "/v1/tv/1/season/99999",
    "/v1/discover/movie?with_watch_providers=1;DROP%20TABLE",
    "/v1/search/multi?query=",
    "/v1/movie/1?language=es-ES",
    "/v1/movie/1?language=tr-TR&language=en-US",
  ]) {
    const res = await gateway.fetch(new Request("https://example.workers.dev"+url), env, ctx);
    assert.equal(res.status, 400, url);
  }
  assert.equal(calls.length, 0);
});

test("caches repeated TMDb catalog calls by canonicalized request", async () => {
  const { env, ctx, calls } = setup();
  const url = "https://example.workers.dev/v1/discover/movie?language=tr-TR&with_genres=878";
  assert.equal((await gateway.fetch(new Request(url), env, ctx)).status, 200);
  assert.equal((await gateway.fetch(new Request(url), env, ctx)).status, 200);
  assert.equal(calls.length, 1);
});

test("serves exact paths used by EA-FB 28-category catalog", async () => {
  const { env, ctx, calls } = setup();
  for (const path of [
    "/v1/trending/all/day", "/v1/movie/now_playing", "/v1/trending/movie/week",
    "/v1/trending/tv/week", "/v1/movie/top_rated", "/v1/tv/top_rated",
    "/v1/discover/movie?with_watch_providers=8&watch_region=TR&with_watch_monetization_types=flatrate",
    "/v1/discover/tv?with_watch_providers=119&watch_region=TR&with_watch_monetization_types=flatrate",
    "/v1/discover/movie?with_genres=27,53",
    "/v1/search/multi?query=Silo",
    "/v1/movie/123?append_to_response=credits,recommendations,external_ids",
    "/v1/tv/4/season/0",
    "/v1/collection/987",
  ]) {
    const res = await gateway.fetch(new Request("https://example.workers.dev"+path), env, ctx);
    assert.equal(res.status, 200, path);
  }
  assert.equal(calls.length, 13);
});

test("discovery sort validates film, series, and rated-list safeguards", async () => {
  const { env, ctx, calls } = setup();
  for (const path of [
    "/v1/discover/movie?with_genres=28&sort_by=primary_release_date.desc",
    "/v1/discover/tv?with_watch_providers=8&sort_by=first_air_date.desc",
    "/v1/discover/movie?with_genres=878&sort_by=vote_average.desc&vote_count.gte=100",
  ]) {
    const res = await gateway.fetch(new Request("https://example.workers.dev" + path), env, ctx);
    assert.equal(res.status, 200, path);
  }
  assert.equal(calls.length, 3);
  for (const path of [
    "/v1/discover/tv?sort_by=primary_release_date.desc",
    "/v1/discover/movie?sort_by=first_air_date.desc",
    "/v1/discover/movie?vote_count.gte=0",
    "/v1/discover/movie?vote_count.gte=10001",
    "/v1/discover/movie?vote_count.gte=100&vote_count.gte=200",
  ]) {
    const res = await gateway.fetch(new Request("https://example.workers.dev" + path), env, ctx);
    assert.equal(res.status, 400, path);
  }
  assert.equal(calls.length, 3);
});

test("rate limit rejects without TMDb fetch", async () => {
  const { env, ctx, calls } = setup();
  env.PER_CLIENT_LIMIT = { limit: async () => ({ success: false }) };
  const res = await gateway.fetch(new Request("https://example.workers.dev/v1/movie/123"), env, ctx);
  assert.equal(res.status, 429);
  assert.equal(calls.length, 0);
});

test("normalizes a stored Bearer prefix without exposing the secret", async () => {
  const {env,ctx,calls}=setup();
  env.TMDB_READ_ACCESS_TOKEN="  Bearer VALID_TEST_VALUE \n";
  const res=await gateway.fetch(new Request("https://example.workers.dev/v1/movie/123"),env,ctx);
  assert.equal(res.status,200);
  assert.equal(calls[0].opts.headers.authorization,"Bearer VALID_TEST_VALUE");
  assert.ok(!(await res.text()).includes("VALID_TEST_VALUE"));
});

test("distinguishes TMDb authorization failure without exposing upstream body", async () => {
  const {env,ctx,calls}=setup();
  globalThis.fetch=async (url, opts) => {
    calls.push({url,opts});
    return new Response(JSON.stringify({status_message:"DO_NOT_LEAK_SECRET_OR_ACCOUNT_DATA"}),{status:401,headers:{"content-type":"application/json"}});
  };
  const res=await gateway.fetch(new Request("https://example.workers.dev/v1/movie/123"),env,ctx);
  assert.equal(res.status,502);
  assert.deepEqual(await res.json(),{error:"tmdb_unauthorized"});
});

test("distinguishes TMDb network failure without leaking exception details", async () => {
  const {env,ctx}=setup();
  globalThis.fetch=async () => {throw new Error("PRIVATE_UNTRUSTED_NETWORK_DETAIL");};
  const res=await gateway.fetch(new Request("https://example.workers.dev/v1/search/multi?query=Silo"),env,ctx);
  assert.equal(res.status,502);
  assert.deepEqual(await res.json(),{error:"tmdb_connection_error",reason:"outbound_network"});
});

test("never forwards the token to redirects and avoids runtime-dependent timeout APIs", async () => {
  const { env, ctx, calls } = setup();
  const result = await gateway.fetch(new Request("https://example.workers.dev/v1/movie/789"), env, ctx);
  assert.equal(result.status, 200);
  assert.equal(calls.length, 1);
  assert.equal(calls[0].opts.redirect, "manual");
  assert.ok(!Object.hasOwn(calls[0].opts, "signal"));
});

test("rejects redirects without forwarding the original Authorization header", async () => {
  const {env,ctx,calls}=setup();
  globalThis.fetch=async (url,opts) => {
    calls.push({url,opts});
    return new Response(null,{status:302,headers:{location:"https://elsewhere.invalid/"}});
  };
  const res=await gateway.fetch(new Request("https://example.workers.dev/v1/movie/789"),env,ctx);
  assert.equal(res.status,502);
  assert.deepEqual(await res.json(),{error:"tmdb_redirect_rejected"});
  assert.equal(calls.length,1);
});

test("optionally adds genuine IMDb rating from OMDb without leaking server key", async () => {
  const {env,ctx,calls}=setup();
  env.OMDB_API_KEY="SERVER_ONLY_OMDB_TEST_KEY";
  globalThis.fetch=async (url,opts) => {
    calls.push({url,opts});
    if (url.startsWith("https://api.themoviedb.org/")) {
      return new Response(JSON.stringify({
        id:123,
        name:"Sample",
        vote_average:8.3,
        vote_count:125,
        external_ids:{imdb_id:"tt14688458"},
      }),{headers:{"content-type":"application/json"}});
    }
    return new Response(JSON.stringify({
      imdbID:"tt14688458",imdbRating:"8.1",Response:"True",
    }),{headers:{"content-type":"application/json"}});
  };
  const request = new Request("https://example.workers.dev/v1/tv/123?append_to_response=external_ids");
  const res=await gateway.fetch(request,env,ctx);
  assert.equal(res.status,200);
  const detail=await res.json();
  assert.equal(detail.ea_fb_ratings.imdb,8.1);
  assert.equal(detail.ea_fb_ratings.source,"OMDb API");
  assert.equal(detail.vote_average,8.3);
  assert.equal(calls.length,2);
  assert.equal(calls[0].opts.headers.authorization,"Bearer "+env.TMDB_READ_ACCESS_TOKEN);
  assert.ok(calls[1].url.includes(encodeURIComponent(env.OMDB_API_KEY)));
  assert.equal(calls[1].opts.redirect,"manual");
  assert.ok(!JSON.stringify(detail).includes(env.OMDB_API_KEY));
  assert.ok(!JSON.stringify(detail).includes(env.TMDB_READ_ACCESS_TOKEN));
});

test("never invents IMDb rating if OMDb is unavailable or responds N/A", async () => {
  const {env,ctx,calls}=setup();
  env.OMDB_API_KEY="SERVER_ONLY_OMDB_TEST_KEY";
  globalThis.fetch=async (url,opts) => {
    calls.push({url,opts});
    if (url.startsWith("https://api.themoviedb.org/")) {
      return new Response(JSON.stringify({
        id:123,
        vote_average:7.4,vote_count:19,
        external_ids:{imdb_id:"tt14688458"},
      }),{headers:{"content-type":"application/json"}});
    }
    return new Response(JSON.stringify({imdbID:"tt14688458",imdbRating:"N/A",Response:"True"}),
      {headers:{"content-type":"application/json"}});
  };
  const r=await gateway.fetch(new Request("https://example.workers.dev/v1/tv/123?append_to_response=external_ids"),env,ctx);
  const body=await r.json();
  assert.equal(r.status,200);
  assert.equal(body.vote_average,7.4);
  assert.equal(body.ea_fb_ratings,undefined);
});

test("no optional OMDb key means zero extra external requests", async () => {
  const {env,ctx,calls}=setup();
  globalThis.fetch=async (url,opts) => {
    calls.push({url,opts});
    return new Response(JSON.stringify({
      id:123,vote_average:8.1,vote_count:22,
      external_ids:{imdb_id:"tt14688458"},
    }),{headers:{"content-type":"application/json"}});
  };
  const res=await gateway.fetch(new Request("https://example.workers.dev/v1/tv/123?append_to_response=external_ids"),env,ctx);
  assert.equal(res.status,200);
  assert.equal((await res.json()).ea_fb_ratings,undefined);
  assert.equal(calls.length,1);
});

test("activating OMDb does not reuse old TMDb-only detail cache", async () => {
  const {env, ctx, calls} = setup();
  globalThis.fetch = async (url, opts) => {
    calls.push({url, opts});
    if (url.startsWith("https://www.omdbapi.com/")) {
      return new Response(JSON.stringify({
        Response:"True", imdbID:"tt14688458", imdbRating:"7.9",
      }), {headers:{"content-type":"application/json"}});
    }
    return new Response(JSON.stringify({
      id:123, vote_average:8.4, vote_count:20,
      external_ids:{imdb_id:"tt14688458"},
    }), {headers:{"content-type":"application/json"}});
  };
  const request = new Request(
    "https://example.workers.dev/v1/movie/123?append_to_response=external_ids"
  );
  const before = await gateway.fetch(request, env, ctx);
  assert.equal((await before.json()).ea_fb_ratings, undefined);
  env.OMDB_API_KEY = "SECRET_ONLY_FOR_SERVER";
  const after = await gateway.fetch(request, env, ctx);
  assert.equal((await after.json()).ea_fb_ratings.imdb, 7.9);
  assert.equal(calls.length, 3); // TMDb without key; TMDb + OMDb with key.
  const cached = await gateway.fetch(request, env, ctx);
  assert.equal((await cached.json()).ea_fb_ratings.imdb, 7.9);
  assert.equal(calls.length, 3);
});
