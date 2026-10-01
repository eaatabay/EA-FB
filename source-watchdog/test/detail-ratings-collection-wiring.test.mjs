import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
const provider=readFileSync(new URL("../../EA-FB/src/main/kotlin/com/eafb/EAProvider.kt",import.meta.url),"utf8");
const worker=readFileSync(new URL("../../worker/src/index.js",import.meta.url),"utf8");
const detailMeta=readFileSync(new URL("../../EA-FB/src/main/kotlin/com/eafb/DetailMetaRow.kt",import.meta.url),"utf8");
const upcomingStyle=readFileSync(new URL("../../EA-FB/src/main/kotlin/com/eafb/EpisodeUpcomingStyle.kt",import.meta.url),"utf8");
const episodeTitleStyle=readFileSync(new URL("../../EA-FB/src/main/kotlin/com/eafb/EpisodeTitleStyle.kt",import.meta.url),"utf8");
const plugin=readFileSync(new URL("../../EA-FB/src/main/kotlin/com/eafb/EAPlugin.kt",import.meta.url),"utf8");
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

test("source-labeled ratings stay in detail tags while the unlabeled native score is hidden",()=>{
  assert.doesNotMatch(provider,/val ratingSummary =/);
  assert.match(provider,/val combinedPlot = listOfNotNull\(\s*seriesNote, overview, director/);
  assert.match(provider,/imdbRating\?\.let \{ "IMDb " \+ scoreText\(it\)/);
  assert.match(provider,/tmdbRating\?\.let \{ "TMDb " \+ scoreText\(it\)/);
  assert.ok(provider.indexOf('imdbRating?.let { "IMDb "') < provider.indexOf('tmdbRating?.let { "TMDb "'));
  assert.match(provider,/tags = ratingBadges \+ genreLabels/);
  assert.ok(detailMeta.includes('"result_meta_rating"'));
  assert.ok(detailMeta.includes("findViews(root, nativeRatingId)"));
  assert.ok(provider.includes("score = tmdbRating?.let { Score.from10(it) }"),
    "bookmark/watch-status rails must keep the stored score");
});

test("episode descriptions do not repeat the visible TMDb score and use real synopsis line breaks",()=>{
  assert.ok(!provider.includes("Bölüm puanı: TMDb"));
  assert.ok(provider.includes(').joinToString("\\n\\n")'));
  assert.ok(!provider.includes(').joinToString("\\\\n\\\\n")'));
  assert.match(provider,/description = if \(future\) \{[\s\S]*?\} else text/);
});

test("TV season and episode panel scales as one 90 percent unit",()=>{
  assert.ok(detailMeta.includes("private const val EPISODE_PANEL_SCALE = 0.90f"));
  assert.ok(detailMeta.includes('"episode_holder_tv"'));
  assert.ok(detailMeta.includes("panel.scaleX = EPISODE_PANEL_SCALE"));
  assert.ok(detailMeta.includes("panel.scaleY = EPISODE_PANEL_SCALE"));
  assert.ok(detailMeta.includes("panel.pivotY = 0f"));
  assert.ok(detailMeta.includes("panel.width.toFloat()"));
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

test("V39 keeps native next-air binding as first-open fallback and exact custom date as final row",()=>{
  assert.ok(provider.includes("return NextAiring(episode, airing.unixSeconds, season)"));
  assert.ok(upcomingStyle.includes("EpisodeRowPolicy.episodeNumber"));
  assert.ok(upcomingStyle.includes("it.episode == episodeNo && it.date > now"));
  assert.ok(detailMeta.includes('listOf("result_coming_soon", "result_tv_coming_soon")'));
  assert.ok(detailMeta.includes("observeDetailFocus(root, fragment)"));
  assert.ok(detailMeta.includes("renderNextEpisode(root, meta, activity)"));
  assert.ok(detailMeta.includes("addOnGlobalFocusChangeListener"));
  assert.ok(!detailMeta.includes("addOnGlobalLayoutListener"));
});

test("V22 keeps exact long Turkish TMDb date while V37 centralizes row parsing",()=>{
  assert.ok(upcomingStyle.includes("EpisodeRowPolicy.episodeNumber"));
  assert.ok(provider.includes('SimpleDateFormat("d MMMM yyyy EEEE", Locale("tr", "TR"))'));
  assert.ok(provider.includes('FutureEpisode(season, episode, date, ep.name)'));
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
test("V24 compact future rows receive exact Turkish long date",()=>{
  assert.ok(upcomingStyle.includes('DATE_TAG'));
  assert.ok(upcomingStyle.includes('parent.addView(TextView(activity)'));
});

test("future episode decoration still renders after the host binds rows",()=>{
  assert.ok(upcomingStyle.includes("renderFragment(fragment)"));
  assert.ok(upcomingStyle.includes("YAKINDA"));
  assert.ok(upcomingStyle.includes("longTurkishDate(future.date)"));
});

test("V26 clears recycled YAKINDA badges before future-only decoration",()=>{
  assert.ok(upcomingStyle.includes("removeView(badge)"));
  assert.ok(upcomingStyle.includes("it.episode == episodeNo && it.date > now"));
});

test("V27 fills blank Turkish episode metadata from English and uses series backdrop for missing stills",()=>{
  assert.ok(provider.includes('getJson("/tv/$id/season/$number", language = "en-US")'));
  assert.ok(provider.includes('english?.optString("overview")'));
  assert.ok(provider.includes('english?.optString("name")'));
  assert.ok(provider.includes('english?.optString("still_path")'));
  assert.ok(provider.includes('posterUrl = image(still, "w500") ?: fallbackBackdrop'));
});

// V27 CI trigger after staging-version regression alignment.

test("V28 carries resolved episode names into the generic future-row decorator",()=>{
  assert.ok(upcomingStyle.includes("val name: String? = null"));
  assert.ok(upcomingStyle.includes('text = "$episodeNo. $actualName"'));
  assert.ok(provider.includes("date, ep.name"));
});

test("V29 forces resolved future episode title into visible host title view",()=>{
  assert.ok(upcomingStyle.includes('text = "$episodeNo. $actualName"'));
  assert.ok(upcomingStyle.includes("visibility = View.VISIBLE"));
});

test("V30 explicitly binds resolved TMDb title to CloudStream Episode.name",()=>{
  assert.ok(provider.includes("this.name = episodeName.ifBlank { null }"));
});

test("V31 rejects localized generic episode labels before EN SxE fallback",()=>{
  assert.ok(provider.includes("genericEpisodeName"));
  assert.ok(provider.includes("!genericEpisodeName(englishName, episodeNo) -> englishName"));
  assert.ok(provider.includes("this.name = episodeName.ifBlank { null }"));
});

test("V32 binds future decoration and metadata fallback to SxE identity",()=>{
  assert.ok(provider.includes('row.optInt("season_number", number) == number'));
  assert.ok(provider.includes('row.optInt("episode_number") == episodeNo'));
  assert.ok(upcomingStyle.includes("it.episode == episodeNo && it.date > now"));
  assert.ok(!upcomingStyle.includes("findVisibleSeason(root)"));
});

test("V33 generic episode names really fall through instead of preserving placeholders",()=>{
  assert.ok(provider.includes('Regex("""(?i)^$escapedEpisode\\.?\\s*bölüm$""")'));
  assert.ok(provider.includes('Regex("""(?i)^episode\\s*$escapedEpisode$""")'));
  assert.ok(!provider.includes('episode\\\\s*'));
});

test("V35 removes per-episode translation HTTP calls from the blocking detail path",()=>{
  assert.ok(worker.includes('parts[6] === "translations"'),
    "the exact server capability may remain for future lazy enrichment");
  assert.ok(!provider.includes("getUnlocalizedJson("));
  assert.ok(!provider.includes('"/tv/$id/season/$number/episode/$episodeNo/translations"'));
  assert.ok(!provider.includes("translatedTurkishName"));
  assert.ok(provider.includes("!missingTurkishTitle -> localizedName"));
  assert.ok(provider.includes("!genericEpisodeName(englishName, episodeNo) -> englishName"));
  assert.ok(provider.includes("val needsEnglish = (0 until episodeRows.length()).any"));
  assert.ok(provider.includes("val englishRows = if (needsEnglish)"));
});

test("V37 binds YAKINDA to season-aware metadata with bounded scroll/focus rendering",()=>{
  assert.ok(upcomingStyle.includes("EpisodeRowPolicy.episodeNumber"));
  assert.ok(upcomingStyle.includes("futureEpisodes[season to episodeNo]"));
  assert.ok(upcomingStyle.includes("val seasonSelection = selectedSeason(root, activity)"));
  assert.ok(upcomingStyle.includes("addOnScrollChangedListener"));
  assert.ok(upcomingStyle.includes("addOnGlobalFocusChangeListener"));
  assert.ok(upcomingStyle.includes("pendingScrollRenders"));
  assert.ok(upcomingStyle.includes("pendingFocusRenders"));
  assert.ok(upcomingStyle.includes("renderFragment(fragment)"));
  assert.ok(upcomingStyle.includes("observedScrollRoots"));
  assert.ok(upcomingStyle.includes("seenTitles"));
  assert.ok(upcomingStyle.includes("Compact CloudStream episode rows have no poster at all"));
  assert.ok(!upcomingStyle.includes("addOnGlobalLayoutListener"));
  assert.ok(!upcomingStyle.includes("androidx.recyclerview.widget.RecyclerView"));
  assert.ok(!upcomingStyle.includes("minByOrNull { it.date }"));
  assert.ok(!upcomingStyle.includes('getIdentifier("episode_upcoming_format", "string"'));
});

// V35 final full-CI trigger after staging guard repair.

test("V37 catalog paging dedupes repeated card URLs across page calls without touching Worker routing",()=>{
  assert.ok(provider.includes("private val mainPageDeduper = CatalogPageDeduper()"));
  assert.ok(provider.includes("mainPageDeduper.filter("));
  assert.ok(provider.includes("category.id + \"|\" + sortMode.name"));
});

test("V38 retries first-open detail metadata after season loading without layout polling",()=>{
  const detailMeta=readFileSync(new URL("../../EA-FB/src/main/kotlin/com/eafb/DetailMetaRow.kt",import.meta.url),"utf8");
  const episodeLoad = provider.indexOf('val episodeLoad = tvEpisodes(tmdbId');
  const republish = provider.indexOf('DetailMetaRow.publish(url, imdbRating, tmdbRating, genreLabels, nextAirDateLabel)', episodeLoad);
  assert.ok(episodeLoad >= 0 && republish > episodeLoad);
  assert.ok(detailMeta.includes('main.postDelayed({ renderRegistered() }, 300)'));
  assert.ok(detailMeta.includes('main.postDelayed({ renderRegistered() }, 900)'));
  assert.ok(detailMeta.includes('main.postDelayed({ renderRegistered() }, 1800)'));
  assert.ok(!detailMeta.includes("addOnGlobalLayoutListener"));
});

test("V39 first-open next-air repair stays isolated from episode-row performance code",()=>{
  assert.ok(detailMeta.includes("pendingFocusRenders"));
  assert.ok(detailMeta.includes("main.postDelayed({"));
  assert.ok(detailMeta.includes("renderNextEpisodeOnly(fragment)"));
  assert.ok(!detailMeta.includes("androidx.recyclerview.widget.RecyclerView"));
  assert.ok(upcomingStyle.includes("pendingFocusRenders"));
});

test("V44 enriches every missing normal-season title lazily without blocking the V39 detail path",()=>{
  assert.ok(plugin.includes("EpisodeTitleStyle.install(context)"));
  assert.ok(provider.includes("CoroutineScope(SupervisorJob() + Dispatchers.IO)"));
  assert.ok(provider.includes("scheduleTurkishEpisodeTitles("));
  assert.ok(provider.includes('item.optString("original_language")'));
  assert.ok(provider.includes('episode-titles/$safeSource?episodes=$query'));
  assert.ok(provider.includes("numbers.chunked(10)"));
  assert.ok(!provider.includes("var remaining = 48"));
  assert.ok(provider.includes("EpisodeTitleCandidate"));
  assert.ok(provider.includes("missingTurkishTitle"));
  assert.ok(provider.includes("if (number > 0 && missingTurkishTitle)"));
  assert.ok(provider.includes("titleBatches(titleCandidates)"));
  assert.ok(provider.includes("EpisodeTitleStyle.publish(seriesUrl, resolved)"));
  assert.ok(provider.includes("translated[season to episode]?.name ?: ep.name"));
  assert.ok(!provider.includes('"/tv/$id/season/$number/episode/$episodeNo/translations"'));
  assert.ok(worker.includes('parts[4] === "episode-titles"'));
  assert.ok(worker.includes("episodeNumbers.length > 10"));
  assert.ok(worker.includes('TITLE_TRANSLATION_VERSION = "v43-localize-3"'),
    "reuse valid D1 AI cache while V44 edge cache semantics change");
  assert.ok(worker.includes('tmdb-plus-ai-v44'));
  assert.ok(worker.includes('"episode","chapter","bölüm"'));
  assert.ok(worker.includes("sourceEpisodeOverview"));
  assert.ok(worker.includes("row.context ? {context:row.context.slice(0, 700)}"));
  assert.ok(worker.includes("localizeTitleBatch"));
  assert.ok(worker.includes("withUpstreamDeadline"));
  assert.ok(episodeTitleStyle.includes("EpisodeRowPolicy.episodeNumber"));
  assert.ok(episodeTitleStyle.includes("addOnGlobalFocusChangeListener"));
  assert.ok(episodeTitleStyle.includes("addOnScrollChangedListener"));
  assert.ok(!episodeTitleStyle.includes("addOnGlobalLayoutListener"));
  assert.ok(!episodeTitleStyle.includes("androidx.recyclerview.widget.RecyclerView"));
});
