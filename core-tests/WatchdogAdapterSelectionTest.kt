package com.eafb

private const val T = 1_800_000_000_000L
private class SelectionAdapter(
    override val id: String,
    override val adapterVersion: Int,
    private val invalidResult: Boolean = false,
    private val explode: Boolean = false
) : VerifiedBaseUrlAdapter {
    var configured = 0
    val baseUrls = mutableListOf<String>()
    override suspend fun search(query: MediaQuery): List<MediaOffer> = emptyList()
    override suspend fun resolve(offer: MediaOffer): List<SourceLink> = emptyList()
    override fun configuredForVerifiedBaseUrl(baseUrl: String): MediaSourceAdapter {
        configured++
        baseUrls.add(baseUrl)
        if (explode) error("simulated reviewed adapter failure")
        val selectedId = if (invalidResult) "other-adapter" else id
        return object : MediaSourceAdapter {
            override val id = selectedId
            override suspend fun search(query: MediaQuery): List<MediaOffer> = emptyList()
            override suspend fun resolve(offer: MediaOffer): List<SourceLink> = emptyList()
        }
    }
}
private val approved = VerifiedSourceSnapshot(42, T, T + 900_000, listOf(
    SnapshotSource("fixture-movie", "movie", "https://films.example.org", 3),
    SnapshotSource("fixture-series", "series", "https://series.example.org", 2),
    SnapshotSource("fixture-both", "both", "https://both.example.org", 1)
))

fun main() {
    var checked = 0
    fun ok(value: Boolean, label: String) { check(value) { label }; checked++ }
    val movie = SelectionAdapter("fixture-movie", 3)
    val series = SelectionAdapter("fixture-series", 2)
    val both = SelectionAdapter("fixture-both", 1)
    val bundled = listOf(movie, series, both)
    fun selected(kind: MediaKind, now: Long = T,
        snapshot: VerifiedSourceSnapshot? = approved,
        adapters: List<VerifiedBaseUrlAdapter> = bundled) =
        WatchdogAdapterSelection.forNewSearch(snapshot, kind, now, adapters).map { it.id }

    ok(selected(MediaKind.MOVIE) == listOf("fixture-movie", "fixture-both"),
        "movie search uses movie and both only")
    ok(series.configured == 0, "movie search never configures a series-only adapter")
    ok(selected(MediaKind.SERIES) == listOf("fixture-series", "fixture-both"),
        "TV search uses series and both only")
    val beforeLive = bundled.sumOf { it.configured }
    ok(selected(MediaKind.LIVE).isEmpty() &&
        beforeLive == bundled.sumOf { it.configured },
        "live TV is out of scope and configures no external adapter")
    ok(movie.baseUrls == listOf("https://films.example.org"),
        "only the exact verified movie URL reaches configured adapter")
    ok(selected(MediaKind.MOVIE, T + 900_000).isEmpty(),
        "expires at the exact signed TTL")
    ok(selected(MediaKind.MOVIE, snapshot = null).isEmpty(),
        "no signed snapshot means zero external sources")
    ok(selected(MediaKind.MOVIE, adapters = emptyList()).isEmpty(),
        "no preinstalled adapters means zero external sources")
    ok(selected(MediaKind.MOVIE, adapters = listOf(movie, movie)).isEmpty(),
        "duplicate bundled IDs fail closed")
    ok(selected(MediaKind.MOVIE,
        adapters = listOf(SelectionAdapter("fixture-movie", 4))).isEmpty(),
        "wrong bundled version cannot be configured")
    val mismatched = SelectionAdapter("fixture-movie", 3, invalidResult = true)
    ok(selected(MediaKind.MOVIE, adapters = listOf(mismatched)).isEmpty(),
        "adapter returning another identity cannot be activated")
    val broken = SelectionAdapter("fixture-movie", 3, explode = true)
    ok(selected(MediaKind.MOVIE,
        adapters = listOf(broken, both)) == listOf("fixture-both"),
        "one throwing adapter does not activate itself or block another")
    ok(selected(MediaKind.MOVIE, adapters = List(33) {
        SelectionAdapter("test-${it.toString().padStart(3, '0')}", 1)
    }).isEmpty(), "more than 32 bundled adapters rejected")
    ok(selected(MediaKind.MOVIE, snapshot = approved.copy(
        usableSources = listOf(approved.usableSources[1]))).isEmpty(),
        "a series-only signed list never serves movie search")
    ok(selected(MediaKind.SERIES, adapters = listOf(movie)).isEmpty(),
        "movie-only adapter not used for series search")
    println("PASS: $checked/$checked signed-media adapter selection policy")
}
