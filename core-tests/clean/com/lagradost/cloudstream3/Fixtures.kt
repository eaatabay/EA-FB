package com.lagradost.cloudstream3

import org.jsoup.Jsoup

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
