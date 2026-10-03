package com.eafb

import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    check(!PlaybackLinkBridge.canResolve("ea-fb:movie:42"))
    check(!PlaybackLinkBridge.canResolve("ea-fb:episode:42:3:2"))
    check(!PlaybackLinkBridge.canResolve("ea-fb:live:abc"))
    check(PlaybackLinkBridge.isCatalogIdentity("ea-fb:movie:42"))
    check(PlaybackLinkBridge.isCatalogIdentity("ea-fb:episode:42:3:2"))
    check(!PlaybackLinkBridge.isCatalogIdentity("ea-fb:episode:42:3:0"))
    check(!PlaybackLinkBridge.isCatalogIdentity("ea-fb:live:abc"))
    check(!PlaybackLinkBridge.isCatalogIdentity("ea-fb:open:big-buck-bunny"))
    check(PlaybackLinkBridge.alternatives(
        "ea-fb:movie:42", "", null, 1000).isEmpty())
    check(PlaybackLinkBridge.alternatives(
        "ea-fb:episode:42:3:2", "İsyan", 2026, 1000).isEmpty())
    check(PlaybackLinkBridge.alternatives(
        "ea-fb:live:abc", "Canlı", null, 1000).isEmpty())
    val offer = MediaOffer("licensed", "Licensed", "İsyan", 2026,
        MediaKind.MOVIE, "https://licensed.example/movie/42", 42)
    val link = SourceLink("licensed", "https://licensed.example/movie/42.m3u8",
        1080, "tr", null)
    val adapter = object : MediaSourceAdapter {
        override val id = "licensed"
        override suspend fun search(query: MediaQuery): List<MediaOffer> = listOf(offer)
        override suspend fun resolve(offer: MediaOffer): List<SourceLink> = listOf(link)
    }
    fun runtime(rights: Set<String>, enabled: Set<String>, healthy: Set<String>) =
        PlaybackSourceRuntime(listOf(adapter), rights, enabled, healthy)
    val allowed = runtime(setOf("licensed"), setOf("licensed"), setOf("licensed"))
    check(allowed.canResolve("ea-fb:movie:42"))
    check(allowed.alternatives("ea-fb:movie:42", "İsyan", 2026, 1000) == listOf(link))
    check(allowed.alternatives("ea-fb:episode:42:3:2", "İsyan", 2026, 1000).isEmpty())
    check(!runtime(emptySet(), setOf("licensed"), setOf("licensed"))
        .canResolve("ea-fb:movie:42"))
    check(!runtime(setOf("licensed"), emptySet(), setOf("licensed"))
        .canResolve("ea-fb:movie:42"))
    check(!runtime(setOf("licensed"), setOf("licensed"), emptySet())
        .canResolve("ea-fb:movie:42"))
    val mutableAdapters = mutableListOf<MediaSourceAdapter>(adapter)
    val mutableRights = mutableSetOf("licensed")
    val mutableEnabled = mutableSetOf("licensed")
    val mutableHealthy = mutableSetOf("licensed")
    val snapshot = PlaybackSourceRuntime(
        mutableAdapters, mutableRights, mutableEnabled, mutableHealthy
    )
    mutableAdapters.clear()
    mutableRights.clear()
    mutableEnabled.clear()
    mutableHealthy.clear()
    check(snapshot.canResolve("ea-fb:movie:42"))
    check(snapshot.alternatives("ea-fb:movie:42", "İsyan", 2026, 1000) == listOf(link))
    val freshRuntime = PlaybackSourceRuntime.fromHealthObservations(
        listOf(adapter), setOf("licensed"), setOf("licensed"),
        listOf(SourceHealthObservation("licensed", true, 1000)), 1050, 100)
    check(freshRuntime.canResolve("ea-fb:movie:42"))
    val expiredRuntime = PlaybackSourceRuntime.fromHealthObservations(
        listOf(adapter), setOf("licensed"), setOf("licensed"),
        listOf(SourceHealthObservation("licensed", true, 1000)), 1100, 100)
    check(!expiredRuntime.canResolve("ea-fb:movie:42"))
    val revokedRuntime = PlaybackSourceRuntime.fromHealthObservations(
        listOf(adapter), emptySet(), setOf("licensed"),
        listOf(SourceHealthObservation("licensed", true, 1000)), 1050, 100)
    check(!revokedRuntime.canResolve("ea-fb:movie:42"))
    check(runCatching {
        PlaybackSourceRuntime(listOf(adapter, adapter), setOf("licensed"),
            setOf("licensed"), setOf("licensed"))
    }.isFailure)
    // PLT-style list: one row per site, ClipBox is always the final row.
    val ordinary = SourceLink("dizibox", "https://example.org/a.m3u8", 720, "tr", null)
    val clipA = SourceLink("clipbox", "https://example.org/clip-a.m3u8", 1080, "tr", null)
    val clipB = SourceLink("clipbox", "https://example.org/clip-b.m3u8", 720, "tr", null)
    val grouped = PlaybackSourceList.group(listOf(clipA, ordinary, clipB), 1000)
    check(grouped.map { it.providerId } == listOf("dizibox", "clipbox"))
    check(grouped.last().links.size == 2)
    val upperClip = clipA.copy(provider = "ClipBox", url = "https://example.org/clip-c.m3u8")
    val mixedCase = PlaybackSourceList.group(listOf(upperClip, ordinary, clipB), 1000)
    check(mixedCase.map { it.providerId } == listOf("dizibox", "clipbox"))
    check(mixedCase.last().links.size == 2)
    val upperSite = ordinary.copy(provider = "DiziBox", url = "https://example.org/b.m3u8")
    val mixedSites = PlaybackSourceList.group(listOf(ordinary, upperSite, clipA), 1000)
    check(mixedSites.map { it.providerId } == listOf("dizibox", "clipbox"))
    check(mixedSites.first().links.size == 2)
    check(PlaybackSourceList.group(listOf(ordinary, ordinary), 1000).size == 1)
    check(allowed.sourceGroups("ea-fb:movie:42", "İsyan", 2026, 1000)
        .map { it.providerId } == listOf("licensed"))
    check(PlaybackLinkBridge.sourceGroups("ea-fb:movie:42", "İsyan", 2026, 1000)
        .isEmpty())
    println("PASS: V49 catalog bridge fails closed without live grants")
}
