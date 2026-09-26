package com.eafb

/** Keep TV home rows navigable when TMDb returns titles without artwork. */
object CatalogCardPolicy {
    fun hasPoster(path: String?): Boolean = path != null && path.length > 1 && path.startsWith('/')

    /** Poster first, then TMDb backdrop; never show an empty TV card. */
    fun bestArtwork(poster: String?, backdrop: String?): String? =
        listOf(poster, backdrop).firstOrNull { path ->
            path != null && hasPoster(path) && !path.startsWith("//")
        }

    /** TMDb total_pages is authoritative; infer only if omitted. */
    fun hasNext(page: Int, rawResultCount: Int, totalPages: Int): Boolean {
        if (page !in 1..20 || rawResultCount <= 0) return false
        return if (totalPages > 0) page < minOf(totalPages, 20)
        else rawResultCount >= 20 && page < 20
    }
}
