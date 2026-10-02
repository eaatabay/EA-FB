package com.eafb

private class GateAdapter(override val id: String) : MediaSourceAdapter {
    override suspend fun search(query: MediaQuery): List<MediaOffer> = emptyList()
    override suspend fun resolve(offer: MediaOffer): List<SourceLink> = emptyList()
}

fun main() {
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
    println("PASS: fail-closed source gate and V49 identity compatibility")
}
