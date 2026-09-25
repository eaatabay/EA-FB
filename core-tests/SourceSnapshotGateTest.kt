package com.eafb

private class GateTestAdapter(override val id: String, override val adapterVersion: Int) : VerifiedBaseUrlAdapter {
    override suspend fun search(query: MediaQuery): List<MediaOffer> = emptyList()
    override suspend fun resolve(offer: MediaOffer): List<SourceLink> = emptyList()
    override fun configuredForVerifiedBaseUrl(baseUrl: String): MediaSourceAdapter = this
}
fun main() {
    val now = 1_800_000_000_000L
    val verified = VerifiedSourceSnapshot(42, now, now + 900000,
        listOf(SnapshotSource("licensed-demo", "movie", "https://licensed.example.org", 3)))
    val first = GateTestAdapter("licensed-demo", 3)
    val old = GateTestAdapter("new-adapter", 1)
    check(SourceSnapshotGate.forNewSearch(null, now, listOf(first)).isEmpty())
    check(SourceSnapshotGate.forNewSearch(verified, now+900000, listOf(first)).isEmpty())
    check(SourceSnapshotGate.forNewSearch(verified, now, listOf(first)) == listOf(first))
    check(SourceSnapshotGate.forNewSearch(verified, now, listOf(old)).isEmpty())
    check(SourceSnapshotGate.forNewSearch(verified, now, listOf(GateTestAdapter("licensed-demo",4))).isEmpty())
    check(SourceSnapshotGate.forNewSearch(verified, now, listOf(first,first)).isEmpty())
    println("PASS: 6/6 verified adapter gate checks")
}
