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
  }
  assert.equal(calls.length,shelves.length,"every shelf must route to TMDb");
  assert.ok(calls.every(c=>c.opts.headers.authorization==="Bearer LOCAL_TEST_ONLY"));
});
