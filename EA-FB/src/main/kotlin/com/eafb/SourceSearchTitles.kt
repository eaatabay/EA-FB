package com.eafb

import org.json.JSONObject

object SourceSearchTitles {
    fun candidates(query: MediaQuery): List<String> {
        val original = (listOf(query.title) + query.alternateTitles)
            .map { it.trim() }.filter { it.isNotBlank() }
            .distinctBy { Identity.normalize(it) }
        // Only the independently confirmed TV/season identity gets an extra exact alias.
        val aliases = if (ConfirmedSeriesAlias.applies(query)) listOf(ConfirmedSeriesAlias.TITLE) else emptyList()
        return (original.take(2) + aliases + original.drop(2))
            .distinctBy { Identity.normalize(it) }.take(3)
    }
}

/** Evidence: docs/series-name-codex/BASLIK-KANITI.json and EA-FB-KATALOG-GOZLEM.json. */
internal object ConfirmedSeriesAlias {
    const val TITLE = "Organize İşler: Karun Hazinesi"
    private fun key(value: String) = Identity.normalize(value.trim().removeSuffix(" izle"))
    fun applies(query: MediaQuery): Boolean =
        query.kind == MediaKind.SERIES && query.tmdbId == 320754 && query.year == 2026 &&
            query.season == 1 && query.episode in 1..8 &&
            key(query.title) in setOf(key("Organize İşler"), key(TITLE))
    fun matches(query: MediaQuery, title: String): Boolean = applies(query) && key(title) == key(TITLE)

    // Newly accepted HTML-source pages need positive TV/year/title corroboration.
    // A missing schema is not guessed from the URL or the requested year.
    fun confirmedPage(query: MediaQuery, heading: String, schemas: List<String>, pageUrl: String): Boolean {
        if (!matches(query, heading)) return false
        return schemas.any { raw ->
            runCatching {
                val item = JSONObject(raw)
                item.optString("@type") == "TVSeries" && matches(query, item.optString("name")) &&
                    item.optString("startDate").matches(Regex("2026(?:-\\d{2}-\\d{2})?")) &&
                    item.optString("url").trimEnd('/') == pageUrl.trimEnd('/')
            }.getOrDefault(false)
        }
    }
}
