import test from "node:test";
import assert from "node:assert/strict";
import {validateCatalogShelves,publicCatalogShelves,compileCatalogShelf,buildCatalogDraft,editCatalogDraft} from "../src/catalog-shelves.mjs";
const base={id:"new-turkish-tv",title:"Yeni Türk Dizileri",kind:"tv",
  genres:"18",language:"tr",yearFrom:2024,yearTo:2026,enabled:true,order:0};
test("validates catalog-only shelf and preserves order",()=>{
  const rows=validateCatalogShelves([{...base,order:2},
    {id:"apple-movie",title:"Apple TV Filmleri",kind:"movie",
      providerId:"350",region:"TR",enabled:true,order:0}]);
  assert.equal(rows[0].id,"apple-movie");
  assert.equal(publicCatalogShelves([{...base,enabled:false}]).shelves.length,0);
  assert.equal(Object.isFrozen(rows[0]),true);
});
test("rejects unapproved playback fields, malformed filters, duplicate IDs",()=>{
  for(const bad of [
    [{...base,streamUrl:"https://example.org/movie"}],
    [{...base,authorized:true}],
    [{...base,providerId:"350"}],
    [{...base,genres:"18;DROP"}],
    [{...base,region:"TR"}],
    [{...base},{...base}],
    [{...base,title:"<script>"}],
    [{...base,title:"Two"+String.fromCharCode(10)+"Lines"}],
    [{...base,order:-1}],
    [{...base,enabled:"true"}],
    [{...base,id:"netflix-movie"}],
    [{...base,id:"trending"}],
    [{...base,language:"TR"}],
    [{...base,language:"tr-TR"}],
    [{...base,yearFrom:2027,yearTo:2026}],
    [{...base,yearFrom:"2024"}],
    [{...base,yearTo:1800}],
  ]) assert.throws(()=>validateCatalogShelves(bad));
});

test("compile only relay-compatible catalog filters, never silently drop constraints",()=>{
  const safe={...base,language:undefined,yearFrom:undefined,yearTo:undefined};
  assert.equal(compileCatalogShelf(safe).path,"/discover/tv?with_genres=18");
  assert.equal(compileCatalogShelf({id:"apple-archive",title:"Apple Archive",
    kind:"movie",providerId:350,region:"TR",enabled:true,order:0}).path,
    "/discover/movie?with_watch_providers=350&watch_region=TR&with_watch_monetization_types=flatrate");
  assert.throws(()=>compileCatalogShelf(base),/unsupported_catalog_filter/);
  assert.throws(()=>compileCatalogShelf({...safe,yearFrom:2020}),/unsupported_catalog_filter/);
});

test("revisioned catalog draft is deterministic and never self-publishes",()=>{
  const draft=buildCatalogDraft([{...base,enabled:false}],4);
  assert.equal(draft.revision,4);
  assert.equal(draft.status,"draft-v6-not-published");
  assert.equal(draft.shelves[0].enabled,false);
  assert.equal(Object.isFrozen(draft),true);
  assert.throws(()=>buildCatalogDraft([base],-1));
  assert.throws(()=>buildCatalogDraft([base],1.5));
});

test("CAS catalog editor covers add, rename, toggle, move, replace and remove",()=>{
  const safe={...base,language:undefined,yearFrom:undefined,yearTo:undefined};
  let draft=buildCatalogDraft([safe],0);
  draft=editCatalogDraft(draft,0,{action:"add",id:"apple-archive",
    shelf:{id:"apple-archive",title:"Apple Arşivi",kind:"movie",
      providerId:"350",region:"TR",enabled:true,order:1}});
  assert.equal(draft.revision,1);
  assert.equal(draft.shelves.length,2);
  draft=editCatalogDraft(draft,1,{action:"rename",id:"apple-archive",title:"Apple Filmleri"});
  draft=editCatalogDraft(draft,2,{action:"toggle",id:"apple-archive",enabled:false});
  draft=editCatalogDraft(draft,3,{action:"move",id:"apple-archive",order:0});
  assert.equal(draft.shelves[0].id,"apple-archive");
  assert.equal(draft.shelves[0].enabled,false);
  draft=editCatalogDraft(draft,4,{action:"replace",id:"apple-archive",
    shelf:{...draft.shelves[0],title:"Apple TV+"}});
  assert.equal(draft.shelves[0].title,"Apple TV+");
  draft=editCatalogDraft(draft,5,{action:"remove",id:"apple-archive"});
  assert.equal(draft.shelves.length,1);
  assert.equal(draft.status,"draft-v6-not-published");
});
test("CAS catalog editor rejects stale revisions and unauthorized mutations",()=>{
  const draft=buildCatalogDraft([base],2);
  for(const operation of [
    {action:"remove",id:"not-present"},
    {action:"rename",id:base.id,title:"<script>"},
    {action:"toggle",id:base.id,enabled:"true"},
    {action:"move",id:base.id,order:9},
    {action:"add",id:"custom-tv",shelf:{...base,id:"custom-tv",streamUrl:"https://evil.invalid"}},
    {action:"remove",id:base.id,secret:"not-allowed"}
  ]) assert.throws(()=>editCatalogDraft(draft,2,operation));
  assert.throws(()=>editCatalogDraft(draft,1,{action:"remove",id:base.id}),
    /catalog_revision_conflict/);
  assert.equal(draft.shelves.length,1);
});
