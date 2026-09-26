import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import {validateCatalogShelves} from "../src/catalog-shelves.mjs";

test("all Android built-in catalog IDs remain reserved in offline admin drafts",()=>{
  const domain=readFileSync(new URL("../../EA-FB/src/main/kotlin/com/eafb/Domain.kt",import.meta.url),"utf8");
  const home=domain.slice(domain.indexOf("object HomeCategories"),domain.indexOf("object Identity"));
  const ids=[...home.matchAll(/CatalogCategory\("([^"]+)"/g)].map(match=>match[1]);
  assert.ok(ids.length>=29,"unexpectedly few built-in rails: check Domain.kt parser");
  assert.equal(new Set(ids).size,ids.length,"Android built-in IDs must be unique");
  for(const id of ids) assert.throws(()=>validateCatalogShelves([{
    id,title:"Overridden rail",kind:"movie",genres:"18",enabled:true,order:0
  }]),/invalid_catalog_shelf/,"admin collision: "+id);
});
