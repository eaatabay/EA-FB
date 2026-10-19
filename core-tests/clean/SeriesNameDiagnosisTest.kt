package com.eafb

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.FixtureExtractor
import kotlinx.coroutines.runBlocking

/** Synthetic observations of unchanged V83; these titles are NOT an alias registry. */
private const val SHORT = "Organize İşler"
private const val LONG = "Organize İşler: Karun Hazinesi"
private const val SYNTHETIC_TMDB = 987654321
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
    val query = MediaQuery(SHORT, 2026, MediaKind.SERIES, SYNTHETIC_TMDB, 1, 8)
    var assertions = 0
    fun verify(value: Boolean, message: String) { check(value) { message }; assertions++ }
    for (land in listOf(false, true)) {
        fun source(page: SeriesPage): Pair<BronzeApiAdapter, SeriesFixture> {
            val fixture = SeriesFixture(if (land) "hdfilmcehennemi.land" else "hdfilmcehennemi.nl", page)
            return (if (land) BronzeLandAdapter { fixture } else BronzeNlAdapter { fixture }) to fixture
        }
        val (shortSource, shortFixture) = source(SeriesPage(LONG))
        verify(shortSource.search(query).isEmpty(), "V83 must reject unprovided long alias")
        verify(shortFixture.searches == 1 && shortFixture.loads == 0,
            "Synthetic result exists but V83 rejects search title BEFORE load/episode/extractor")
        val exact = source(SeriesPage(SHORT)).first.search(query).single()
        verify(exact.season == 1 && exact.episode == 8 && exact.tmdbId == SYNTHETIC_TMDB,
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
        println("PASS diagnosis ${shortSource.id}: synthetic short/long mismatch is search-title rejection; supplied alias works")
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
                    if (box) "<article class='grid-box'><a href='/fixture-$season-sezon-$episode-bolum-izle/'>$season. Sezon $episode. Bölüm</a></article>"
                    else "<a href='/fixture-episode/'><div class='bolumust'><div class='baslik'>$season. Sezon $episode. Bölüm</div></div></a>"
                }
                else -> ""
            }
            FixtureResponse(body, req.url)
        }
        FixtureExtractor.handler = { _, _, _, _ -> extractorCalls++; false }
        val source: MediaSourceAdapter = if (box) DiziBoxAdapter() else DiziYouAdapter()
        verify(source.search(query).isEmpty(), "V83 short query must reject unprovided long alias")
        verify(seriesLoads == 0, "Rejection precedes series page and extractor")
        verify(source.search(query.copy(alternateTitles = listOf(LONG))).single().tmdbId == SYNTHETIC_TMDB,
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
        println("PASS diagnosis ${source.id}: title gate isolated; supplied alias and existing normalization preserved")
    }
    println("PASS: $assertions V83 diagnostic assertions; synthetic responses only, no live TMDb/source/device validation")
}
