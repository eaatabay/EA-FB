package com.eafb

import com.lagradost.cloudstream3.*
import kotlinx.coroutines.runBlocking
import java.net.URI
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.io.File
import org.json.JSONObject

/** Opt-in real source lookup; never calls resolve, a player URL or a media playlist. */
fun main() = runBlocking {
    val codes = mutableListOf<Int>()
    app.handler = { req ->
        val uri = URI(req.url)
        check(uri.scheme == "https" && uri.host == "www.diziyou.one")
        val connection = uri.toURL().openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = false
        connection.connectTimeout = 15000
        connection.readTimeout = 15000
        connection.requestMethod = req.method
        if (req.method == "POST") {
            check(uri.path == "/wp-admin/admin-ajax.php" && req.data["action"] == "data_fetch")
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            val body = req.data.entries.joinToString("&") {
                URLEncoder.encode(it.key, "UTF-8") + "=" + URLEncoder.encode(it.value, "UTF-8")
            }
            connection.outputStream.use { it.write(body.toByteArray()) }
        }
        val code = connection.responseCode
        codes += code
        val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
            ?.bufferedReader()?.use { it.readText() }.orEmpty()
        connection.disconnect()
        FixtureResponse(body, req.url, code)
    }
    val query = MediaQuery("Organize İşler", 2026, MediaKind.SERIES, 320754, 1, 8)
    val offers = DiziYouAdapter().search(query)
    val report = JSONObject().put("source", "diziyou").put("tmdbId", 320754)
        .put("season", 1).put("episode", 8).put("requests", codes.size)
        .put("httpStatuses", codes).put("offers", offers.size).put("streamOrExtractorTested", false)
    File("build/series-diagnostics").mkdirs()
    File("build/series-diagnostics/live-source-lookup.json").writeText(report.toString(2))
    check(offers.size == 1 && offers.single().tmdbId == 320754 &&
        offers.single().season == 1 && offers.single().episode == 8) {
        "Live lookup failed: requests=${codes.size} statuses=$codes offers=${offers.size}"
    }
    println("PASS LIVE: real DiziYou lookup returned one corroborated S1E8 offer; requests=${codes.size}; no extractor or stream request")
}
