package com.eafb

/**
 * CloudStream-independent entry point for future catalog playback.
 *
 * No source is activated merely by shipping this code. An independently
 * reviewed release must provide approved adapter IDs, user preferences and
 * current health. The default V49-compatible state is deliberately empty.
 */
object PlaybackLinkBridge {
    private val installedAdapters: List<MediaSourceAdapter> = emptyList()
    private val releaseApprovedIds: Set<String> = emptySet()
    private val userEnabledIds: Set<String> = emptySet()
    private val healthyIds: Set<String> = emptySet()

    fun isCatalogIdentity(data: String): Boolean = PlaybackData.parse(data) != null

    suspend fun alternatives(
        data: String,
        title: String,
        year: Int?,
        nowMillis: Long
    ): List<SourceLink> {
        if (!isCatalogIdentity(data) || installedAdapters.isEmpty() ||
            releaseApprovedIds.isEmpty() || userEnabledIds.isEmpty() ||
            healthyIds.isEmpty()) return emptyList()
        return PlaybackSourceGate.alternatives(
            data, title, year, installedAdapters, releaseApprovedIds,
            userEnabledIds, healthyIds, nowMillis
        )
    }
}
