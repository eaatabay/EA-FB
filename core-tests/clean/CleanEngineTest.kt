package com.eafb

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val query = MediaQuery("Show", 2026, MediaKind.SERIES, 42, 1, 1)
    val offer = MediaOffer("test", "Test", "Show", 2026, query.kind, "https://test.example/episode", 42, 1, 1)
    val adaptive = SourceLink("test", "https://test.example/master", 2160, null, null,
        isHls = true, isAdaptive = true, headers = mapOf("Referer" to "https://test.example/"))
    val fixed = adaptive.copy(url = "https://test.example/2160.mp4", isHls = false, isAdaptive = false)
    val adapter = object : ProgressiveMediaSourceAdapter {
        override val id = "test"
        override suspend fun search(query: MediaQuery) = listOf(offer)
        override suspend fun resolveIncrementally(offer: MediaOffer, emit: (SourceLink) -> Unit) {
            emit(adaptive)
            emit(fixed)
            emit(adaptive.copy(provider = "other"))
            emit(adaptive.copy(url = "http://127.0.0.1/master"))
            emit(adaptive.copy(url = "https://test.example/expired", expiresAtMillis = 999))
            emit(adaptive.copy(url = "https://test.example/private", requiresPrivateSession = true))
            delay(500)
        }
    }
    val engine = MultiSourceEngine(listOf(adapter), perAdapterTimeoutMs = 100)
    check(engine.resolve(listOf(offer), 1000) == listOf(adaptive))
    check(engine.resolveFirstAvailable(query, listOf(offer), 1000) == listOf(adaptive))
    check(SourceLinkPolicy.compatibleQuality(adaptive, 720))
    check(!SourceLinkPolicy.compatibleQuality(fixed, 1080))
    val cancelAdapter = object : MediaSourceAdapter {
        override val id = "test"
        override suspend fun search(query: MediaQuery) = listOf(offer)
        override suspend fun resolve(offer: MediaOffer): List<SourceLink> { throw CancellationException("external cancellation") }
    }
    check(runCatching { MultiSourceEngine(listOf(cancelAdapter)).resolve(listOf(offer), 1000) }.exceptionOrNull() is CancellationException)
    println("PASS: adaptive master retained, fixed 4K rejected, partial links survive timeout; external cancellation propagates")
}
