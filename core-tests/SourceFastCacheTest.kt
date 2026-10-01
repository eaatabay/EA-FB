package com.eafb

fun main() {
    val cache = SourceFastCache(ttlMillis = 10_000, capacity = 2)
    val movie = SourceFastCache.Key(MediaKind.MOVIE, 42)
    val episode1 = SourceFastCache.Key(MediaKind.SERIES, 42, 1, 1)
    val episode2 = SourceFastCache.Key(MediaKind.SERIES, 42, 1, 2)
    val dubbed = SourceLink("approved", "https://example.org/movie.mp4", 1080, "tr", null)
    val original = dubbed.copy(url = "https://example.org/original.mp4", audioLanguage = "en")
    val private = dubbed.copy(url = "https://example.org/session.mp4", requiresPrivateSession = true)
    val expired = dubbed.copy(url = "https://example.org/expired.mp4", expiresAtMillis = 1_050)
    val shortLived = dubbed.copy(url = "https://example.org/short.mp4", expiresAtMillis = 1_500)
    val insecure = dubbed.copy(url = "http://example.org/movie.mp4")
    cache.put(movie, listOf(original, dubbed, private, expired, shortLived, insecure), 1_000)
    check(cache.get(movie, 1_100).map { it.url } == listOf(dubbed.url, shortLived.url, original.url))
    check(cache.get(movie, 1_600).map { it.url } == listOf(dubbed.url, original.url))
    check(cache.get(episode1, 1_600).isEmpty())
    cache.put(episode1, listOf(dubbed), 1_700)
    check(cache.get(episode1, 1_800).size == 1)
    cache.put(episode2, listOf(dubbed), 1_900)
    check(cache.get(movie, 2_000).isEmpty()) // LRU capacity
    check(cache.get(episode1, 2_000).size == 1)
    check(cache.get(episode2, 12_000).isEmpty()) // TTL
    cache.invalidate(episode1)
    check(cache.get(episode1, 2_100).isEmpty())
    check(runCatching { SourceFastCache.Key(MediaKind.SERIES, 42) }.isFailure)
    check(runCatching { SourceFastCache.Key(MediaKind.MOVIE, 42, 1, 1) }.isFailure)
    check(runCatching { SourceFastCache(ttlMillis = 0) }.isFailure)
    check(runCatching { SourceFastCache(capacity = 0) }.isFailure)
    println("PASS: 12/12 source fast-cache assertions")
}
