import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
const provider=readFileSync(new URL("../../EA-FB/src/main/kotlin/com/eafb/EAProvider.kt",import.meta.url),"utf8");
const worker=readFileSync(new URL("../../worker/src/index.js",import.meta.url),"utf8");
test("IMDb title badge requires independently sourced OMDb score",()=>{
  assert.match(provider,/optString\("source"\) == "OMDb API"/);
  assert.match(provider,/optJSONObject\("external_ids"\)\?\.optString\("imdb_id"\)/);
  assert.match(worker,/result\.ea_fb_ratings = \{ imdb: parsed\.rating, source: "OMDb API" \}/);
  assert.match(worker,/const hadUntrustedRating = Object\.hasOwn\(result, "ea_fb_ratings"\)/);
});
test("franchise detail labels only reference visible official collection artwork",()=>{
  assert.match(provider,/belongs_to_collection/);
  assert.match(provider,/FilmCollectionPolicy\.chronological/);
  assert.match(provider,/FilmCollectionPolicy\.visibleChronology/);
  assert.match(provider,/if \(ownId !in visibleIds\) return Pair\(null, emptyList\(\)\)/);
  assert.match(provider,/val movieRelated = \(collectionCards \+ recs\)\.distinctBy/);
});
