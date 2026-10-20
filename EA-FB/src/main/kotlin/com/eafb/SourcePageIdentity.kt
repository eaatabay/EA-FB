package com.eafb

import org.json.JSONObject
import org.json.JSONArray
import org.jsoup.nodes.Document

/** Several independent markup paths; no mandatory JSON-LD tag and no whole-page year guessing. */
object SourcePageIdentity {
    fun read(doc: Document, typeHint: MediaKind?, fallbackTitle: String = ""): SourceIdentity {
        val names = mutableListOf<String>(); val years = mutableSetOf<Int>(); val visibleYears = mutableSetOf<Int>(); val types = mutableSetOf<MediaKind>()
        fun year(raw: String) = Regex("(?<!\\d)(?:18|19|20)\\d{2}(?!\\d)").find(raw)?.value?.toIntOrNull()
        fun schema(value: Any?) {
            when (value) {
                is JSONArray -> (0 until value.length()).forEach { schema(value.opt(it)) }
                is JSONObject -> {
                    val type = value.opt("@type")
                    val tags = if (type is JSONArray) (0 until type.length()).map { type.optString(it) } else listOf(type?.toString())
                    val kind = when { "Movie" in tags -> MediaKind.MOVIE; "TVSeries" in tags -> MediaKind.SERIES; else -> null }
                    if (kind != null) {
                        types += kind; value.optString("name").takeIf { it.isNotBlank() }?.let(names::add)
                        year(value.optString(if (kind == MediaKind.SERIES) "startDate" else "datePublished"))?.let(years::add)
                    }
                    schema(value.opt("@graph"))
                }
            }
        }
        doc.select("script[type=application/ld+json]").take(4).forEach {
            runCatching { val text = it.data().trim(); schema(if (text.startsWith("[")) JSONArray(text) else JSONObject(text)) }
        }
        val heading = doc.selectFirst("div.tv-overview h1, h1.entry-title, h1")?.text().orEmpty()
        val title = heading.ifBlank { names.firstOrNull() ?: fallbackTitle }
        // Deliberately scope selectors to publication metadata, never episode dates or footer years.
        doc.select("[itemprop=startDate], .tv-overview .year, .tv-overview .release-year, .release-year, meta[property=video:release_date], meta[itemprop=datePublished]")
            .take(8).forEach { node -> year(node.attr("content").ifBlank { node.attr("datetime") }.ifBlank { node.text() })?.let(visibleYears::add) }
        val info = doc.select(".tv-overview, .info, .details, .entry-content, .series-info").take(6).joinToString(" ") { it.text() }
        val labelled = Regex("(?:yap[iı]m y[iı]l[iı]|ilk yay[iı]n(?: tarihi| y[iı]l[iı])?|release year|first air date)\\s*[:：]?\\s*((?:18|19|20)\\d{2})", RegexOption.IGNORE_CASE)
        labelled.findAll(info).forEach { it.groupValues[1].toIntOrNull()?.let(visibleYears::add) }
        val kind = types.singleOrNull() ?: typeHint
        // A generic visible year can describe the current season. Do not replace a TVSeries startDate.
        if (kind != MediaKind.SERIES || years.isEmpty()) years += visibleYears
        if (kind == MediaKind.SERIES) {
            Regex("(?:ilk yay[iı]n(?: tarihi| y[iı]l[iı])?|first air date)\\s*[:：]?\\s*((?:18|19|20)\\d{2})", RegexOption.IGNORE_CASE)
                .findAll(info).forEach { it.groupValues[1].toIntOrNull()?.let(years::add) }
        }
        return SourceIdentity(title, kind, years.singleOrNull(), conflicting = types.size > 1 || years.size > 1, observedNames = names)
    }
}
