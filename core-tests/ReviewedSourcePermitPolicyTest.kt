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
private val defaultSnapshot = VerifiedSourceSnapshot(42, T, T+900_000, listOf(
    SnapshotSource("fixture-movie","movie","https://films.example.org/public/first",3),
    SnapshotSource("fixture-series","series","https://series.example.org",2),
    SnapshotSource("fixture-both","both","https://both.example.org/media/first",1)
))

fun main() {
    var passed = 0
    fun test(ok: Boolean, name: String) { check(ok) { name }; passed++ }
    val permits = listOf(movie, series, both)
    fun only(snapshot: VerifiedSourceSnapshot? = defaultSnapshot,
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
    val withOtherHost = defaultSnapshot.copy(usableSources =
        defaultSnapshot.usableSources.map { if (it.id=="fixture-movie")
            it.copy(baseUrl="https://evil.example.org/public/first") else it })
    test(only(snapshot=withOtherHost).map { it.id } ==
        listOf("fixture-series","fixture-both"),"a signed URL cannot override approved domain")
    val withWrongPath = defaultSnapshot.copy(usableSources =
        defaultSnapshot.usableSources.map { if (it.id=="fixture-movie")
            it.copy(baseUrl="https://films.example.org/publicity") else it })
    test(only(snapshot=withWrongPath).map { it.id } ==
        listOf("fixture-series","fixture-both"),"path prefix requires exact segment boundary")
    val withEncodedPath = defaultSnapshot.copy(usableSources =
        defaultSnapshot.usableSources.map { if (it.id=="fixture-movie")
            it.copy(baseUrl="https://films.example.org/public/%2e%2e/private") else it })
    test(only(snapshot=withEncodedPath).map { it.id } ==
        listOf("fixture-series","fixture-both"),"encoded traversal rejected")
    val moved = defaultSnapshot.copy(usableSources =
        defaultSnapshot.usableSources.map { if(it.id=="fixture-movie")
            it.copy(baseUrl="https://new-films.example.org/public/second") else it })
    test(only(snapshot=moved).first().baseUrl ==
        "https://new-films.example.org/public/second",
        "human preapproved alternate domain can be signed and promoted")
    val unapprovedMove = defaultSnapshot.copy(usableSources =
        defaultSnapshot.usableSources.map { if(it.id=="fixture-movie")
            it.copy(baseUrl="https://unknown-films.example.org/public") else it })
    test(only(snapshot=unapprovedMove).none { it.id=="fixture-movie" },
        "unreviewed redirect host cannot be promoted")
    val signedBoth = defaultSnapshot.copy(usableSources =
        defaultSnapshot.usableSources.map { if (it.id=="fixture-movie")
            it.copy(mediaKind="both") else it })
    test(only(snapshot=signedBoth).first().mediaKind=="movie",
        "license restricted to films narrows a signed both-media source")
    val signedWrongKind = defaultSnapshot.copy(usableSources =
        defaultSnapshot.usableSources.map { if (it.id=="fixture-movie")
            it.copy(mediaKind="series") else it })
    test(only(snapshot=signedWrongKind).none {it.id=="fixture-movie"},
        "signed series cannot exceed a movie-only reviewed permit")
    test(only(list=permits+List(30) {
        movie.copy(id="fixture-extra-$it")
    }).isEmpty(),"no more than 32 compiled rights permits")
    // URL scope must be a segment boundary, never a string-prefix grant.
    fun movieUrl(url: String): List<SnapshotSource> =
        only(snapshot=defaultSnapshot.copy(usableSources=
            defaultSnapshot.usableSources.map {
                if (it.id=="fixture-movie") it.copy(baseUrl=url) else it
            }))
    test(movieUrl("https://films.example.org/public").any { it.id=="fixture-movie" },
        "exact approved path accepted")
    test(movieUrl("https://films.example.org/public/child/deep").any { it.id=="fixture-movie" },
        "descendant approved path accepted")
    test(movieUrl("https://films.example.org/private").none { it.id=="fixture-movie" },
        "outside approved path rejected")
    test(movieUrl("https://films.example.org/public-other").none { it.id=="fixture-movie" },
        "sibling path cannot exploit prefix")
    test(movieUrl("http://films.example.org/public").none { it.id=="fixture-movie" },
        "plain HTTP cannot use approved HTTPS host")
    test(movieUrl("https://films.example.org:443/public").none { it.id=="fixture-movie" },
        "explicit port is outside pinned origin")
    test(movieUrl("https://user@films.example.org/public").none { it.id=="fixture-movie" },
        "URL credentials cannot accompany approved host")
    test(movieUrl("https://films.example.org/public?redirect=other").none { it.id=="fixture-movie" },
        "query cannot extend approved path scope")
    test(movieUrl("https://films.example.org/public#fragment").none { it.id=="fixture-movie" },
        "fragment cannot extend approved path scope")
    test(movieUrl("https://films.example.org.evil.org/public").none { it.id=="fixture-movie" },
        "approved hostname suffix is not a valid exact match")
    test(movieUrl("https://films.example.org/public/%2Fprivate").none { it.id=="fixture-movie" },
        "encoded slash cannot bypass reviewed path")
    test(movieUrl("https://films.example.org/public/../private").none { it.id=="fixture-movie" },
        "literal parent traversal cannot bypass reviewed path")

    val signedAllBoth=defaultSnapshot.copy(usableSources=
        defaultSnapshot.usableSources.map { it.copy(mediaKind="both") })
    test(only(snapshot=signedAllBoth).map { it.mediaKind } ==
        listOf("movie","series","both"),
        "rights intersect signed movie/series/both claims independently")
    val signedMovieOnly=defaultSnapshot.copy(usableSources=
        defaultSnapshot.usableSources.map {
            if(it.id=="fixture-both") it.copy(mediaKind="movie") else it
        })
    test(only(snapshot=signedMovieOnly).last().mediaKind=="movie",
        "both-media permit never broadens signed movie-only claim")
    val signedSeriesOnly=defaultSnapshot.copy(usableSources=
        defaultSnapshot.usableSources.map {
            if(it.id=="fixture-both") it.copy(mediaKind="series") else it
        })
    test(only(snapshot=signedSeriesOnly).last().mediaKind=="series",
        "both-media permit never broadens signed series-only claim")
    val wrongSeries=defaultSnapshot.copy(usableSources=
        defaultSnapshot.usableSources.map {
            if(it.id=="fixture-series") it.copy(mediaKind="movie") else it
        })
    test(only(snapshot=wrongSeries).none { it.id=="fixture-series" },
        "series-only rights reject signed movie-only claim")
    test(only(list=listOf(movie)).map { it.id }==listOf("fixture-movie"),
        "unreviewed signed sources are excluded without dropping reviewed ones")
    test(ReviewedSourcePermitPolicy.restrict(defaultSnapshot,T,emptyList())==null,
        "empty compiled rights registry fails closed rather than returning an approval")
    test(ReviewedSourcePermitPolicy.restrict(defaultSnapshot,T,permits+movie)==null,
        "duplicate rights registry fails closed rather than returning an approval")
    test(ReviewedSourcePermitPolicy.restrict(null,T,permits)==null,
        "unsigned snapshot fails closed")
    test(only(list=listOf(movie.copy(approvedPathPrefix="/public/../private"),
        series,both)).isEmpty(), "unsafe reviewed path closes registry")
    test(only(list=listOf(movie.copy(approvedHosts=setOf("FILMS.example.org")),
        series,both)).isEmpty(), "noncanonical uppercase approved host rejected")
    test(only(list=listOf(movie.copy(mediaKind="video"),series,both)).isEmpty(),
        "unknown media rights category closes registry")
    test(only(list=listOf(movie.copy(evidenceReference="rights/2026/a.md"),
        series,both)).isEmpty(), "short unverifiable evidence path rejected")
    val signedInvalid=defaultSnapshot.copy(usableSources=
        defaultSnapshot.usableSources.map {
            if (it.id=="fixture-both") it.copy(mediaKind="live") else it
        })
    test(only(snapshot=signedInvalid).none { it.id=="fixture-both" },
        "even a both-media permit rejects unsupported signed LIVE kind")
    val signedUnknown=defaultSnapshot.copy(usableSources=
        defaultSnapshot.usableSources.map {
            if (it.id=="fixture-both") it.copy(mediaKind="unknown") else it
        })
    test(only(snapshot=signedUnknown).none { it.id=="fixture-both" },
        "unsupported signed media never passes the permit intersection")
    test(only(list=listOf(movie.copy(reviewedAt=T+DAY),series,both)).isEmpty(),
        "rights review dated in future is not yet valid")
    test(only(list=listOf(movie.copy(evidenceReference=""),series,both)).isEmpty(),
        "missing evidence reference fails closed")
    test(only(list=listOf(movie.copy(evidenceReference="https://example.org/fake.md"),
        series,both)).isEmpty(), "external evidence URL cannot substitute reviewed record")
    test(only(list=listOf(movie.copy(id="fixture-movie",approvedHosts=emptySet()),
        series,both)).isEmpty(), "empty approved host set invalidates permit registry")
    test(only(list=listOf(movie.copy(
        evidenceReference="rights/2026//fixture-movie.md"),series,both)).isEmpty(),
        "empty evidence path segment cannot alias a reviewed record")
    test(only(list=listOf(movie.copy(
        evidenceReference="rights/2026/./fixture-movie.md"),series,both)).isEmpty(),
        "current-directory evidence segment cannot alias a reviewed record")
    test(only(list=listOf(movie.copy(
        evidenceReference="rights/2026/fixture-movie.md/"),series,both)).isEmpty(),
        "trailing slash cannot alias a reviewed record")
    test(only(list=listOf(movie.copy(
        evidenceReference="rights/2026/fixture-movie.md"),series,both))
        .any { it.id=="fixture-movie" },
        "canonical reviewed evidence path remains valid")
    test(movieUrl("https://FILMS.example.org/public").none { it.id=="fixture-movie" },
        "noncanonical uppercase signed authority rejected")
    val duplicateSigned=defaultSnapshot.copy(usableSources=
        defaultSnapshot.usableSources + defaultSnapshot.usableSources.first())
    test(ReviewedSourcePermitPolicy.restrict(duplicateSigned,T,permits)==null,
        "duplicate signed source IDs fail closed at rights boundary")
    test(movieUrl("https://films.example.org/public/./private")
        .none { it.id=="fixture-movie" },
        "dot segment in signed URL path is rejected")
    test(only(list=listOf(movie.copy(approvedPathPrefix="/public/./private"),
        series,both)).isEmpty(),
        "dot segment in reviewed path scope invalidates the registry")
    test(movieUrl("https://films.example.org/public/a..b")
        .any { it.id=="fixture-movie" },
        "safe filename containing two dots remains inside reviewed path")
    println("PASS: $passed/$passed reviewed source permit policy cases")
}
