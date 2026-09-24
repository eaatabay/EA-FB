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
    val badSeries = goodA.copy(kind = MediaKind.SERIES, pageUrl = "https://a.test/tv")
    val hd = SourceLink("alpha", "https://a.test/hls", 1080, "tr", null)
    val alternateAudio = hd.copy(audioLanguage = "en")
    val tooLarge = SourceLink("beta", "https://b.test/4k", 2160, "tr", null)
    val expired = hd.copy(url = "https://a.test/expired", expiresAtMillis = 999)
    val adapterA = TestAdapter("alpha", listOf(goodA, bad, badSeries), listOf(hd, alternateAudio, expired))
    val adapterB = TestAdapter("beta", listOf(goodB), listOf(tooLarge))
    val failed = TestAdapter("broken", emptyList(), fail = true)
    val slow = TestAdapter("slow", listOf(goodA.copy(providerId = "slow")), delayMs = 500)
    val engine = MultiSourceEngine(listOf(adapterA, adapterB, failed, slow), maxConcurrent = 4, perAdapterTimeoutMs = 200)
    val offers = engine.find(q)
    check(offers.size == 2 && offers.map { it.providerId }.toSet() == setOf("alpha", "beta"))
    check(engine.resolve(offers, nowMillis = 1000).map { it.url }.toSet() == setOf(hd.url, tooLarge.url))
    val links = engine.resolve(offers, nowMillis = 1000)
    check(links.size == 3)
    check(links.first() == hd)
    check(Identity.mediaKey(MediaKind.MOVIE, "İSYAN", 2026) == Identity.mediaKey(MediaKind.MOVIE, "Isyan", 2026))
    check(Identity.mediaKey(MediaKind.MOVIE, "İsyan", 2025) != Identity.mediaKey(MediaKind.MOVIE, "İsyan", 2026))
    check(SourcePicker.preferred(listOf(tooLarge, hd), 1000).first() == hd)
    check(runCatching { MultiSourceEngine(listOf(adapterA, adapterA)) }.isFailure)
    check(runCatching { MultiSourceEngine(listOf(adapterA), maxConcurrent = 0) }.isFailure)
    println("PASS: 9/9 source-engine assertions")
}
