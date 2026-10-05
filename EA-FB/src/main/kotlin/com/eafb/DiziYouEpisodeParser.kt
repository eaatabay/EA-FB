package com.eafb

import java.net.URI

/** Pure DiziYou episode identity and URL validation; no network or player access. */
object DiziYouEpisodeParser {
    private val season = Regex("""(\d+)\.\s*Sezon""", RegexOption.IGNORE_CASE)
    private val episode = Regex("""(\d+)\.\s*Bölüm""", RegexOption.IGNORE_CASE)
    private val itemId = Regex("""^[a-zA-Z0-9_-]{1,128}$""")

    fun exactEpisode(heading: String, expectedSeason: Int, expectedEpisode: Int): Boolean =
        season.find(heading)?.groupValues?.get(1)?.toIntOrNull() == expectedSeason &&
            episode.find(heading)?.groupValues?.get(1)?.toIntOrNull() == expectedEpisode

    /**
     * Match the working DiziYou plugin's player-id extraction without trusting
     * an arbitrary player URL. Current embeds may use another DiziYou subdomain
     * and may carry a query/fragment, so only require HTTPS + the DiziYou domain
     * family and extract the final <id>.html path segment.
     */
    fun playerId(iframeUrl: String, expectedHost: String): String? {
        return try {
            val uri = URI(iframeUrl)
            val expectedDomain = expectedHost.removePrefix("www.")
            val host = uri.host?.lowercase()
            if (uri.scheme != "https" || uri.userInfo != null || host == null ||
                !(host == expectedDomain || host.endsWith(".$expectedDomain"))) null
            else {
                val name = uri.path.substringAfterLast('/')
                if (!name.endsWith(".html", ignoreCase = true)) null
                else name.substringBeforeLast('.').takeIf { itemId.matches(it) }
            }
        } catch (_: Exception) { null }
    }
}
