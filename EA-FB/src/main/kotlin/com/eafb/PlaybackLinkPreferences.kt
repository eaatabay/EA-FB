package com.eafb

/**
 * User-facing filters for the manual source selector.
 * Null means "any"; an explicit language/quality never silently falls back
 * to a different selection. The automatic path remains SourcePicker.preferred.
 */
data class PlaybackLinkPreferences(
    val audioLanguage: String? = null,
    val subtitleLanguage: String? = null,
    val maxQuality: Int? = null,
    val providerId: String? = null
) {
    init {
        require(maxQuality == null || maxQuality > 0)
        require(audioLanguage == null || audioLanguage.isNotBlank())
        require(subtitleLanguage == null || subtitleLanguage.isNotBlank())
        require(providerId == null || providerId.isNotBlank())
    }
}

object PlaybackLinkSelector {
    fun select(
        links: List<SourceLink>,
        preferences: PlaybackLinkPreferences,
        nowMillis: Long
    ): List<SourceLink> {
        require(nowMillis >= 0)
        return SourcePicker.unique(links)
            .asSequence()
            .filter { it.url.startsWith("https://") && !it.requiresPrivateSession }
            .filter { it.expiresAtMillis == null || it.expiresAtMillis > nowMillis }
            .filter { preferences.providerId == null || it.provider == preferences.providerId }
            .filter { preferences.audioLanguage == null ||
                it.audioLanguage.equals(preferences.audioLanguage, ignoreCase = true) }
            .filter { preferences.subtitleLanguage == null ||
                it.subtitleLanguage.equals(preferences.subtitleLanguage, ignoreCase = true) }
            .filter { preferences.maxQuality == null ||
                (it.quality != null && it.quality in 1..preferences.maxQuality) }
            .sortedWith(compareByDescending<SourceLink> { it.quality ?: 0 }
                .thenBy { it.provider })
            .toList()
    }
}

/**
 * PLT-style single-provider source list: ordinary adapters first, ClipBox once
 * at the end. ClipBox's internal playback choices are intentionally not expanded
 * into the top-level list. This is presentation-only; it does not authorize,
 * discover, or claim that any stream plays.
 */
object PlaybackSourceList {
    data class Entry(val providerId: String, val links: List<SourceLink>)

    fun group(
        links: List<SourceLink>,
        nowMillis: Long,
        clipBoxProviderId: String = "clipbox"
    ): List<Entry> {
        require(nowMillis >= 0)
        require(clipBoxProviderId.isNotBlank())
        val valid = PlaybackLinkSelector.select(links, PlaybackLinkPreferences(), nowMillis)
        val ordinary = valid.filterNot {
            it.provider.equals(clipBoxProviderId, ignoreCase = true)
        }.groupBy { it.provider.lowercase(java.util.Locale.ROOT) }
        val clipBox = valid.filter {
            it.provider.equals(clipBoxProviderId, ignoreCase = true)
        }
        return ordinary.keys.sorted().map { Entry(it, ordinary.getValue(it)) } +
            if (clipBox.isEmpty()) emptyList() else listOf(Entry(clipBoxProviderId, clipBox))
    }
}
