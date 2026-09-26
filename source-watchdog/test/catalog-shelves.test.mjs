import test from "node:test";
import assert from "node:assert/strict";
import {validateCatalogShelves,publicCatalogShelves} from "../src/catalog-shelves.mjs";
const base={id:"new-turkish-tv",title:"Yeni Türk Dizileri",kind:"tv",
  genres:"18",enabled:true,order:0};
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
    [{...base,order:-1}],
    [{...base,enabled:"true"}],
  ]) assert.throws(()=>validateCatalogShelves(bad));
});
