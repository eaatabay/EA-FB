package com.eafb

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

internal open class BronzeFixture(private val domain: String, private val land: Boolean) : MainAPI() {
    val searches = mutableListOf<String>()
    val payloads = mutableListOf<String>()
    var delayed = false
    var broken = false
    var resolveBroken = false
    override suspend fun search(query: String): List<SearchResponse> {
        searches += query
        if (broken) throw NoClassDefFoundError("fixture runtime dependency")
        return if (query == "Original Show") listOf(
            SearchResponse("Wrong Show", "https://$domain/wrong/"),
            SearchResponse("Original Show", "https://$domain/dizi/original/"),
            SearchResponse("Original Show", "https://evil.example/dizi/original/"))
        else if (query == "Film") listOf(SearchResponse("Film", "https://$domain/film/movie/")) else emptyList()
    }
    override suspend fun load(url: String): LoadResponse = if (url.contains("/film/"))
        MovieLoadResponse("Film", 2026, "opaque-film-data")
    else TvSeriesLoadResponse("Original Show", 2026, listOf(
        Episode("wrong-season", 2, 2), Episode("wrong-episode", 1, 3),
        Episode("opaque-correct-data", 1, 2)))
    override suspend fun loadLinks(data: String, isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        if (resolveBroken) throw NoClassDefFoundError("fixture resolver dependency")
        payloads += data
        check(!isCasting && data in listOf("opaque-correct-data", "opaque-film-data"))
        val url = if (land) "http://127.0.0.1:43127/master_fixture_dublaj.m3u8" else "https://cdn.example/extensionless"
        callback(ExtractorLink(url, "Reference", 0, "https://$domain/", mapOf(
            "User-Agent" to "fixture-UA", "Origin" to "https://$domain", "Referer" to "https://player.example/", "X-Fixture" to "kept"), ExtractorLinkType.M3U8))
        subtitleCallback(SubtitleFile("Türkçe", "https://cdn.example/sub.vtt", mapOf("User-Agent" to "subtitle-UA")))
        if (delayed) delay(500)
        return true
    }
}

fun main() = runBlocking {
    val query = MediaQuery("Türkçe Ad", 2026, MediaKind.SERIES, 42, 1, 2, listOf("Original Show"))
    for (land in listOf(false, true)) {
        val fixture = BronzeFixture(if (land) "hdfilmcehennemi.land" else "hdfilmcehennemi.nl", land)
        val adapter = if (land) BronzeLandAdapter { fixture } else BronzeNlAdapter { fixture }
        val offers = adapter.search(query)
        check(offers.single().playbackData == "opaque-correct-data")
        check(offers.single().season == 1 && offers.single().episode == 2)
        check(fixture.searches == listOf("Türkçe Ad", "Original Show"))
        val engine = MultiSourceEngine(listOf(adapter), perAdapterTimeoutMs = 100)
        val links = engine.resolve(offers, 1000)
        check(links.size == 1 && links.single().isHls)
        check(links.single().isLandLoopback == land)
        check(links.single().displayName == "${if (land) "HDFilmCehennemi LAND" else "HDFilmCehennemi"} • Reference")
        check(links.single().referer == "https://player.example/")
        check(links.single().headers["User-Agent"] == "fixture-UA")
        check(links.single().headers["Origin"]?.startsWith("https://hdfilmcehennemi.") == true)
        check(links.single().subtitles.single().headers["User-Agent"] == "subtitle-UA")
        check(PlaybackSourceList.group(links, 1000).single().links.size == 1)
        fixture.delayed = true
        check(engine.resolve(offers, 1000).single().url == links.single().url)
        fixture.delayed = false
        val film = adapter.search(MediaQuery("Film", 2026, MediaKind.MOVIE, 43))
        check(film.single().playbackData == "opaque-film-data")
        check(engine.resolve(film, 1000).size == 1)
        check(adapter.search(query.copy(year = 1990)).isEmpty())
        check(adapter.search(query.copy(season = 9)).isEmpty())
        fixture.resolveBroken = true
        check(engine.resolve(film, 1000).isEmpty())
        fixture.broken = true
        check(adapter.search(query).isEmpty())
        println("PASS: ${if (land) "LAND" else "NL"} separate wrapper regressions")
    }
    val unavailable = BronzeFixture("hdfilmcehennemi.nl", false).apply { broken = true }
    val available = BronzeFixture("hdfilmcehennemi.land", true)
    val mixed = MultiSourceEngine(listOf(BronzeNlAdapter { unavailable }, BronzeLandAdapter { available }))
    check(mixed.resolve(mixed.find(query), 1000).single().provider == "hdfilmcehennemi-land")
    val accepted = SourceLink("hdfilmcehennemi-land", "http://127.0.0.1:43127/master_test.m3u8", null, null, null,
        isHls = true, isLandLoopback = true)
    check(SourceLinkPolicy.isPlaybackUrl(accepted))
    for (bad in listOf("http://localhost:43127/master_test.m3u8", "http://127.0.0.1:43127/other.m3u8",
        "http://192.168.1.2:43127/master_test.m3u8", "http://127.0.0.1:43127/master_test.m3u8?x=1"))
        check(!SourceLinkPolicy.isPlaybackUrl(accepted.copy(url = bad)))
    check(!SourceLinkPolicy.isPlaybackUrl(accepted.copy(provider = "dizibox")))
    check(!SourceLinkPolicy.isPlaybackUrl(accepted.copy(isLandLoopback = false)))
    check(!SourceLinkPolicy.isPlaybackUrl(accepted.copy(isHls = false)))
    println("PASS: NL/LAND API delegation, movie/episode identity, metadata, late subtitles, partial timeout, runtime isolation and narrow LAND loopback policy")
}
