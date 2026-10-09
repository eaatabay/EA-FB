package com.lagradost.cloudstream3.utils

enum class ExtractorLinkType { VIDEO, M3U8 }
data class ExtractorLink(val url: String, val name: String, val quality: Int, val referer: String,
    val headers: Map<String, String>, val type: ExtractorLinkType)
data class SubtitleFile(val lang: String, val url: String)
object FixtureExtractor {
    var handler: suspend (String, String, (SubtitleFile) -> Unit, (ExtractorLink) -> Unit) -> Boolean =
        { _, _, _, _ -> error("No network allowed in offline extractor tests") }
}
suspend fun loadExtractor(url: String, referer: String, subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit): Boolean = FixtureExtractor.handler(url, referer, subtitleCallback, callback)
