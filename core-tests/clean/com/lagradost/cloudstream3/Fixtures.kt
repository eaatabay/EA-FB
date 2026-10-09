package com.lagradost.cloudstream3

import org.jsoup.Jsoup
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.SubtitleFile

open class MainAPI {
    open suspend fun search(query: String): List<SearchResponse>? = error("No fixture")
    open suspend fun load(url: String): LoadResponse? = error("No fixture")
    open suspend fun loadLinks(data: String, isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean = error("No fixture")
}
data class SearchResponse(val name: String, val url: String)
open class LoadResponse(val name: String, val year: Int?)
class MovieLoadResponse(name: String, year: Int?, val dataUrl: String) : LoadResponse(name, year)
class TvSeriesLoadResponse(name: String, year: Int?, val episodes: List<Episode>) : LoadResponse(name, year)
data class Episode(val data: String, val season: Int?, val episode: Int?)

data class FixtureRequest(val method: String, val url: String, val data: Map<String, String>)
data class FixtureResponse(val text: String, val url: String, val code: Int = 200) {
    val document get() = Jsoup.parse(text, url)
}
object app {
    val requests = mutableListOf<FixtureRequest>()
    var handler: suspend (FixtureRequest) -> FixtureResponse = { error("No network allowed in offline adapter tests") }
    suspend fun get(url: String, referer: String? = null, headers: Map<String, String> = emptyMap(), cookies: Map<String, String> = emptyMap()): FixtureResponse {
        val request = FixtureRequest("GET", url, emptyMap()); requests += request
        return handler(request)
    }
    suspend fun post(url: String, data: Map<String, String>): FixtureResponse {
        val request = FixtureRequest("POST", url, data); requests += request
        return handler(request)
    }
}
