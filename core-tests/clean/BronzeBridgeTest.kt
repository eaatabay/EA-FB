package com.eafb

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val context = MemoryContext()
    EASettings.initialize(context)
    val box = "https://www.dizibox.live"
    val you = "https://www.diziyou.one"
    app.handler = { req ->
        val body = when {
            req.url.startsWith(box) && req.url.contains("action=dwls_search") -> """{"results":[{"post_title":"Original Show","permalink":"$box/diziler/show/"}]}"""
            req.url == "$box/diziler/show/" -> "<article class='grid-box'><a href='/show-1-sezon-2-bolum-izle/'>1. Sezon 2. Bölüm</a></article>"
            req.url == "$box/show-1-sezon-2-bolum-izle/" -> "<div id='video-area'><iframe src='https://extractor.example/embed'></iframe></div>"
            req.method == "POST" -> ""
            req.url == "$you/?s=Original+Show" -> "<div id='list-series-main'><div class='cat-title-main'><a href='/original/' title='Original Show'>Original Show</a></div></div>"
            req.url == "$you/original/" -> "<a href='/right-episode/'><div class='bolumust'><div class='baslik'>1. Sezon 2. Bölüm</div></div></a>"
            req.url == "$you/right-episode/" -> "<iframe id='diziyouPlayer' src='https://player.diziyou.one/abc.html'></iframe><span class='diziyouOption' id='turkceDublaj'></span>"
            req.url.startsWith("https://storage.diziyou.one/episodes/") -> "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1,RESOLUTION=1920x1080\n1080.m3u8"
            else -> ""
        }
        FixtureResponse(body, req.url)
    }
    FixtureExtractor.handler = { _, referer, _, callback ->
        callback(ExtractorLink("https://cdn.example/box.m3u8", "Fixture", 1080, referer, emptyMap(), ExtractorLinkType.M3U8)); true
    }
    val data = "ea-fb:episode:42:1:2"
    suspend fun groups() = PlaybackLinkBridge.sourceGroups(data, "Türkçe Ad", 2026, 1000, listOf("Original Show"))
    check(groups().isEmpty())
    for (id in CleanTestIdentity.sourceIds) {
        EASettings.setSourceEnabled(id, true)
        check(groups().map { it.providerId } == listOf(id)) { "Independent toggle failed: $id" }
        EASettings.setSourceEnabled(id, false)
    }
    CleanTestIdentity.sourceIds.forEach { EASettings.setSourceEnabled(it, true) }
    check(groups().map { it.providerId }.toSet() == CleanTestIdentity.sourceIds.toSet())
    EASettings.setSourceEnabled("hdfilmcehennemi-land", false)
    check(groups().map { it.providerId }.toSet() == CleanTestIdentity.sourceIds.toSet() - "hdfilmcehennemi-land")
    println("PASS: four-source real bridge, individual opt-in and combined results (Bronze API-contract fixtures)")
}
