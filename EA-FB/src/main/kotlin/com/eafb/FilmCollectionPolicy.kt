package com.eafb

/**
 * Official TMDb belongs_to_collection only; never guess a franchise from
 * a similar title (e.g. different Spider-Man reboot continuities).
 * Unknown dates go last; stable tie-break by TMDb ID.
 */
data class FilmCollectionPart(
    val id: Int,
    val title: String,
    val releaseDate: String?
)

object FilmCollectionPolicy {
    private val date = Regex("\\d{4}-(0[1-9]|1[0-2])-(0[1-9]|[12]\\d|3[01])")

    fun chronological(parts: List<FilmCollectionPart>, ownId: Int): List<FilmCollectionPart> {
        if (ownId <= 0) return emptyList()
        val unique = linkedMapOf<Int, FilmCollectionPart>()
        for (part in parts) {
            if (part.id <= 0 || part.title.isBlank() || unique.containsKey(part.id)) continue
            unique[part.id] = part.copy(
                title = part.title.trim(),
                releaseDate = part.releaseDate?.takeIf { date.matches(it) }
            )
        }
        // A single entry or a collection that excludes the selected film
        // must never turn unrelated TMDb recommendations into "series" titles.
        if (unique.size < 2 || !unique.containsKey(ownId)) return emptyList()
        return unique.values.sortedWith(
            compareBy<FilmCollectionPart>({ it.releaseDate ?: "9999-99-99" }, { it.id })
        )
    }
}
