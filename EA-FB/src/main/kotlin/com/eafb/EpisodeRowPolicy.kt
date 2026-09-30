package com.eafb

/** Pure row/selector parsing shared by the Android decorator and offline tests. */
object EpisodeRowPolicy {
    fun episodeNumber(text: String, localizedEpisodeWord: String? = null): Int? {
        val value = text.trim()
        Regex("""^(\d+)\.\s*""").find(value)
            ?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?.let { return it.takeIf { number -> number > 0 } }

        val words = listOfNotNull(localizedEpisodeWord?.trim()?.takeIf { it.isNotEmpty() }) +
            listOf("Episode", "Bölüm")
        words.distinct().forEach { word ->
            Regex(
                "^" + Regex.escape(word) + """\s+(\d+)(?:\s|$|[.:])""",
                RegexOption.IGNORE_CASE
            ).find(value)?.groupValues?.getOrNull(1)?.toIntOrNull()
                ?.let { return it.takeIf { number -> number > 0 } }
        }
        return null
    }

    fun rowName(text: String, localizedEpisodeWord: String? = null): String {
        val value = text.trim()
        Regex("""^\d+\.\s*""").find(value)?.let { return value.removeRange(it.range).trim() }

        val words = listOfNotNull(localizedEpisodeWord?.trim()?.takeIf { it.isNotEmpty() }) +
            listOf("Episode", "Bölüm")
        words.distinct().forEach { word ->
            Regex(
                "^" + Regex.escape(word) + """\s+\d+(?:\s|$|[.:])\s*""",
                RegexOption.IGNORE_CASE
            ).find(value)?.let { return value.removeRange(it.range).trim() }
        }
        return ""
    }

    fun seasonNumber(text: String, localizedSeasonWord: String? = null): Int? {
        val value = text.trim()
        val words = listOfNotNull(localizedSeasonWord?.trim()?.takeIf { it.isNotEmpty() }) +
            listOf("Season", "Sezon")
        words.distinct().forEach { word ->
            val escaped = Regex.escape(word)
            Regex("^" + escaped + """\s+(\d+)(?:\b.*)?$""", RegexOption.IGNORE_CASE)
                .matchEntire(value)?.groupValues?.getOrNull(1)?.toIntOrNull()
                ?.let { return it.takeIf { number -> number >= 0 } }
            Regex("""^(\d+)\.?\s*""" + escaped + """(?:\b.*)?$""", RegexOption.IGNORE_CASE)
                .matchEntire(value)?.groupValues?.getOrNull(1)?.toIntOrNull()
                ?.let { return it.takeIf { number -> number >= 0 } }
        }
        return null
    }
}
