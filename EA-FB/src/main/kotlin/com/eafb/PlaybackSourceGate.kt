package com.eafb

/**
 * A fail-closed boundary between catalog playback identities and source discovery.
 * It is deliberately independent of CloudStream UI, network and D1.
 * Only installed, user-enabled, healthy sources participate in discovery.
 */
object PlaybackSourceGate {
    fun permitted(
        installed: List<MediaSourceAdapter>,
        userEnabledIds: Set<String>,
        healthyIds: Set<String>
    ): List<MediaSourceAdapter> {
        if (userEnabledIds.isEmpty() || healthyIds.isEmpty())
            return emptyList()
        return installed.filter { adapter ->
                adapter.id in userEnabledIds &&
                adapter.id in healthyIds &&
                !adapter.id.startsWith("fixture-")
        }.distinctBy { it.id }
    }

    /**
     * Produce alternate links for the player's manual source selector.
     * Each link is freshly resolved by an independently permitted adapter.
     * Unlike resolve(), this does not stop at the first successful source.
     */
    suspend fun alternatives(
        data: String,
        title: String,
        year: Int?,
        installed: List<MediaSourceAdapter>,
        userEnabledIds: Set<String>,
        healthyIds: Set<String>,
        nowMillis: Long,
        preferredLanguage: String = "tr",
        maxQuality: Int = 1080
    ): List<SourceLink> {
        val media = query(data, title, year) ?: return emptyList()
        val approved = permitted(installed, userEnabledIds, healthyIds)
        if (approved.isEmpty()) return emptyList()
        val engine = MultiSourceEngine(approved)
        val offers = engine.find(media)
        if (offers.isEmpty()) return emptyList()
        return engine.resolve(offers, nowMillis, preferredLanguage, maxQuality)
    }

    fun query(data: String, title: String, year: Int?): MediaQuery? =
        PlaybackQuery.fromData(data, title, year)

    /**
     * Discovery and resolution require user-enabled, healthy installed sources. This pure orchestration layer does not change V49's UI,
     * register adapters, grant rights, persist URLs or attest playback.
     */
    suspend fun resolve(
        data: String,
        title: String,
        year: Int?,
        installed: List<MediaSourceAdapter>,
        userEnabledIds: Set<String>,
        healthyIds: Set<String>,
        nowMillis: Long,
        preferredProviderIds: List<String> = emptyList(),
        preferredLanguage: String = "tr",
        maxQuality: Int = 1080
    ): List<SourceLink> {
        val media = query(data, title, year) ?: return emptyList()
        val approved = permitted(installed, userEnabledIds, healthyIds)
        if (approved.isEmpty()) return emptyList()
        val engine = MultiSourceEngine(approved)
        val offers = engine.find(media)
        if (offers.isEmpty()) return emptyList()
        val approvedIds = approved.map { it.id }.toSet()
        val orderedIds = preferredProviderIds.filter { it in approvedIds }.distinct()
        return engine.resolveFirstAvailable(
            media, offers, nowMillis, orderedIds, preferredLanguage, maxQuality
        )
    }
}
