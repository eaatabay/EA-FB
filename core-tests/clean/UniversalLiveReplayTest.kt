package com.eafb

import java.io.File
import org.json.JSONObject
import com.lagradost.cloudstream3.*
import kotlinx.coroutines.runBlocking

/** Fresh read-only HTTP capture replay through the REAL adapter; never resolve/extract/play. */
fun main() = runBlocking {
    val dir = File(checkNotNull(System.getenv("UNIVERSAL_LIVE_DIR")))
    val catalog = JSONObject(File(dir, "catalog.json").readText())
    val season = catalog.getJSONArray("seasons").getJSONObject(0).getInt("season_number")
    val q = MediaQuery(catalog.getString("name"), catalog.getString("first_air_date").take(4).toInt(),
        MediaKind.SERIES, catalog.getInt("id"), season, 8,
        CatalogMatchMetadata.alternatives(catalog, false), CatalogMatchMetadata.seasonName(catalog, season))
    app.requests.clear()
    app.handler = { request ->
        val text = if (request.method == "POST") File(dir, "you-search.html").readText()
            else if (request.url == File(dir, "you-series.url").readText().trim()) File(dir, "you.html").readText() else ""
        FixtureResponse(text, request.url)
    }
    val offers = MultiSourceEngine(listOf(DiziYouAdapter())).find(q)
    check(offers.size == 1 && offers.single().episode == 8)
    println("PASS: fresh read-only DiziYou capture replay offers=${offers.size} requests=${app.requests.size}; resolver/extractor/player NOT called")
}
