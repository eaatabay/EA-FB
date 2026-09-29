import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
const provider=readFileSync(new URL("../../EA-FB/src/main/kotlin/com/eafb/EAProvider.kt",import.meta.url),"utf8");
const worker=readFileSync(new URL("../../worker/src/index.js",import.meta.url),"utf8");
const detailMeta=readFileSync(new URL("../../EA-FB/src/main/kotlin/com/eafb/DetailMetaRow.kt",import.meta.url),"utf8");
const upcomingStyle=readFileSync(new URL("../../EA-FB/src/main/kotlin/com/eafb/EpisodeUpcomingStyle.kt",import.meta.url),"utf8");
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

test("source-labeled ratings stay in detail tags and out of description",()=>{
  assert.doesNotMatch(provider,/val ratingSummary =/);
  assert.match(provider,/val combinedPlot = listOfNotNull\(\s*seriesNote, upcomingLabel, overview, director/);
  assert.match(provider,/imdbRating\?\.let \{ "IMDb " \+ scoreText\(it\)/);
  assert.match(provider,/tmdbRating\?\.let \{ "TMDb " \+ scoreText\(it\)/);
  assert.ok(provider.indexOf('imdbRating?.let { "IMDb "') < provider.indexOf('tmdbRating?.let { "TMDb "'));
  assert.match(provider,/tags = ratingBadges \+ genreLabels/);
});

test("TV detail metadata moves ratings and genres beside duration with native fallback",()=>{
  assert.match(provider,/DetailMetaRow\.publish\(url, imdbRating, tmdbRating, genreLabels\)/);
  assert.ok(detailMeta.includes('"result_meta_duration"'));
  assert.ok(detailMeta.includes('"result_tag"'));
  assert.ok(detailMeta.includes("Color.YELLOW"));
  assert.ok(detailMeta.includes("Color.rgb(57, 255, 20)"));
  assert.match(detailMeta,/visibility = View\.GONE/);
  assert.match(provider,/tags = ratingBadges \+ genreLabels/);
});

test("posterless first-page platform and genre rails use bounded same-route recovery",()=>{
  assert.match(provider,/CatalogPagePolicy\.extraSparseDiscoverPages\(/);
  assert.match(provider,/val extra = getJson\(route, extraPage\)/);
  assert.match(provider,/if \(results\.isNotEmpty\(\)\) break/);
});

test("chronological film series cue precedes long synopsis in details",()=>{
  assert.match(provider,/val seriesNote = collectionLabel/);
  assert.match(provider,/listOfNotNull\(\s*seriesNote, upcomingLabel, overview, director/);
  assert.match(provider,/recommendations = movieRelated/);
});

test("future TV episodes use long Turkish date and EA-FB yellow upcoming badge",()=>{
  assert.match(provider,/EpisodeUpcomingStyle\.publish\(url, episodes\.mapNotNull \{ it\.airDate \}/);
  assert.ok(upcomingStyle.includes('if (!url.contains("/tv/")) return'));
  assert.ok(upcomingStyle.includes('SimpleDateFormat("d MMMM yyyy EEEE"'));
  assert.ok(upcomingStyle.includes('text = "YAKINDA"'));
  assert.ok(upcomingStyle.includes("Color.rgb(255, 208, 0)"));
  assert.ok(upcomingStyle.includes("Color.rgb(7, 22, 45)"));
  assert.ok(!upcomingStyle.includes("Color.RED"));
});

test("movie stock coming-soon placeholder is hidden without touching TV",()=>{
  assert.ok(detailMeta.includes('if (url.contains("/movie/"))'));
  assert.ok(detailMeta.includes('"result_coming_soon"'));
  assert.ok(!detailMeta.includes('"result_tv_coming_soon"'));
});
