package com.eafb

/** Sorting applies to TMDb discover feeds (platforms and genres).
 * Trending, charts and cinema lists retain their own meaningful native order.
 */
enum class CatalogSortMode(val key: String, val title: String) {
    POPULAR("popular", "Popüler"),
    NEWEST("newest", "En Yeni"),
    HIGHEST_RATED("highest_rated", "Puanı Yüksek");

    companion object {
        fun fromKey(value: String?): CatalogSortMode =
            entries.firstOrNull { it.key == value } ?: POPULAR
    }
}

object CatalogSortPolicy {
    fun route(path: String, kind: MediaKind, mode: CatalogSortMode): String {
        if (!path.startsWith("/discover/")) return path
        val sortBy = when (mode) {
            CatalogSortMode.POPULAR -> "popularity.desc"
            CatalogSortMode.NEWEST -> if (kind == MediaKind.SERIES)
                "first_air_date.desc" else "primary_release_date.desc"
            CatalogSortMode.HIGHEST_RATED -> "vote_average.desc"
        }
        // Remove previous sort/vote filters, retaining platform and genre identity.
        val parts = path.split("&").filterNot {
            it.startsWith("sort_by=") || it.startsWith("vote_count.gte=") ||
                it.startsWith("first_air_date.lte=") || it.startsWith("primary_release_date.lte=")
        }
        // Keep v6 compatible with the currently deployed v5 relay: it may
        // reject new date-bound parameters. EAProvider filters future titles
        // locally; a labeled popular fallback handles empty newest pages.
        // Sparse regional provider feeds should not vanish due to a 100-vote gate.
        val minimumVotes = if (mode == CatalogSortMode.HIGHEST_RATED)
            "&vote_count.gte=10" else ""
        return parts.joinToString("&") + "&sort_by=" + sortBy + minimumVotes
    }
}
