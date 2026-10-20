package com.eafb

import com.lagradost.cloudstream3.*
import kotlinx.coroutines.*
import org.jsoup.Jsoup
import org.json.JSONObject
import kotlin.system.measureTimeMillis

fun main() = runBlocking {
    var assertions = 0
    fun verify(value: Boolean) { check(value) { "universal assertion ${assertions + 1}" }; assertions++ }
    fun accept(q: MediaQuery, s: SourceIdentity) = SourceTitleMatcher.assess(q, s).accepted
    val ted = MediaQuery("Ted Lasso", 2020, MediaKind.SERIES, 97546, 1, 2)
    val direct = SourceIdentity("Ted Lasso", MediaKind.SERIES, 2020, season = 1, episode = 2)
    verify(accept(ted, direct))
    val organized = MediaQuery("Organize İşler", 2026, MediaKind.SERIES, 320754, 1, 8, seasonTitle = "Karun Hazinesi")
    verify(SourceSearchTitles.candidates(organized).contains("Organize İşler: Karun Hazinesi"))
    verify(accept(organized, SourceIdentity("Organize İşler: Karun Hazinesi", MediaKind.SERIES, 2026, season = 1, episode = 8)))
    verify(!accept(organized, SourceIdentity("Organize İşler", MediaKind.MOVIE, 2005)))
    verify(!accept(organized, SourceIdentity("Organize İşler: Sazan Sarmalı", MediaKind.MOVIE, 2019)))
    // Previously unseen identities: no fixture-specific condition can satisfy this family.
    for (i in 1..80) {
        val q = MediaQuery("Unknown Observatory $i", 1900 + i, MediaKind.SERIES, 700000 + i, 2, 3,
            listOf("Yeni Gözlemevi $i"), "The Archive $i")
        val title = "Unknown Observatory $i: The Archive $i"
        verify(SourceSearchTitles.candidates(q).contains(title))
        verify(accept(q, SourceIdentity(title, MediaKind.SERIES, 1900 + i, season = 2, episode = 3)))
        verify(!accept(q, SourceIdentity(title, MediaKind.SERIES, 1901 + i, season = 2, episode = 3)))
        verify(!accept(q, SourceIdentity(title, MediaKind.SERIES, 1900 + i, season = 1, episode = 3)))
    }
    for (generic in listOf("Season 1", "1. Sezon", "Sezon 2", "Özel Bölümler", "Specials"))
        verify(SourceSearchTitles.candidates(ted.copy(seasonTitle = generic)).none { ':' in it })
    verify(SourceSearchTitles.candidates(ted.copy(seasonTitle = "New Chapter")).any { it.contains("New Chapter") })
    val film = MediaQuery("Spider-Man: Homecoming", 2017, MediaKind.MOVIE, 315635,
        alternateTitles = listOf("Örümcek Adam Eve Dönüş"))
    for (name in listOf("Spider-Man: Homecoming", "Spiderman Homecoming", "Homecoming Spider Man", "Örümcek Adam Eve Dönüş"))
        verify(accept(film, SourceIdentity(name, MediaKind.MOVIE, 2017)))
    for (name in listOf("Spider Man", "Spider-Man 2", "Spider-Man: Far From Home", "Spider-Man Homecoming 2"))
        verify(!accept(film, SourceIdentity(name, MediaKind.MOVIE, 2017)))
    for (bad in listOf(direct.copy(year = 2021), direct.copy(kind = MediaKind.MOVIE), direct.copy(season = 2),
        direct.copy(episode = 3), direct.copy(season = null, episode = null), direct.copy(tmdbId = 12),
        direct.copy(conflicting = true), direct.copy(title = "Ted Lasso Spin Off"))) verify(!accept(ted, bad))
    verify(SourceTitleMatcher.assess(ted, direct.copy(year = null)).level == MatchLevel.AMBIGUOUS)
    verify(!accept(ted.copy(year = null), direct))
    verify(accept(ted, direct.copy(year = null, tmdbId = ted.tmdbId)))
    verify(!accept(ted, direct.copy(year = 2019, tmdbId = ted.tmdbId)))
    val first = ted.copy(seasonTitle = "First Chapter")
    val second = ted.copy(season = 2, seasonTitle = "Second Chapter")
    verify(SourceSearchTitles.candidates(first).none { it.contains("Second Chapter") })
    verify(!accept(second, SourceIdentity("Ted Lasso: First Chapter", MediaKind.SERIES, 2020, season = 2, episode = 2)))
    val visible = Jsoup.parse("<h1>Ted Lasso</h1><div class='tv-overview'>İlk Yayın Yılı: 2020</div>")
    verify(accept(ted, SourcePageIdentity.read(visible, MediaKind.SERIES).copy(season = 1, episode = 2)))
    val schema = Jsoup.parse("""<h1>Ted Lasso</h1><script type='application/ld+json'>{"@type":"TVSeries","name":"Ted Lasso","startDate":"2020-08-14"}</script>""")
    verify(accept(ted, SourcePageIdentity.read(schema, null).copy(season = 1, episode = 2)))
    val conflict = Jsoup.parse("""<h1>Ted Lasso</h1><script type='application/ld+json'>{"@type":"Movie","name":"Different Film","datePublished":"2005"}</script><div class='tv-overview'>Yapım Yılı: 2020</div>""")
    verify(!accept(ted, SourcePageIdentity.read(conflict, MediaKind.SERIES).copy(season = 1, episode = 2)))
    val laterSeason = Jsoup.parse("""<h1>Ted Lasso</h1><script type='application/ld+json'>{"@type":"TVSeries","name":"Ted Lasso","startDate":"2020"}</script><div class='tv-overview'><span class='year'>2026</span></div>""")
    verify(accept(ted, SourcePageIdentity.read(laterSeason, MediaKind.SERIES).copy(season = 1, episode = 2)))
    verify(SourceTitleMatcher.assess(ted, direct).level == MatchLevel.EXACT)
    verify(SourceTitleMatcher.assess(film, SourceIdentity("Örümcek Adam Eve Dönüş", MediaKind.MOVIE, 2017)).level == MatchLevel.VERIFIED_ALIAS)
    verify(SourceTitleMatcher.assess(organized, SourceIdentity("Organize İşler: Karun Hazinesi", MediaKind.SERIES, 2026, season = 1, episode = 8)).level == MatchLevel.CORROBORATED)
    verify(accept(second, SourceIdentity("Ted Lasso: Second Chapter", MediaKind.SERIES, 2020, season = 2, episode = 2)))
    verify(!accept(second, SourceIdentity("Ted Lasso: Second Chapter", MediaKind.SERIES, 2026, season = 2, episode = 2)))
    for (title in listOf("未知の映画 2", "새로운 시리즈 3", "Yeni Film İzle")) {
        val q = MediaQuery(title, 2020, MediaKind.MOVIE)
        verify(accept(q, SourceIdentity(title, MediaKind.MOVIE, 2020)))
    }
    val noYear = Jsoup.parse("<h1>Ted Lasso</h1><article>Episode aired 2025</article><footer>2026</footer>")
    verify(SourcePageIdentity.read(noYear, MediaKind.SERIES).year == null)
    val metadata = JSONObject("""{"original_name":"Original","first_air_date":"2000","seasons":[{"season_number":1,"name":"First"},{"season_number":2,"name":"Second"}],"alternative_titles":{"results":[{"title":"Alias"}]}}""")
    verify(CatalogMatchMetadata.seasonName(metadata, 2) == "Second")
    verify(CatalogMatchMetadata.alternatives(metadata, false) == listOf("Original", "Alias"))
    verify(SourceSearchTitles.candidates(ted.copy(alternateTitles = (1..100).map { "Alias $it" })).size <= 6)
    val offer = MediaOffer("test", "Test", ted.title, ted.year, ted.kind, "https://test/one", ted.tmdbId, 1, 2, sourceIdentity = direct)
    verify(SourceTitleMatcher.unique(listOf(offer, offer)).size == 1)
    verify(SourceTitleMatcher.unique(listOf(offer, offer.copy(pageUrl = "https://test/two"))).isEmpty())
    var calls = 0
    val slow = object : MediaSourceAdapter {
        override val id = "slow"
        override suspend fun search(query: MediaQuery): List<MediaOffer> {
            for (candidate in SourceSearchTitles.candidates(query)) { calls++; delay(80) }
            return emptyList()
        }
        override suspend fun resolve(offer: MediaOffer) = emptyList<SourceLink>()
    }
    val millis = measureTimeMillis { verify(MultiSourceEngine(listOf(slow), perAdapterTimeoutMs = 150).find(first).isEmpty()) }
    verify(calls in 1..2 && millis < 1000)
    println("PASS: $assertions universal candidate/evidence assertions; timeout=${millis}ms calls=$calls; no device playback claim")
}
