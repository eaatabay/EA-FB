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

    fun playerId(iframeUrl: String, expectedHost: String): String? {
        return try {
            val uri = URI(iframeUrl)
            if (uri.scheme != "https" || !uri.host.equals(expectedHost, ignoreCase = true) ||
                uri.userInfo != null || uri.query != null || uri.fragment != null) null
            else {
                val name = uri.path.substringAfterLast('/')
                if (!name.endsWith(".html")) null
                else name.removeSuffix(".html").takeIf { itemId.matches(it) }
            }
        } catch (_: Exception) { null }
    }
}
