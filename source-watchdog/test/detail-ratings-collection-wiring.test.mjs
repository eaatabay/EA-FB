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
test("franchise detail cards only reference visible official collection artwork",()=>{
  assert.match(provider,/belongs_to_collection/);
  assert.match(provider,/FilmCollectionPolicy\.chronological/);
  assert.match(provider,/Film Serisi: \$\{cards\.size\} film • vizyon sırası/);
  assert.match(provider,/if \(ownId !in visibleIds\) return FilmCollectionResult\(null, emptyList\(\), emptyMap\(\), emptyMap\(\)\)/);
  assert.match(provider,/val collectionUrls = collectionCards\.map \{ it\.url \}\.toSet\(\)/);
  assert.match(provider,/val movieRelated = recs\.filterNot/);
  assert.match(provider,/val releaseDates = sortedParts\.mapNotNull/);
  assert.match(provider,/FilmCollectionPolicy\.displayDate\(part\.releaseDate\)/);
  assert.doesNotMatch(provider,/movieRelated.*take\(32\)/);
});

test("source-labeled ratings remain visible at the top of detail description",()=>{
  assert.match(provider,/val ratingSummary = ratingBadges\.takeIf \{ it\.isNotEmpty\(\) \}/);
  assert.match(provider,/val combinedPlot = listOfNotNull\(\s*ratingSummary, seriesNote, upcomingLabel, overview, director/);
  assert.match(provider,/imdbRating\?\.let \{ "IMDb " \+ scoreText\(it\)/);
  assert.match(provider,/tmdbRating\\?\\.let \\{ "TMDb " \\+ scoreText\\(it\\)/);
  assert.match(provider,/val ratingBadges = listOfNotNull\\(\\s*imdbRating\\?\\.let[\\s\\S]*tmdbRating\\?\\.let/);
  assert.match(provider,/tags = ratingBadges \\+ genres\\(item\\)/);
});

test("posterless first-page platform and genre rails use bounded same-route recovery",()=>{
  assert.match(provider,/CatalogPagePolicy\.extraSparseDiscoverPages\(/);
  assert.match(provider,/val extra = getJson\(route, extraPage\)/);
  assert.match(provider,/if \(results\.isNotEmpty\(\)\) break/);
});

test("chronological film series cue precedes long synopsis in details",()=>{
  assert.match(provider,/val seriesNote = collectionLabel/);
  assert.match(provider,/ratingSummary, seriesNote, upcomingLabel, overview, director/);
  assert.match(provider,/recommendations = movieRelated/);
});
