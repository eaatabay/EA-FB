package com.eafb

import com.lagradost.cloudstream3.*
import kotlinx.coroutines.runBlocking
import android.util.Log

private data class FilmPage(val result: String, val loaded: String, val year: Int?, val path: String = "film")
private class FilmSearchFixture(private val domain: String, private val pages: List<FilmPage>) : MainAPI() {
    val searches = mutableListOf<String>()
    val loads = mutableListOf<String>()
    override suspend fun search(query: String): List<SearchResponse> {
        searches += query
        return pages.map { SearchResponse(it.result, "https://$domain/${it.path}/") }
    }
    override suspend fun load(url: String): LoadResponse {
        loads += url
        val page = pages.first { url == "https://$domain/${it.path}/" }
        return MovieLoadResponse(page.loaded, page.year, "opaque:${page.path}")
    }
}

fun main() = runBlocking {
    // These are controlled fixtures, not claims about current source catalogues.
    for (land in listOf(false, true)) {
        fun adapter(pages: List<FilmPage>): Pair<BronzeApiAdapter, FilmSearchFixture> {
            val fixture = FilmSearchFixture(if (land) "hdfilmcehennemi.land" else "hdfilmcehennemi.nl", pages)
            return (if (land) BronzeLandAdapter { fixture } else BronzeNlAdapter { fixture }) to fixture
        }
        suspend fun accepts(title: String, year: Int?, page: FilmPage, aliases: List<String> = emptyList()) {
            val (source, fixture) = adapter(listOf(page))
            val offers = source.search(MediaQuery(title, year, MediaKind.MOVIE, 123, alternateTitles = aliases))
            check(offers.size == 1 && offers.single().playbackData == "opaque:${page.path}") {
                "Expected acceptance: $title -> ${page.result} / ${page.loaded}, year=$year/${page.year}"
            }
            check(fixture.loads.size == 1)
        }
        suspend fun rejects(title: String, year: Int?, pages: List<FilmPage>, aliases: List<String> = emptyList()) {
            val (source, _) = adapter(pages)
            check(source.search(MediaQuery(title, year, MediaKind.MOVIE, 123, alternateTitles = aliases)).isEmpty()) {
                "Expected rejection: $title, year=$year, pages=$pages"
            }
        }
        accepts("The Odyssey", 2026, FilmPage("Odyssey - The Odyssey", "Odyssey - The Odyssey", 2026))
        accepts("Örümcek Adam Eve Dönüş", 2017,
            FilmPage("Örümcek Adam Eve Dönüş - Spider-Man: Homecoming", "Spider-Man: Homecoming", 2017),
            listOf("Spider-Man: Homecoming"))
        accepts("Spider-Man: Homecoming", 2017, FilmPage("Spiderman Homecoming", "Spiderman Homecoming", 2017))
        accepts("Spider-Man: Far From Home", 2019, FilmPage("Spider Man Far From Home", "Spider-Man: Far From Home", 2019))
        accepts("Spider-Man: No Way Home", 2021, FilmPage("Home No Way Spider Man", "Spider-Man: No Way Home", 2021))
        accepts("Spider-Man: Brand New Day", 2026, FilmPage("Brand New Day Spider Man", "Spider-Man: Brand New Day", 2026))
        accepts("Çağrı", 2020, FilmPage("Cagri", "Çağrı", 2020))
        // Original exact-name behaviour with unknown years remains available.
        accepts("Exact Film", null, FilmPage("Exact Film", "Exact Film", null))
        rejects("The Odyssey", 2026, listOf(FilmPage("Odyssey - The Odyssey", "Odyssey - The Odyssey", 2025)))
        rejects("The Odyssey", 2026, listOf(FilmPage("Odyssey - The Odyssey", "Odyssey - The Odyssey", null)))
        rejects("The Odyssey", null, listOf(FilmPage("Odyssey - The Odyssey", "Odyssey - The Odyssey", 2026)))
        rejects("The Odyssey", 2026, listOf(FilmPage("Other Film - The Odyssey", "Other Film - The Odyssey", 2026)))
        rejects("Spider-Man: Homecoming", 2017, listOf(FilmPage("Spider-Man: No Way Home", "Spider-Man: No Way Home", 2017)))
        rejects("Film", 2026, listOf(FilmPage("Film", "Different Film", 2026)))
        rejects("Film", 2026, listOf(FilmPage("Film", "Film", 2025)))
        rejects("The Odyssey", 2026, listOf(
            FilmPage("Odyssey - The Odyssey", "The Odyssey", 2026, "one"),
            FilmPage("The Odyssey", "The Odyssey", 2026, "two")))
        rejects("One One Two", 2026, listOf(FilmPage("One Two Two", "One Two Two", 2026)))
        val fallbackQueries = mutableListOf<String>()
        val fallback = object : MainAPI() {
            override suspend fun search(query: String): List<SearchResponse> {
                fallbackQueries += query
                return if (query == "The Odyssey") listOf(SearchResponse("Odyssey - The Odyssey",
                    "https://${if (land) "hdfilmcehennemi.land" else "hdfilmcehennemi.nl"}/odyssey/")) else emptyList()
            }
            override suspend fun load(url: String): LoadResponse = MovieLoadResponse("The Odyssey", 2026, "opaque-fallback")
        }
        val fallbackSource = if (land) BronzeLandAdapter { fallback } else BronzeNlAdapter { fallback }
        check(fallbackSource.search(MediaQuery("Odysseia", 2026, MediaKind.MOVIE, 123,
            alternateTitles = listOf("The Odyssey"))).single().playbackData == "opaque-fallback")
        check(fallbackQueries == listOf("Odysseia", "The Odyssey"))
        for (reason in listOf("search-title", "load-title", "year-mismatch", "variant-needs-year", "ambiguous-pages")) {
            check(Log.records.any { it.first == "EA-FB-Bronze" && it.second.contains("provider=${fallbackSource.id}") &&
                it.second.contains("reason=$reason") })
        }
        check(Log.records.none { it.second.contains("opaque:") || it.second.contains("opaque-fallback") })
        println("PASS: ${if (land) "LAND" else "NL"} movie aliases, all five control-title fixtures, years, wrong films and ambiguity")
    }
}
