package com.eafb

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

private class TestAdapter(
    override val id: String,
    val entries: List<MediaOffer>,
    val links: List<SourceLink> = emptyList(),
    val fail: Boolean = false,
    val delayMs: Long = 0
) : MediaSourceAdapter {
    override suspend fun search(query: MediaQuery): List<MediaOffer> {
        if (delayMs > 0) delay(delayMs)
        if (fail) error("Temporary source failure")
        return entries
    }
    override suspend fun resolve(offer: MediaOffer): List<SourceLink> {
        if (delayMs > 0) delay(delayMs)
        if (fail) error("Temporary source failure")
        return links
    }
}

fun main() = runBlocking {
    val q = MediaQuery("İsyan", 2026, MediaKind.MOVIE, 42)
    val goodA = MediaOffer("alpha", "A", "İsyan", 2026, MediaKind.MOVIE, "https://a.test/title/42", 42)
    val goodB = MediaOffer("beta", "B", "Isyan", 2026, MediaKind.MOVIE, "https://b.test/title/42")
    val bad = MediaOffer("alpha", "A", "İsyan", 1990, MediaKind.MOVIE, "https://a.test/another")
    val insecure = goodA.copy(pageUrl = "http://a.test/title/42")
    val badSeries = goodA.copy(kind = MediaKind.SERIES, pageUrl = "https://a.test/tv")
    val contradicted = goodA.copy(year = 1990, pageUrl = "https://a.test/incorrect-year")
    val wrongId = goodA.copy(tmdbId = 99, pageUrl = "https://a.test/incorrect-id")
    val hd = SourceLink("alpha", "https://a.test/hls", 1080, "tr", null)
    val alternateAudio = hd.copy(audioLanguage = "en")
    val tooLarge = SourceLink("beta", "https://b.test/4k", 2160, "tr", null)
    val expired = hd.copy(url = "https://a.test/expired", expiresAtMillis = 999)
    val adapterA = TestAdapter("alpha", listOf(goodA, bad, badSeries, contradicted, wrongId, insecure), listOf(hd, alternateAudio, expired))
    val adapterB = TestAdapter("beta", listOf(goodB), listOf(tooLarge))
    val failed = TestAdapter("broken", emptyList(), fail = true)
    val slow = TestAdapter("slow", listOf(goodA.copy(providerId = "slow")), delayMs = 500)
    val engine = MultiSourceEngine(listOf(adapterA, adapterB, failed, slow), maxConcurrent = 4, perAdapterTimeoutMs = 200)
    val offers = engine.find(q)
    check(offers.size == 2 && offers.map { it.providerId }.toSet() == setOf("alpha", "beta"))
    check(contradicted !in offers)
    check(wrongId !in offers)
    check(insecure !in offers)
    check(engine.resolve(offers, nowMillis = 1000).map { it.url }.toSet() == setOf(hd.url, tooLarge.url))
    val links = engine.resolve(offers, nowMillis = 1000)
    check(links.size == 3)
    check(links.first() == hd)
    check(Identity.mediaKey(MediaKind.MOVIE, "İSYAN", 2026) == Identity.mediaKey(MediaKind.MOVIE, "Isyan", 2026))
    check(Identity.mediaKey(MediaKind.MOVIE, "İsyan", 2025) != Identity.mediaKey(MediaKind.MOVIE, "İsyan", 2026))
    check(SourcePicker.preferred(listOf(tooLarge, hd), 1000).first() == hd)
    check(runCatching { MultiSourceEngine(listOf(adapterA, adapterA)) }.isFailure)
    check(runCatching { MultiSourceEngine(listOf(adapterA), maxConcurrent = 0) }.isFailure)
    val epQuery = MediaQuery("İsyan", 2026, MediaKind.SERIES, 42, 3, 2)
    val exactEpisode = MediaOffer("alpha", "A", "İsyan", 2026,
        MediaKind.SERIES, "https://a.test/tv/42/s3e2", 42, 3, 2)
    val otherEpisode = exactEpisode.copy(episode = 3,
        pageUrl = "https://a.test/tv/42/s3e3")
    val noTmdb = exactEpisode.copy(tmdbId = null,
        pageUrl = "https://a.test/tv/unknown/s3e2")
    val wrongTmdb = exactEpisode.copy(tmdbId = 99,
        pageUrl = "https://a.test/tv/99/s3e2")
    val showOnly = exactEpisode.copy(season = null, episode = null,
        pageUrl = "https://a.test/tv/42")
    val episodeEngine = MultiSourceEngine(listOf(
        TestAdapter("alpha", listOf(exactEpisode, otherEpisode, showOnly,
            noTmdb, wrongTmdb))))
    check(episodeEngine.find(epQuery) == listOf(exactEpisode))
    check(runCatching {
        MediaQuery("İsyan", 2026, MediaKind.SERIES, 42, 3, null)
    }.isFailure)
    check(runCatching {
        MediaOffer("alpha", "A", "İsyan", 2026,
            MediaKind.MOVIE, "https://a.test/movie/42", 42, 3, 2)
    }.isFailure)
    val fallbackA = TestAdapter("source-a", listOf(
        exactEpisode.copy(providerId = "source-a")), links = emptyList())
    val fallbackB = TestAdapter("source-b", listOf(
        exactEpisode.copy(providerId = "source-b")), links = listOf(
        SourceLink("source-b", "https://licensed.example/episode.m3u8",
            1080, "tr", null)))
    val fallbackEngine = MultiSourceEngine(listOf(fallbackA, fallbackB))
    val fallbackOffers = listOf(
        exactEpisode.copy(providerId = "source-b"),
        exactEpisode.copy(providerId = "source-a"))
    check(fallbackEngine.resolveFirstAvailable(epQuery, fallbackOffers, 1000,
        listOf("source-a", "source-b")).single().provider == "source-b")
    check(fallbackEngine.resolveFirstAvailable(epQuery, fallbackOffers, 1000,
        listOf("source-b", "source-a")).single().provider == "source-b")
    check(fallbackEngine.resolveFirstAvailable(epQuery,
        listOf(otherEpisode.copy(providerId = "source-b")), 1000,
        listOf("source-b")).isEmpty())
    check(fallbackEngine.resolveFirstAvailable(epQuery,
        listOf(exactEpisode.copy(providerId = "unknown")), 1000,
        listOf("unknown")).isEmpty())
    val throwingFirst = TestAdapter("source-a", emptyList(), fail = true)
    val failoverEngine = MultiSourceEngine(listOf(throwingFirst, fallbackB))
    check(failoverEngine.resolveFirstAvailable(epQuery, fallbackOffers, 1000,
        listOf("source-a", "source-b")).single().provider == "source-b")
    val slowFirst = TestAdapter("source-a", emptyList(), delayMs = 300)
    val timeoutEngine = MultiSourceEngine(listOf(slowFirst, fallbackB),
        perAdapterTimeoutMs = 100)
    check(timeoutEngine.resolveFirstAvailable(epQuery, fallbackOffers, 1000,
        listOf("source-a", "source-b")).single().provider == "source-b")
    val expiredFirst = TestAdapter("source-a", emptyList(), links = listOf(
        SourceLink("source-a", "https://licensed.example/expired.m3u8",
            1080, "tr", null, expiresAtMillis = 999)))
    val expiredEngine = MultiSourceEngine(listOf(expiredFirst, fallbackB))
    check(expiredEngine.resolveFirstAvailable(epQuery, fallbackOffers, 1000,
        listOf("source-a", "source-b")).single().provider == "source-b")
    val mismatchedFirst = TestAdapter("source-a", emptyList(), links = listOf(
        SourceLink("unrelated", "https://licensed.example/foreign.m3u8",
            1080, "tr", null)))
    val mismatchedEngine = MultiSourceEngine(listOf(mismatchedFirst, fallbackB))
    check(mismatchedEngine.resolveFirstAvailable(epQuery, fallbackOffers, 1000,
        listOf("source-a", "source-b")).single().provider == "source-b")
    val insecureOffer = exactEpisode.copy(
        providerId = "source-b", pageUrl = "http://untrusted.example/episode")
    check(fallbackEngine.resolveFirstAvailable(
        epQuery, listOf(insecureOffer), 1000).isEmpty())
    check(fallbackEngine.resolve(
        listOf(insecureOffer), 1000).isEmpty())
    check(fallbackEngine.resolve(
        listOf(insecureOffer, fallbackOffers.first()), 1000) == listOf(
            SourceLink("source-b", "https://licensed.example/episode.m3u8",
                1080, "tr", null)))
    check(runCatching {
        fallbackEngine.resolveFirstAvailable(epQuery, fallbackOffers, -1)
    }.isFailure)
    println("PASS: source-engine + exact-episode assertions")
}
