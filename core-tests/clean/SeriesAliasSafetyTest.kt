package com.eafb

import com.lagradost.cloudstream3.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject

private const val ALIAS = "Organize İşler: Karun Hazinesi"
private val TARGET = MediaQuery("Organize İşler", 2026, MediaKind.SERIES, 320754, 1, 8)
private data class SafePage(val path: String = "one", val year: Int? = 2026,
    val movie: Boolean = false, val name: String = ALIAS,
    val episodes: List<Episode> = listOf(Episode("safe-fixture", 1, 8)))
private class SafeBronze(private val host: String, private val pages: List<SafePage>) : MainAPI() {
    override suspend fun search(query: String) = pages.map { SearchResponse(it.name, "https://$host/${it.path}/") }
    override suspend fun load(url: String): LoadResponse {
        val page = pages.single { url == "https://$host/${it.path}/" }
        return if (page.movie) MovieLoadResponse(page.name, page.year, "never-episode")
            else TvSeriesLoadResponse(page.name, page.year, page.episodes)
    }
}

fun main() = runBlocking {
    var count = 0
    fun verify(value: Boolean) { check(value); count++ }
    for (land in listOf(false, true)) {
        suspend fun find(pages: List<SafePage>): List<MediaOffer> {
            val api = SafeBronze(if (land) "hdfilmcehennemi.land" else "hdfilmcehennemi.nl", pages)
            return (if (land) BronzeLandAdapter { api } else BronzeNlAdapter { api }).search(TARGET)
        }
        verify(find(listOf(SafePage())).single().playbackData == "safe-fixture")
        for (bad in listOf(SafePage(year = null), SafePage(year = 2019), SafePage(movie = true),
            SafePage(name = "Organize İşler: Sazan Sarmalı"), SafePage(name = "Karun Hazinesi Başka Dizi"),
            SafePage(episodes = listOf(Episode("bad", 2, 8))), SafePage(episodes = listOf(Episode("bad", 1, 7))),
            SafePage(episodes = listOf(Episode("first", 1, 8), Episode("second", 1, 8))))) {
            verify(find(listOf(bad)).isEmpty())
        }
        verify(find(listOf(SafePage("one"), SafePage("two"))).isEmpty())
    }
    for (box in listOf(false, true)) {
        val base = if (box) "https://www.dizibox.live" else "https://www.diziyou.one"
        val source: MediaSourceAdapter = if (box) DiziBoxAdapter() else DiziYouAdapter()
        suspend fun find(year: String? = "2026", type: String = "TVSeries", season: Int = 1,
            episode: Int = 8, duplicate: Boolean = false, heading: String = ALIAS): List<MediaOffer> {
            app.handler = { req ->
                fun page(path: String): String {
                    val slug = path.trim('/').substringAfterLast('/')
                    val url = base + path
                    val schema = JSONObject().put("@type", type).put("name", ALIAS).put("url", url)
                    if (year != null) schema.put("startDate", year)
                    val meta = "<h1>$heading</h1><script type='application/ld+json'>$schema</script>"
                    val episodeUrl = if (box) "/$slug-$season-sezon-$episode-bolum-izle/" else "/$slug-episode/"
                    return if (box) "<div class='tv-overview'>$meta</div><article class='grid-box'><a href='$episodeUrl'>$season. Sezon $episode. Bölüm</a></article>"
                    else meta + "<a href='$episodeUrl'><div class='bolumust'><div class='baslik'>$season. Sezon $episode. Bölüm</div></div></a>"
                }
                val paths = if (box) listOf("/diziler/one/", "/diziler/two/") else listOf("/one/", "/two/")
                val matches = if (duplicate) paths else paths.take(1)
                val body = when {
                    box && req.url.contains("action=dwls_search") -> "{\"results\":[" + matches.joinToString(",") {
                        JSONObject().put("post_title", ALIAS).put("permalink", base+it).toString()
                    } + "]}"
                    !box && req.method == "POST" -> matches.joinToString("") {
                        "<div id='searchelement'><a href='$it'>Image</a><a>$ALIAS</a></div>"
                    }
                    req.url.removePrefix(base) in paths -> page(req.url.removePrefix(base))
                    else -> ""
                }
                FixtureResponse(body, req.url)
            }
            return source.search(TARGET)
        }
        verify(find().single().tmdbId == 320754)
        verify(find(year = null).isEmpty())
        verify(find(year = "2019").isEmpty())
        verify(find(type = "Movie").isEmpty())
        verify(find(season = 2).isEmpty())
        verify(find(episode = 7).isEmpty())
        verify(find(duplicate = true).isEmpty())
        verify(find(heading = "Organize İşler: Sazan Sarmalı").isEmpty())
    }
    println("PASS: $count new-alias source safety assertions; missing/wrong year, movie, wrong episode and ambiguity rejected")
}
