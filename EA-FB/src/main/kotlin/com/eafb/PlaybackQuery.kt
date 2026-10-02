package com.eafb

/**
 * Pure mapping from CloudStream's stable data string to a provider query.
 * No network I/O, rights changes or local persistence. Invalid IDs fail
 * closed; live channels and the open demonstration movie use their existing
 * separate playback paths.
 */
object PlaybackQuery {
    fun fromData(data: String, title: String, year: Int?): MediaQuery? {
        if (title.isBlank()) return null
        return when (val identity = PlaybackData.parse(data)) {
            is PlaybackData.Movie -> MediaQuery(
                title, year, MediaKind.MOVIE, identity.tmdbId
            )
            is PlaybackData.Episode -> MediaQuery(
                title, year, MediaKind.SERIES, identity.tmdbId,
                identity.season, identity.episode
            )
            null -> null
        }
    }
}
