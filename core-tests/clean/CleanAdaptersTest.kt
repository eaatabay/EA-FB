package com.eafb

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.FixtureResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.FixtureExtractor
import com.lagradost.cloudstream3.utils.SubtitleFile
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val query = MediaQuery("Türkçe Ad", 2026, MediaKind.SERIES, 42, 1, 2, listOf("Original Show"))
    check(SourceSearchTitles.candidates(query.copy(alternateTitles = listOf("Türkçe Ad", "", "Original Show"))) == listOf("Türkçe Ad", "Original Show"))
    val master = "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1,RESOLUTION=1920x1080\n1080.m3u8\n#EXT-X-STREAM-INF:BANDWIDTH=2,RESOLUTION=3840x2160\n2160.m3u8"
    check(HlsPlaylistInfo.parse(master) == HlsPlaylistInfo(2160, true))
    check(!HlsPlaylistInfo.parse("#EXTM3U\n#EXTINF:5,\nseg.ts").adaptive)
    check(HlsPlaylistInfo.parse("<html>error</html>") == HlsPlaylistInfo(null, false))
    val you = "https://www.diziyou.one"
    app.requests.clear()
    app.handler = { req ->
        val body = when {
            req.method == "POST" -> "<div>unusable AJAX</div>"
            req.url == "$you/?s=Original+Show" -> "<div id='list-series-main'><div class='cat-title-main'><a href='/original/' title='Original Show'>Original Show</a></div><a href='/wrong/'>Different Show</a></div>"
            req.url == "$you/original/" -> "<a href='/wrong-season/'><div class='bolumust'><div class='baslik'>2. Sezon 2. Bölüm</div></div></a><a href='/right-episode/'><div class='bolumust'><div class='baslik'>1. Sezon 2. Bölüm</div></div></a>"
            req.url == "$you/right-episode/" -> "<iframe id='diziyouPlayer' src='https://player.diziyou.one/abc.html'></iframe><span class='diziyouOption' id='turkceAltyazili'></span><span class='diziyouOption' id='ingilizceAltyazili'></span><span class='diziyouOption' id='turkceDublaj'></span>"
            req.url.startsWith("https://storage.diziyou.one/episodes/") -> master
            else -> ""
        }
        FixtureResponse(body, req.url)
    }
    val adapter = DiziYouAdapter()
    val offers = adapter.search(query)
    check(offers.single().pageUrl == "$you/right-episode/")
    check(offers.single().title == query.title && offers.single().tmdbId == 42)
    val links = MultiSourceEngine(listOf(adapter)).resolve(offers, 1000)
    check(links.size == 2 && links.all { it.isHls && it.isAdaptive })
    check(links.first { it.audioLanguage == "original" }.subtitles.map { it.url } == listOf("https://storage.diziyou.one/subtitles/abc/tr.vtt", "https://storage.diziyou.one/subtitles/abc/en.vtt"))
    check(app.requests.any { it.url == "$you/?s=Original+Show" })
    // Keep the existing AJAX success path: no fallback fetch when it already resolves an episode.
    app.requests.clear()
    val htmlHandler = app.handler
    app.handler = { req ->
        if (req.method == "POST") FixtureResponse("<div id='searchelement'><a href='/original/'>Image</a><a>Türkçe Ad</a></div>", req.url)
        else htmlHandler(req)
    }
    check(adapter.search(query).size == 1)
    check(app.requests.none { "/?s=" in it.url })
    check(adapter.search(query.copy(kind = MediaKind.MOVIE, season = null, episode = null)).isEmpty())

    val box = "https://www.dizibox.live"
    app.requests.clear()
    app.handler = { req ->
        if (req.url == "$box/slow-alt/") delay(500)
        val body = when {
            req.url == "$box/diziler/show/" -> "<article class='grid-box'><a href='/show-1-sezon-2-bolum-izle/'>1. Sezon 2. Bölüm</a></article>"
            req.url == "$box/show-1-sezon-2-bolum-izle/" -> "<div id='video-area'><iframe src='$box/player/king/king.php?v=test'></iframe></div><div class='video-toolbar'><select><option value='$box/slow-alt/'>Alternative</option></select></div>"
            req.url.contains("/player/king/king.php?") -> "<div id='Player'><iframe src='https://extractor.example/embed'></iframe></div>"
            else -> ""
        }
        FixtureResponse(body, req.url)
    }
    FixtureExtractor.handler = { url, referer, sub, link ->
        check(url == "https://extractor.example/embed")
        link(ExtractorLink("https://cdn.example/extensionless", "Fixture HLS", 1080, referer, mapOf("X-Fixture" to "required"), ExtractorLinkType.M3U8))
        // Late subtitles must still reach the final SourceLink.
        sub(SubtitleFile("Türkçe", "https://cdn.example/sub.vtt"))
        true
    }
    val boxAdapter = DiziBoxAdapter()
    val offer = MediaOffer("dizibox", "DiziBox", "Show", 2026, MediaKind.SERIES, "$box/diziler/show/", 42, 1, 2)
    val resolved = MultiSourceEngine(listOf(boxAdapter), perAdapterTimeoutMs = 200).resolve(listOf(offer), 1000)
    check(resolved.size == 1)
    check(resolved.single().isHls && resolved.single().headers == mapOf("X-Fixture" to "required"))
    check(resolved.single().subtitles.single().url == "https://cdn.example/sub.vtt")
    check(app.requests.any { it.url == "$box/slow-alt/" })
    // DiziBox keeps catalog identity even when the original title finds the source.
    app.handler = { req ->
        val body = if (req.url.contains("action=dwls_search") && req.url.contains("Original+Show"))
            "{\"results\":[{\"post_title\":\"Original Show\",\"permalink\":\"$box/diziler/show/\"}]}" else ""
        FixtureResponse(body, req.url)
    }
    check(boxAdapter.search(query).single().title == query.title)
    // Independent, precomputed OpenSSL AES-256-CBC/MD5 vector; no runtime KDF duplication.
    val ciphertext = "U2FsdGVkX18BAgMEBQYHCPSHkaSjNO8wP8FpiNw0ulvzt5zE1VDNdJiY/MGTMuaei8wa1yCls5X6wKTcG/AntA=="
    for (mode in listOf("king", "moly", "haydi")) {
        app.handler = { req ->
            val player = when (mode) {
                "king" -> "$box/player/king/king.php?v=test"
                "moly" -> "$box/player/moly/moly.php?h=test"
                else -> "$box/player/haydi.php?v=" + java.util.Base64.getEncoder().encodeToString("https://molystream.org/embed/test".toByteArray())
            }
            val encodedFrame = java.util.Base64.getEncoder().encodeToString("<iframe src='https://molystream.org/embed/test'></iframe>".toByteArray())
            val body = when {
                req.url == "$box/diziler/show/" -> "<article class='grid-box'><a href='/show-1-sezon-2-bolum-izle/'>1. Sezon 2. Bölüm</a></article>"
                req.url == "$box/show-1-sezon-2-bolum-izle/" -> "<div id='video-area'><iframe src='$player'></iframe></div>"
                req.url.contains("/player/king/") -> "<iframe src='https://molystream.org/embed/test'></iframe>"
                req.url.contains("/player/moly/") -> "<script>unescape(\"$encodedFrame\")</script>"
                req.url == "https://molystream.org/embed/test" -> "<script>CryptoJS.AES.decrypt(\"$ciphertext\",\"fixture\")</script>"
                req.url == "https://molystream.org/master" -> master
                else -> ""
            }
            FixtureResponse(body, req.url)
        }
        val result = MultiSourceEngine(listOf(boxAdapter)).resolve(listOf(offer), 1000)
        check(result.single().url == "https://molystream.org/master") { "Broken $mode fixture" }
        check(result.single().isAdaptive)
    }
    println("PASS: real adapters with offline HTML/HLS fixtures; GET/title fallback, exact episode, subtitles, headers/type and main-link timeout retention")
}
