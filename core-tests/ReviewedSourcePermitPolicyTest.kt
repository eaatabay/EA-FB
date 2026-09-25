package com.eafb

private const val T = 1_800_000_000_000L
private const val DAY = 86_400_000L

private val movie = ReviewedSourcePermit(
    "fixture-movie", 3, "movie", setOf("films.example.org","new-films.example.org"),
    "/public", "rights/2026/fixture-movie.md", T - DAY, T + 7*DAY
)
private val series = ReviewedSourcePermit(
    "fixture-series", 2, "series", setOf("series.example.org"),
    "/", "rights/2026/fixture-series.md", T - DAY, T + 7*DAY
)
private val both = ReviewedSourcePermit(
    "fixture-both", 1, "both", setOf("both.example.org"),
    "/media", "rights/2026/fixture-both.md", T - DAY, T + 7*DAY
)
private val snapshot = VerifiedSourceSnapshot(42, T, T+900_000, listOf(
    SnapshotSource("fixture-movie","movie","https://films.example.org/public/first",3),
    SnapshotSource("fixture-series","series","https://series.example.org",2),
    SnapshotSource("fixture-both","both","https://both.example.org/media/first",1)
))

fun main() {
    var passed = 0
    fun test(ok: Boolean, name: String) { check(ok) { name }; passed++ }
    val permits = listOf(movie, series, both)
    fun only(snapshot: VerifiedSourceSnapshot? = this.snapshot,
        list: List<ReviewedSourcePermit> = permits,
        now: Long = T): List<SnapshotSource> =
        ReviewedSourcePermitPolicy.restrict(snapshot,now,list)?.usableSources.orEmpty()
    test(ReviewedSourcePermits.bundled.isEmpty(),"no real approvals in public release")
    test(only().map { it.id } ==
        listOf("fixture-movie","fixture-series","fixture-both"),
        "three distinct reviewed source types permitted")
    test(only(list=emptyList()).isEmpty(),"no reviewed permits means no active source")
    test(only(snapshot=null).isEmpty(),"no signature-verified snapshot")
    test(only(now=T+900_000).isEmpty(),"snapshot expiration is binding")
    test(only(list=permits+movie).isEmpty(),"duplicate rights IDs block whole list")
    test(only(list=permits+movie.copy(id="fixture-extras")).isEmpty().not(),
        "distinct reviewed permit does not corrupt valid sources")
    test(only(list=listOf(movie.copy(adapterVersion=4),series,both))
        .map { it.id } == listOf("fixture-series","fixture-both"),
        "signed and bundled version must match rights permit")
    test(only(list=listOf(movie.copy(validUntil=T),series,both)).isEmpty(),
        "an expired reviewed permit closes the whole registry")
    test(only(list=listOf(movie.copy(reviewedAt=T+1),series,both)).isEmpty(),
        "future-dated rights review never accepted")
    test(only(list=listOf(movie.copy(validUntil=T+367*DAY),series,both)).isEmpty(),
        "a rights permit must be reviewed at least annually")
    test(only(list=listOf(movie.copy(evidenceReference="approved"),series,both))
        .isEmpty(),"unverifiable rights reference not silently trusted")
    test(only(list=listOf(movie.copy(evidenceReference="rights/../fake/permit.md"),series,both))
        .isEmpty(),"parent-directory traversal in reference denied")
    test(only(list=listOf(movie.copy(approvedHosts=setOf("internal.local")),series,both))
        .isEmpty(),"internal-only domain never becomes approved")
    test(only(list=listOf(movie.copy(approvedHosts=setOf("192.0.0.9")),series,both))
        .isEmpty(),"IP literal cannot become approved host")
    val withOtherHost = snapshot.copy(usableSources =
        snapshot.usableSources.map { if (it.id=="fixture-movie")
            it.copy(baseUrl="https://evil.example.org/public/first") else it })
    test(only(snapshot=withOtherHost).map { it.id } ==
        listOf("fixture-series","fixture-both"),"a signed URL cannot override approved domain")
    val withWrongPath = snapshot.copy(usableSources =
        snapshot.usableSources.map { if (it.id=="fixture-movie")
            it.copy(baseUrl="https://films.example.org/publicity") else it })
    test(only(snapshot=withWrongPath).map { it.id } ==
        listOf("fixture-series","fixture-both"),"path prefix requires exact segment boundary")
    val withEncodedPath = snapshot.copy(usableSources =
        snapshot.usableSources.map { if (it.id=="fixture-movie")
            it.copy(baseUrl="https://films.example.org/public/%2e%2e/private") else it })
    test(only(snapshot=withEncodedPath).map { it.id } ==
        listOf("fixture-series","fixture-both"),"encoded traversal rejected")
    val moved = snapshot.copy(usableSources =
        snapshot.usableSources.map { if(it.id=="fixture-movie")
            it.copy(baseUrl="https://new-films.example.org/public/second") else it })
    test(only(snapshot=moved).first().baseUrl ==
        "https://new-films.example.org/public/second",
        "human preapproved alternate domain can be signed and promoted")
    val unapprovedMove = snapshot.copy(usableSources =
        snapshot.usableSources.map { if(it.id=="fixture-movie")
            it.copy(baseUrl="https://unknown-films.example.org/public") else it })
    test(only(snapshot=unapprovedMove).none { it.id=="fixture-movie" },
        "unreviewed redirect host cannot be promoted")
    val signedBoth = snapshot.copy(usableSources =
        snapshot.usableSources.map { if (it.id=="fixture-movie")
            it.copy(mediaKind="both") else it })
    test(only(snapshot=signedBoth).first().mediaKind=="movie",
        "license restricted to films narrows a signed both-media source")
    val signedWrongKind = snapshot.copy(usableSources =
        snapshot.usableSources.map { if (it.id=="fixture-movie")
            it.copy(mediaKind="series") else it })
    test(only(snapshot=signedWrongKind).none {it.id=="fixture-movie"},
        "signed series cannot exceed a movie-only reviewed permit")
    test(only(list=permits+List(30) {
        movie.copy(id="fixture-extra-$it")
    }).isEmpty(),"no more than 32 compiled rights permits")
    println("PASS: $passed/$passed reviewed source permit policy cases")
}
