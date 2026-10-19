package com.eafb

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.FixtureExtractor
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.io.File

/**
 * Replay of safe fields from observed EA-FB catalogue/source metadata against V83.
 * Adapter responses remain synthetic; no runtime alias registry is created.
 */
private const val SHORT = "Organize İşler"
private const val LONG = "Organize İşler: Karun Hazinesi"
private const val CONFIRMED_TMDB = 320754
private const val REPORTED_IMDB = "tt38732131"
private const val REPORTED_NETFLIX = "https://www.netflix.com/tr/title/82047949"
private val EXCLUDED_MOVIES = listOf(30634 to "Organize İşler", 577990 to "Organize İşler: Sazan Sarmalı")
private data class SeriesPage(val title: String, val year: Int? = 2026,
    val movie: Boolean = false, val season: Int = 1, val episode: Int = 8)
private class SeriesFixture(private val host: String, private val page: SeriesPage) : MainAPI() {
    var searches = 0
    var loads = 0
    var resolves = 0
    override suspend fun search(query: String): List<SearchResponse> {
        searches++
        return listOf(SearchResponse(page.title, "https://$host/fixture/"))
    }
    override suspend fun load(url: String): LoadResponse {
        loads++
        return if (page.movie) MovieLoadResponse(page.title, page.year, "opaque-fixture-movie")
        else TvSeriesLoadResponse(page.title, page.year,
            listOf(Episode("opaque-fixture-episode", page.season, page.episode)))
    }
    override suspend fun loadLinks(data: String, isCasting: Boolean,
        subtitleCallback: (com.lagradost.cloudstream3.utils.SubtitleFile) -> Unit,
        callback: (com.lagradost.cloudstream3.utils.ExtractorLink) -> Unit): Boolean {
        resolves++
        error("Diagnosis must not resolve streams")
    }
}

