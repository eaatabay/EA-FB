package com.eafb

/** Pure policy for admin-defined metadata shelves. No playback rights or URLs. */
data class CatalogShelfDefinition(
    val id: String,
    val title: String,
    val kind: MediaKind,
    val providerId: Int? = null,
    val region: String? = null,
    val genres: String? = null,
    val language: String? = null,
    val yearFrom: Int? = null,
    val yearTo: Int? = null,
    val order: Int = 0,
    val enabled: Boolean = true
)

object CatalogShelfPolicy {
    private val ids = Regex("[a-z][a-z0-9-]{2,47}")
    private val regions = Regex("[A-Z]{2}")
    private val languages = Regex("[a-z]{2}")
    private val genreIds = Regex("[0-9]{1,4}(,[0-9]{1,4}){0,4}")

    fun category(shelf: CatalogShelfDefinition): CatalogCategory? {
        if (!shelf.enabled || !ids.matches(shelf.id) || shelf.title.length !in 2..64 ||
            shelf.kind == MediaKind.LIVE || shelf.title.any { it == '<' || it == '>' } ||
            (shelf.providerId == null) == (shelf.genres == null) ||
            shelf.language?.let { !languages.matches(it) } == true ||
            shelf.yearFrom?.let { it !in 1888..2100 } == true ||
            shelf.yearTo?.let { it !in 1888..2100 } == true ||
            (shelf.yearFrom != null && shelf.yearTo != null && shelf.yearFrom > shelf.yearTo)
        ) return null
        val path = "/discover/" + if (shelf.kind == MediaKind.SERIES) "tv" else "movie"
        val params = mutableListOf<String>()
        if (shelf.providerId != null) {
            if (shelf.providerId !in 1..999999 || !regions.matches(shelf.region.orEmpty())) return null
            params += "with_watch_providers=${shelf.providerId}"
            params += "watch_region=${shelf.region}"
            params += "with_watch_monetization_types=flatrate"
        } else {
            if (!genreIds.matches(shelf.genres.orEmpty()) || shelf.region != null) return null
            params += "with_genres=${shelf.genres}"
        }
        // The deployed metadata relay currently does not accept language/year
        // filters; retain them as draft metadata until its release is approved.
        if (shelf.language != null || shelf.yearFrom != null || shelf.yearTo != null) return null
        return CatalogCategory(shelf.id, shelf.title, shelf.kind, path + "?" + params.joinToString("&"))
    }
}
