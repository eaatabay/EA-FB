package com.eafb

import kotlinx.coroutines.runBlocking

private class GateAdapter(override val id: String) : MediaSourceAdapter {
    override suspend fun search(query: MediaQuery): List<MediaOffer> = emptyList()
    override suspend fun resolve(offer: MediaOffer): List<SourceLink> = emptyList()
}

fun main() = runBlocking {
    val adapters = listOf(GateAdapter("approved"), GateAdapter("disabled"),
        GateAdapter("unhealthy"), GateAdapter("fixture-test"), GateAdapter("unknown"))
    val allowed = PlaybackSourceGate.permitted(adapters,
        setOf("approved", "disabled", "unhealthy", "fixture-test"),
        setOf("approved", "unhealthy", "fixture-test"),
        setOf("approved", "disabled", "fixture-test"))
    check(allowed.map { it.id } == listOf("approved"))
    check(PlaybackSourceGate.permitted(adapters, emptySet(),
        setOf("approved"), setOf("approved")).isEmpty())
    check(PlaybackSourceGate.permitted(adapters, setOf("approved"),
        emptySet(), setOf("approved")).isEmpty())
    check(PlaybackSourceGate.permitted(adapters, setOf("approved"),
        setOf("approved"), emptySet()).isEmpty())
    check(PlaybackSourceGate.query("ea-fb:movie:42", "İsyan", 2026) ==
        MediaQuery("İsyan", 2026, MediaKind.MOVIE, 42))
    check(PlaybackSourceGate.query("ea-fb:episode:42:3:2", "İsyan", 2026) ==
        MediaQuery("İsyan", 2026, MediaKind.SERIES, 42, 3, 2))
    check(PlaybackSourceGate.query("ea-fb:open:big-buck-bunny", "Bunny", 2008) == null)
    check(PlaybackSourceGate.query("ea-fb:live:abc", "Canlı", null) == null)
    check(PlaybackSourceGate.query("ea-fb:episode:42:3:0", "İsyan", 2026) == null)
    val movie = "ea-fb:movie:42"
    val valid = MediaOffer("approved", "Approved", "İsyan", 2026,
        MediaKind.MOVIE, "https://approved.example/movie/42", 42)
    val fallback = valid.copy(providerId = "backup", providerTitle = "Backup",
        pageUrl = "https://backup.example/movie/42")
    val wrong = valid.copy(tmdbId = 99,
        pageUrl = "https://approved.example/movie/99")
    val playable = SourceLink("backup", "https://backup.example/film.m3u8",
        1080, "tr", null)
    val active = object : MediaSourceAdapter {
        override val id = "approved"
        override suspend fun search(query: MediaQuery) = listOf(valid, wrong)
        override suspend fun resolve(offer: MediaOffer): List<SourceLink> = emptyList()
    }
    val backup = object : MediaSourceAdapter {
        override val id = "backup"
        override suspend fun search(query: MediaQuery) = listOf(fallback)
        override suspend fun resolve(offer: MediaOffer) = listOf(playable)
    }
    val all = listOf(active, backup)
    suspend fun links(rights: Set<String>, enabled: Set<String>,
        healthy: Set<String>, data: String = movie) =
        PlaybackSourceGate.resolve(data, "İsyan", 2026, all, rights, enabled,
            healthy, 1000, listOf("approved", "backup"))
    check(links(setOf("approved", "backup"), setOf("approved", "backup"),
        setOf("approved", "backup")) == listOf(playable))
    check(links(setOf("approved"), setOf("approved", "backup"),
        setOf("approved", "backup")).isEmpty())
    check(links(setOf("approved", "backup"), setOf("approved"),
        setOf("approved", "backup")).isEmpty())
    check(links(setOf("approved", "backup"), setOf("approved", "backup"),
        setOf("approved")).isEmpty())
    check(links(emptySet(), setOf("approved", "backup"),
        setOf("approved", "backup")).isEmpty())
    check(links(setOf("approved", "backup"), setOf("approved", "backup"),
        setOf("approved", "backup"), "ea-fb:episode:42:3:2").isEmpty())
    check(links(setOf("approved", "backup"), setOf("approved", "backup"),
        setOf("approved", "backup"), "ea-fb:live:test").isEmpty())
    val alternatives = PlaybackSourceGate.alternatives(movie, "İsyan", 2026,
        all, setOf("approved", "backup"), setOf("approved", "backup"),
        setOf("approved", "backup"), 1000)
    check(alternatives == listOf(playable))
    check(PlaybackSourceGate.alternatives(movie, "İsyan", 2026,
        all, setOf("approved", "backup"), setOf("approved"),
        setOf("approved", "backup"), 1000).isEmpty())
    check(PlaybackSourceGate.alternatives("ea-fb:live:test", "İsyan", 2026,
        all, setOf("approved", "backup"), setOf("approved", "backup"),
        setOf("approved", "backup"), 1000).isEmpty())
    println("PASS: fail-closed source gate, exact identity and approved fallback")
}