fun main() = runBlocking {
    // Replay only safe metadata fields captured from real read-only EA-FB catalogue CI.
    // Adapter network responses below remain synthetic and are labelled separately.
    val recorded = JSONObject(File("docs/series-name-codex/EA-FB-KATALOG-GOZLEM.json").readText())
    val metadata = recorded.getJSONObject("tv")
    val query = PlaybackQuery.fromData(
        "ea-fb:episode:" + metadata.getInt("id") + ":1:8",
        metadata.getString("name"), metadata.getString("first_air_date").take(4).toInt(),
        listOf(metadata.getString("original_name"))
    )!!
    var assertions = 0
    fun verify(value: Boolean, message: String) { check(value) { message }; assertions++ }
    // User-supplied identity references are kept explicit; a name alone is never an ID.
    println("IDENTITY CONTEXT (catalogue CI 39311300009 confirmed): tv=$CONFIRMED_TMDB imdb=$REPORTED_IMDB netflix=$REPORTED_NETFLIX")
    verify(recorded.getJSONObject("external").getString("imdb_id") == REPORTED_IMDB,
        "Actual EA-FB external_ids must confirm the researched IMDb identity")
    val actualSeason = recorded.getJSONObject("season")
    verify(actualSeason.getInt("season_number") == 1 && actualSeason.getString("name") == "Karun Hazinesi" &&
        actualSeason.getJSONArray("episodes").length() == 8,
        "Actual catalogue distinguishes the season subtitle from the series title")
    val observedSource = JSONObject(File("docs/series-name-codex/DIZIYOU-GOZLEM.json").readText())
    val actualSourceTitles = observedSource.getJSONArray("searchCards")
    verify(actualSourceTitles.length() > 0 && (0 until actualSourceTitles.length()).any { index ->
        Identity.normalize(actualSourceTitles.getJSONObject(index).getString("title")) in
            SourceSearchTitles.candidates(query).map(Identity::normalize)
    }, "Confirmed DiziYou title now matches the narrowly expanded catalogue candidates")
    verify((0 until actualSourceTitles.length()).any { index ->
        Identity.normalize(actualSourceTitles.getJSONObject(index).getString("title")) in
            SourceSearchTitles.candidates(query.copy(alternateTitles = listOf(LONG))).map(Identity::normalize)
    }, "An explicitly supplied verified long title can match the observed result without fuzzy names")
    val parsedSeries = PlaybackQuery.fromData("ea-fb:episode:320754:1:8", SHORT, 2026, listOf(SHORT))!!
    verify(parsedSeries == query, "Playback identity retains the candidate TV ID, year and S1E8")
    verify(SourceSearchTitles.candidates(query) == listOf(SHORT, LONG),
        "Only the confirmed identity gains its verified season alternative")
    for ((id, _) in EXCLUDED_MOVIES) {
        val actualSearch = recorded.getJSONArray("search")
        val movie = (0 until actualSearch.length()).map(actualSearch::getJSONObject).single { it.getInt("id") == id }
        verify(movie.getString("media_type") == "movie", "Observed catalogue film $id must not be a TV result")
        val film = PlaybackQuery.fromData("ea-fb:movie:$id", movie.getString("title"),
            movie.getString("release_date").take(4).toInt())!!
        verify(film.kind == MediaKind.MOVIE && film.tmdbId == id && film.season == null && film.episode == null,
            "Film $id must retain movie identity, not candidate TV 320754")
        verify(LONG !in SourceSearchTitles.candidates(film), "No automatic series alias for film $id")
    }
    verify(PlaybackQuery.fromData("ea-fb:episode:320754:-1:8", SHORT, 2026) == null,
        "Negative season must not become a series query; season zero remains valid for specials")
    verify(PlaybackQuery.fromData("ea-fb:episode:320754:1:0", SHORT, 2026) == null,
        "Invalid episode must not become a series query")
    for (land in listOf(false, true)) {
        fun source(page: SeriesPage): Pair<BronzeApiAdapter, SeriesFixture> {
            val fixture = SeriesFixture(if (land) "hdfilmcehennemi.land" else "hdfilmcehennemi.nl", page)
            return (if (land) BronzeLandAdapter { fixture } else BronzeNlAdapter { fixture }) to fixture
        }
        val (shortSource, shortFixture) = source(SeriesPage(LONG))
        verify(shortSource.search(query).size == 1, "Confirmed long alias must now resolve a series offer")
        verify(shortFixture.searches == 1 && shortFixture.loads == 1,
            "Confirmed source result reaches load and exact episode validation")
        val exact = source(SeriesPage(SHORT)).first.search(query).single()
        verify(exact.season == 1 && exact.episode == 8 && exact.tmdbId == CONFIRMED_TMDB,
            "Exact title must preserve catalogue/episode identity")
        verify(source(SeriesPage("Organize Isler")).first.search(query).size == 1,
            "Existing Turkish normalization must work")
        // Explicit query input is an experiment, not proof that TMDb supplies this alias.
        val alias = source(SeriesPage(LONG)).first.search(query.copy(alternateTitles = listOf(LONG)))
        verify(alias.single().playbackData == "opaque-fixture-episode", "Existing supplied-alias path works")
        for (page in listOf(SeriesPage(SHORT, movie = true),
            SeriesPage("Organize İşler: Sazan Sarmalı", movie = true),
            SeriesPage(SHORT, year = 2005), SeriesPage(SHORT, season = 2),
            SeriesPage(SHORT, episode = 7), SeriesPage("Organize İşler Başka Dizi"),
            SeriesPage("İşler"))) {
            verify(source(page).first.search(query).isEmpty(), "Wrong identity must be rejected: $page")
        }
        verify(shortFixture.resolves == 0, "No extractor was invoked")
        println("PASS diagnosis ${shortSource.id}: confirmed short/long series identity accepted; exact episode retained")
    }

    for (box in listOf(false, true)) {
        val base = if (box) "https://www.dizibox.live" else "https://www.diziyou.one"
        val seriesPath = if (box) "/diziler/fixture/" else "/fixture/"
        var sourceName = LONG
        var season = 1
        var episode = 8
        var seriesLoads = 0
        var extractorCalls = 0
        app.handler = { req ->
            val body = when {
                box && req.url.contains("action=dwls_search") ->
                    """{"results":[{"post_title":"$sourceName","permalink":"$base$seriesPath"}]}"""
                !box && req.method == "POST" ->
                    "<div id='searchelement'><a href='$seriesPath'>Image</a><a>$sourceName</a></div>"
                req.url == "$base$seriesPath" -> {
                    seriesLoads++
                    val identity = "<h1>$sourceName</h1><script type='application/ld+json'>" +
                        JSONObject().put("@type", "TVSeries").put("name", sourceName).put("startDate", "2026")
                            .put("url", "$base$seriesPath").toString() + "</script>"
                    if (box) "<div class='tv-overview'>$identity</div><article class='grid-box'><a href='/fixture-$season-sezon-$episode-bolum-izle/'>$season. Sezon $episode. Bölüm</a></article>"
                    else identity + "<a href='/fixture-episode/'><div class='bolumust'><div class='baslik'>$season. Sezon $episode. Bölüm</div></div></a>"
                }
                else -> ""
            }
            FixtureResponse(body, req.url)
        }
        FixtureExtractor.handler = { _, _, _, _ -> extractorCalls++; false }
        val source: MediaSourceAdapter = if (box) DiziBoxAdapter() else DiziYouAdapter()
        verify(source.search(query).size == 1, "Confirmed short query now finds verified long series page")
        verify(seriesLoads > 0, "New alias is corroborated on the series page before returning an offer")
        verify(source.search(query.copy(alternateTitles = listOf(LONG))).single().tmdbId == CONFIRMED_TMDB,
            "Supplied exact alias works without source code changes")
        sourceName = "Organize Isler"
        verify(source.search(query).size == 1, "Existing Turkish normalization preserved")
        sourceName = SHORT
        verify(source.search(query).size == 1, "Existing exact-name path preserved")
        if (!box) {
            season = 2
            verify(source.search(query).isEmpty(), "DiziYou wrong season rejected")
            season = 1; episode = 7
            verify(source.search(query).isEmpty(), "DiziYou wrong episode rejected")
        } else {
            // DiziBox validates episodes later in resolve; search is not playback proof.
            season = 2
            val offer = source.search(query).single()
            verify(offer.season == 1 && offer.episode == 8, "DiziBox offer retains requested identity")
            verify(source.resolve(offer).isEmpty(), "Wrong season fixture has no playable requested episode")
            season = 1; episode = 7
            verify(source.resolve(source.search(query).single()).isEmpty(), "Wrong episode fixture has no playable requested episode")
        }
        sourceName = "Organize İşler: Sazan Sarmalı"
        verify(source.search(query).isEmpty(), "Different suffix must not be accepted by substring")
        verify(extractorCalls == 0, "Diagnostic fixtures produce no stream links")
        println("PASS diagnosis ${source.id}: confirmed alias validates TV/year/page identity; existing normalization preserved")
    }
    println("PASS: $assertions V83 diagnostic assertions; recorded live catalogue/source metadata, synthetic adapter responses; no stream or device validation")
}
