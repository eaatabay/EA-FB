package com.eafb

import org.json.JSONObject

/** Parse only payloads supplied by the existing trusted catalogue relay. No network or secrets. */
object CatalogMatchMetadata {
    fun alternatives(metadata: JSONObject, movie: Boolean): List<String> = buildList {
        metadata.optString(if (movie) "original_title" else "original_name").takeIf { it.isNotBlank() }?.let(::add)
        val container = metadata.optJSONObject("alternative_titles")
        val rows = container?.optJSONArray(if (movie) "titles" else "results")
        if (rows != null) for (i in 0 until minOf(rows.length(), 30)) {
            rows.optJSONObject(i)?.optString("title")?.takeIf { it.isNotBlank() }?.let(::add)
        }
    }.distinctBy(SourceTitleMatcher::key).take(12)
    fun hasSeason(metadata: JSONObject, number: Int): Boolean {
        val rows = metadata.optJSONArray("seasons") ?: return false
        return (0 until rows.length()).any { rows.optJSONObject(it)?.optInt("season_number", -1) == number }
    }
    fun seasonName(metadata: JSONObject, number: Int): String? {
        val rows = metadata.optJSONArray("seasons") ?: return null
        for (i in 0 until rows.length()) {
            val row = rows.optJSONObject(i) ?: continue
            if (row.optInt("season_number", -1) == number) return SourceTitleMatcher.meaningfulSeason(row.optString("name"))
        }
        return null
    }
}
