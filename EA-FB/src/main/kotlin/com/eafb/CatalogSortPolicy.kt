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
        if (!path.startsWith("/discover/movie?") && !path.startsWith("/discover/tv?")) return path
        // POPULAR must use the exact previously working v5 route. Do not
        // add a redundant sort parameter to every platform/genre request.
        if (mode == CatalogSortMode.POPULAR) return path
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
        // Deployed v5 relay rejects vote_count.gte; do not require a v6 deploy.
        return parts.joinToString("&") + "&sort_by=" + sortBy
    }
}
