import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import gateway from "../../worker/src/index.js";

// Exercise the ACTUAL Android shelf declarations against the ACTUAL Worker
// allowlist. No external API, credentials, Cloudflare deployment or D1.
const domain = readFileSync(new URL("../../EA-FB/src/main/kotlin/com/eafb/Domain.kt", import.meta.url), "utf8");
const block = domain.split("object HomeCategories {")[1]?.split("\nobject Identity {")[0];
if (!block) throw new Error("HomeCategories declaration missing");
const shelves = [...block.matchAll(/CatalogCategory\("([^"]+)",\s*"([^"]+)",\s*MediaKind\.(MOVIE|SERIES),\s*"([^"]+)"\)/g)]
  .map(([,id,title,kind,path])=>({id,title,kind,path}));
test("all checked-in Android metadata shelves pass the Worker route allowlist",async()=>{
  assert.ok(shelves.length>=20,"category parser must see the actual platform and genre shelves");
  assert.ok(shelves.some(s=>s.id==="netflix-movie"));
  assert.ok(shelves.some(s=>s.id==="amazon-tv"));
  assert.ok(shelves.some(s=>s.id==="sci-fi"));
  const calls=[];
  globalThis.caches={default:{
    match:async()=>null,
    put:async()=>{},
  }};
  globalThis.fetch=async(url,opts)=>{
    calls.push({url,opts});
    return new Response(JSON.stringify({page:1,total_pages:1,results:[]}),{
      headers:{"content-type":"application/json"}
    });
  };
  const env={
    TMDB_READ_ACCESS_TOKEN:"LOCAL_TEST_ONLY",
    PER_CLIENT_LIMIT:{limit:async()=>({success:true})},
    GLOBAL_LIMIT:{limit:async()=>({success:true})},
  };
  const ctx={waitUntil:()=>{}};
  for(const shelf of shelves){
    const response=await gateway.fetch(
      new Request("https://local.invalid/v1"+shelf.path+
        (shelf.path.includes("?")?"&":"?")+"language=tr-TR"),
      env,ctx);
    assert.equal(response.status,200,shelf.id+": "+JSON.stringify(await response.json()));
    const forwarded=new URL(calls.at(-1).url);
    const expected=new URL("https://api.themoviedb.org/3"+shelf.path);
    assert.equal(forwarded.pathname,expected.pathname,shelf.id+" path");
    for(const [key,value] of expected.searchParams){
      assert.equal(forwarded.searchParams.get(key),value,shelf.id+" "+key);
    }
    assert.equal(forwarded.searchParams.get("language"),"tr-TR",shelf.id+" language");
  }
  assert.equal(calls.length,shelves.length,"every shelf must route to TMDb");
  assert.ok(calls.every(c=>c.opts.headers.authorization==="Bearer LOCAL_TEST_ONLY"));
});

test("sparse regional shelf page-2 retains provider, region and genre filters",async()=>{
  const requests=[];
  globalThis.caches={default:{match:async()=>null,put:async()=>{}}};
  globalThis.fetch=async(url,opts)=>{
    requests.push(new URL(url));
    return new Response(JSON.stringify({page:2,total_pages:3,results:[]}),{
      headers:{"content-type":"application/json"}
    });
  };
  const env={TMDB_READ_ACCESS_TOKEN:"LOCAL_TEST_ONLY",
    PER_CLIENT_LIMIT:{limit:async()=>({success:true})},
    GLOBAL_LIMIT:{limit:async()=>({success:true})}};
  const ctx={waitUntil:()=>{}};
  for(const path of [
    "/discover/movie?with_watch_providers=8&watch_region=TR&with_watch_monetization_types=flatrate",
    "/discover/tv?with_watch_providers=119&watch_region=TR&with_watch_monetization_types=flatrate",
    "/discover/movie?with_genres=878"
  ]){
    const result=await gateway.fetch(new Request(
      "https://local.invalid/v1"+path+"&language=tr-TR&page=2"),env,ctx);
    assert.equal(result.status,200,path);
    const forwarded=requests.at(-1);
    assert.equal(forwarded.searchParams.get("page"),"2");
    assert.equal(forwarded.searchParams.get("language"),"tr-TR");
    const expected=new URL("https://api.themoviedb.org/3"+path);
    for(const [key,value] of expected.searchParams){
      assert.equal(forwarded.searchParams.get(key),value,key);
    }
  }
  assert.equal(requests.length,3);
});
