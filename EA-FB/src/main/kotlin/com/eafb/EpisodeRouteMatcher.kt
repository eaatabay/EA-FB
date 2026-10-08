package com.eafb

/** Match an explicit episode route, never silently choose a different episode. */
object EpisodeRouteMatcher {
    private fun clean(value: String): String = value.lowercase()
        .replace('ö', 'o').replace('ü', 'u').replace('ı', 'i')
        .replace('ş', 's').replace('ğ', 'g').replace('ç', 'c')

    private fun pair(value: String): Pair<Int, Int>? {
        val compact = clean(value)
        val sx = Regex("""(?:^|[^a-z0-9])s(\d{1,2})e(\d{1,3})(?:[^0-9]|$)""").find(compact)
        if (sx != null) return sx.groupValues[1].toInt() to sx.groupValues[2].toInt()
        val x = Regex("""(?:^|[^0-9])(\d{1,2})x(\d{1,3})(?:[^0-9]|$)""").find(compact)
        return x?.let { it.groupValues[1].toInt() to it.groupValues[2].toInt() }
    }

    private fun number(value: String, word: String): Int? {
        val normal = clean(value)
        return Regex("""(?:^|[^0-9])(\d{1,3})[._-]\s*""" + word)
            .find(normal)?.groupValues?.get(1)?.toIntOrNull()
            ?: Regex(word + """[.\s_/-]*(\d{1,3})(?:[^0-9]|$)""")
                .find(normal)?.groupValues?.get(1)?.toIntOrNull()
            ?: Regex("""(?:^|[^0-9])(\d{1,3})[\s_-]+""" + word)
                .find(normal)?.groupValues?.get(1)?.toIntOrNull()
    }

    fun matches(label: String, href: String, season: Int, episode: Int): Boolean {
        if (season < 0 || episode < 1) return false
        val fromLabel = pair(label)
        val fromUrl = pair(href)
        val detectedSeason = fromLabel?.first ?: fromUrl?.first
            ?: number(label, "sezon") ?: number(href, "sezon")
            ?: number(label, "season") ?: number(href, "season")
        val detectedEpisode = fromLabel?.second ?: fromUrl?.second
            ?: number(label, "bolum") ?: number(href, "bolum")
            ?: number(label, "episode") ?: number(href, "episode")
        return detectedEpisode == episode &&
            (detectedSeason == season || (detectedSeason == null && season == 1))
    }
}
