package com.eafb

import com.lagradost.cloudstream3.*
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject

fun main() = runBlocking {
    val baseline = System.getProperty("universal.baseline") == "true"
    var tests = 0
    for (provider in listOf("dizibox", "diziyou", "hdfilmcehennemi-nl", "hdfilmcehennemi-land")) {
        var totalCalls = 0
        var elapsedMillis = 0L
        var accepted = 0
        var falseAccepted = 0
        for (case in 0..7) {
            val name = if (case == 0) "Ted Lasso" else if (case == 1) "Organize İşler" else "Unseen Adventure $case"
            val seasonTitle = if (case == 1) "Karun Hazinesi" else if (case == 2) "Hidden Archive $case" else null
            val year = if (case == 0) 2020 else 2026
            // Identical fixture cases compile on both the immutable V83 and the new model.
            val constructor = MediaQuery::class.java.declaredConstructors.first { it.parameterCount in 7..8 }
            val args: List<Any?> = listOf(name, year, MediaKind.SERIES, 9000 + case, 1, 2, emptyList<String>())
            val q = constructor.newInstance(*(if (constructor.parameterCount == 8) args + seasonTitle else args).toTypedArray()) as MediaQuery
            val actual = if (seasonTitle == null) name else "$name: $seasonTitle"
            val observedYear = if (case == 4) null else if (case == 3) year - 1 else year
            val actualEpisode = if (case == 5) 3 else 2
            val ambiguous = case == 6
            val movie = case == 7
            val shouldAccept = case in 0..2
            val domain = when (provider) { "dizibox" -> "www.dizibox.live"; "diziyou" -> "www.diziyou.one";
                "hdfilmcehennemi-nl" -> "hdfilmcehennemi.nl"; else -> "hdfilmcehennemi.land" }
            val base = "https://$domain"
            val paths = if (ambiguous) listOf("one", "two") else listOf("one")
            app.requests.clear()
            app.handler = { request ->
                val result = when {
                    request.url.contains("action=dwls_search") -> JSONObject().put("results", JSONArray(paths.map {
                        JSONObject().put("post_title", actual).put("permalink", "$base/diziler/$it/")
                    })).toString()
                    request.method == "POST" -> paths.joinToString("") { "<div id='searchelement'><a href='/$it/'>Image</a><a>$actual</a></div>" }
                    paths.any { request.url == "$base/$it/" || request.url == "$base/diziler/$it/" } -> {
                        val path = request.url.trimEnd('/').substringAfterLast('/')
                        "<h1>$actual</h1>" + (observedYear?.let { "<div class='tv-overview'>Yapım Yılı: $it</div>" } ?: "") +
                            (if (movie) "<script type='application/ld+json'>{\"@type\":\"Movie\",\"name\":\"$actual\"}</script>" else "") +
                            "<article class='grid-box'><a href='/$path-1-sezon-$actualEpisode-bolum-izle/'>1. Sezon $actualEpisode. Bölüm</a></article>" +
                            "<a href='/$path-episode/'><div class='bolumust'><div class='baslik'>1. Sezon $actualEpisode. Bölüm</div></div></a>"
                    }
                    else -> ""
                }
                FixtureResponse(result, request.url)
            }
            var apiCalls = 0
            val api = object : MainAPI() {
                override suspend fun search(query: String): List<SearchResponse> { apiCalls++; return paths.map { SearchResponse(actual, "$base/$it/") } }
                override suspend fun load(url: String): LoadResponse { apiCalls++; return if (movie) MovieLoadResponse(actual, observedYear, "opaque")
                    else TvSeriesLoadResponse(actual, observedYear, listOf(Episode("opaque", 1, actualEpisode))) }
            }
            val adapter: MediaSourceAdapter = when (provider) { "dizibox" -> DiziBoxAdapter(); "diziyou" -> DiziYouAdapter();
                "hdfilmcehennemi-nl" -> BronzeNlAdapter { api }; else -> BronzeLandAdapter { api } }
            val started = System.nanoTime()
            val result = MultiSourceEngine(listOf(adapter)).find(q)
            elapsedMillis += (System.nanoTime() - started) / 1_000_000
            totalCalls += app.requests.size + apiCalls
            if (!baseline) check(result.isNotEmpty() == shouldAccept) { "provider=$provider case=$case offers=${result.size}" }
            if (result.isNotEmpty()) { if (shouldAccept) accepted++ else falseAccepted++ }
            tests++
        }
        println("PASS: ${if (baseline) "V83 baseline" else "universal"} adapter $provider positive=$accepted/3 falseAccept=$falseAccepted/5 calls=$totalCalls fixtureMs=$elapsedMillis")
    }
    println("PASS: $tests real-adapter discovery cases; original resolver fixtures run separately")
}
