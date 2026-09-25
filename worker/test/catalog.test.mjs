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
  assert.deepEqual(await res.json(),{error:"tmdb_connection_error"});
});
