package com.eafb

/**
 * Pure handoff between a CloudStream playback identity and the existing
 * approved-adapter source engine. This component does not install adapters,
 * grant source permissions, store URLs, or report playback success.
 *
 * A caller must supply metadata from the already loaded movie/episode and
 * explicitly pass the approved offers returned by its own source discovery.
 */
class PlaybackResolution(private val engine: MultiSourceEngine) {
    suspend fun resolve(
        data: String,
        title: String,
        year: Int?,
        approvedOffers: List<MediaOffer>,
        nowMillis: Long,
        preferredProviderIds: List<String> = emptyList(),
        preferredLanguage: String = "tr",
        maxQuality: Int = 1080
    ): List<SourceLink> {
        val query = PlaybackQuery.fromData(data, title, year) ?: return emptyList()
        if (approvedOffers.isEmpty()) return emptyList()
        return engine.resolveFirstAvailable(
            query = query,
            offers = approvedOffers,
            nowMillis = nowMillis,
            preferredProviderIds = preferredProviderIds,
            preferredLanguage = preferredLanguage,
            maxQuality = maxQuality
        )
    }
}
