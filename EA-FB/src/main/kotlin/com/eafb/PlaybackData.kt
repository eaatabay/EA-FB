package com.eafb

/**
 * Stable, non-secret CloudStream playback identity. The string identifies a
 * TMDb movie or an exact TV episode, never a direct stream URL or entitlement.
 * All source resolution still requires an approved adapter at playback time.
 */
sealed class PlaybackData {
    data class Movie(val tmdbId: Int) : PlaybackData()
    data class Episode(val tmdbId: Int, val season: Int, val episode: Int) : PlaybackData()

    companion object {
        fun movie(tmdbId: Int): String {
            require(tmdbId > 0)
            return "ea-fb:movie:$tmdbId"
        }

        fun episode(tmdbId: Int, season: Int, episode: Int): String {
            require(tmdbId > 0 && season >= 0 && episode > 0)
            return "ea-fb:episode:$tmdbId:$season:$episode"
        }

        fun parse(value: String): PlaybackData? {
            val parts = value.split(':')
            if (parts.size !in 3..5 || parts[0] != "ea-fb") return null
            fun strictInt(text: String): Int? =
                text.toIntOrNull()?.takeIf { it >= 0 && it.toString() == text }
            return when {
                parts.size == 3 && parts[1] == "movie" ->
                    strictInt(parts[2])?.takeIf { it > 0 }?.let(::Movie)
                parts.size == 5 && parts[1] == "episode" -> {
                    val id = strictInt(parts[2])
                    val season = strictInt(parts[3])
                    val episode = strictInt(parts[4])
                    if (id != null && id > 0 && season != null &&
                        episode != null && episode > 0) Episode(id, season, episode)
                    else null
                }
                else -> null
            }
        }
    }
}
