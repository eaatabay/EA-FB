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
  assert.match(provider,/val combinedPlot = listOfNotNull\(\s*seriesNote, overview, director/);
  assert.match(provider,/imdbRating\?\.let \{ "IMDb " \+ scoreText\(it\)/);
  assert.match(provider,/tmdbRating\?\.let \{ "TMDb " \+ scoreText\(it\)/);
  assert.ok(provider.indexOf('imdbRating?.let { "IMDb "') < provider.indexOf('tmdbRating?.let { "TMDb "'));
  assert.match(provider,/tags = ratingBadges \+ genreLabels/);
});

test("TV detail metadata moves ratings and genres beside duration with native fallback",()=>{
  assert.ok(provider.includes("DetailMetaRow.publish(url, imdbRating, tmdbRating, genreLabels, nextAirDateLabel)"));
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
  assert.match(provider,/listOfNotNull\(\s*seriesNote, overview, director/);
  assert.match(provider,/recommendations = movieRelated/);
});

test("future TV episodes use long Turkish date and EA-FB yellow upcoming badge",()=>{
  assert.ok(provider.includes('EpisodeUpcomingStyle.publish(url, episodes.mapNotNull { ep ->'));
  assert.ok(upcomingStyle.includes('if (!url.contains("/tv/")) return'));
  assert.ok(upcomingStyle.includes('SimpleDateFormat("d MMMM yyyy EEEE"'));
  assert.ok(upcomingStyle.includes('text = "YAKINDA"'));
  assert.ok(upcomingStyle.includes("Color.rgb(255, 208, 0)"));
  assert.ok(upcomingStyle.includes("Color.rgb(7, 22, 45)"));
  assert.ok(!upcomingStyle.includes("Color.RED"));
});

test("stock coming-soon placeholders are suppressed on EA-FB details",()=>{
  assert.ok(detailMeta.includes('"result_coming_soon"'));
  assert.ok(detailMeta.includes('"result_tv_coming_soon"'));
});

test("detail metadata font inherits host TextView without unsafe resource lookup",()=>{
  assert.ok(detailMeta.includes("(duration as? TextView)?.textSize"));
  assert.ok(!detailMeta.includes('getDimension(duration.resources.getIdentifier'));
});

test("V21 exact upcoming UI suppresses native countdown and both host placeholders",()=>{
  assert.ok(provider.includes("private fun nextEpisode") && provider.includes("NextAiring? = null"));
  assert.ok(upcomingStyle.includes("episodeNo = Regex("));
  assert.ok(upcomingStyle.includes("key.second == episodeNo"));
  assert.ok(detailMeta.includes('listOf("result_coming_soon", "result_tv_coming_soon")'));
});

test("V22 uses literal episode regex and exact long Turkish TMDb date",()=>{
  assert.ok(upcomingStyle.includes('Regex("""^\\s*(\\d+)\\.""")'));
  assert.ok(provider.includes('SimpleDateFormat("d MMMM yyyy EEEE", Locale("tr", "TR"))'));
  assert.ok(provider.includes('FutureEpisode(season, episode, date)'));
});

test("V23 persists load score for all CloudStream watch-status bookmark rails",()=>{
  assert.ok(provider.includes("score = tmdbRating?.let { Score.from10(it) }"));
});
test("V23 targets poster and compact episode holder variants",()=>{
  assert.ok(upcomingStyle.includes('listOf("episode_holder_large", "episode_holder")'));
});

test("V24 moves next-air label out of plot into native TV airing row",()=>{
  assert.ok(detailMeta.includes('"result_next_airing_holder"'));
  assert.ok(detailMeta.includes('"result_next_airing_time"'));
  assert.ok(provider.includes("DetailMetaRow.publish(url, imdbRating, tmdbRating, genreLabels, nextAirDateLabel)"));
  assert.ok(!provider.includes("seriesNote, upcomingLabel, overview, director"));
});
test("V24 compact future rows receive exact long date",()=>{
  assert.ok(upcomingStyle.includes('DATE_TAG'));
  assert.ok(upcomingStyle.includes('parent.addView(TextView(activity)'));
});
