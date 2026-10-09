package com.eafb

/**
 * Explicit, immutable playback adapter configuration. No UI setting or
 * remotely supplied flag can install an adapter.
 * Only bundled adapters can enter installedAdapters.
 */
class PlaybackSourceRuntime(
    installedAdapters: List<MediaSourceAdapter>,
    userEnabledIds: Set<String>,
    healthyIds: Set<String>
) {
    private val installedAdapters = installedAdapters.toList()
    private val userEnabledIds = userEnabledIds.toSet()
    private val healthyIds = healthyIds.toSet()

    init {
        require(installedAdapters.map { it.id }.distinct().size == installedAdapters.size)
        require(installedAdapters.none { it.id.isBlank() })
    }

    companion object {
        /**
         * A fresh health snapshot is required when assembling a runtime.
         * A healthy observation never enables a source by itself.
         */
        fun fromHealthObservations(
            installedAdapters: List<MediaSourceAdapter>,
            userEnabledIds: Set<String>,
            observations: List<SourceHealthObservation>,
            nowMillis: Long,
            ttlMillis: Long = 15 * 60 * 1000L
        ): PlaybackSourceRuntime = PlaybackSourceRuntime(
            installedAdapters, userEnabledIds,
            PlaybackSourceHealth.healthyIds(observations, nowMillis, ttlMillis)
        )
    }

    private fun permitted(): List<MediaSourceAdapter> =
        PlaybackSourceGate.permitted(
            installedAdapters, userEnabledIds, healthyIds
        )

    fun canResolve(data: String): Boolean =
        PlaybackData.parse(data) != null && permitted().isNotEmpty()

    suspend fun alternatives(
        data: String,
        title: String,
        year: Int?,
        nowMillis: Long
    ): List<SourceLink> {
        if (!canResolve(data)) return emptyList()
        return PlaybackSourceGate.alternatives(
            data, title, year, permitted(),
            userEnabledIds, healthyIds, nowMillis
        )
    }

    /** One entry per normal provider; ClipBox remains one final entry. */
    suspend fun sourceGroups(
        data: String,
        title: String,
        year: Int?,
        nowMillis: Long
    ): List<PlaybackSourceList.Entry> = PlaybackSourceList.group(
        alternatives(data, title, year, nowMillis), nowMillis
    )
}

/**
 * Test-branch default: candidates are bundled but remain user-disabled until
 * enabled through settings; no credentials are shared.
 */
object PlaybackLinkBridge {
    private val installedAdapters: List<MediaSourceAdapter> =
        listOf(DiziYouAdapter(), DiziBoxAdapter(), HDFilmCehennemiLandAdapter())

    private fun enabledAdapters(): List<MediaSourceAdapter> =
        installedAdapters.filter { EASettings.sourceEnabled(it.id) }

    fun isCatalogIdentity(data: String): Boolean = PlaybackData.parse(data) != null

    // This is a preflight eligibility check, not a claim of source health.
    // Actual health is determined by fresh successful resolution below.
    fun canResolve(data: String): Boolean =
        isCatalogIdentity(data) && enabledAdapters().isNotEmpty()

    suspend fun alternatives(
        data: String,
        title: String,
        year: Int?,
        nowMillis: Long
    ): List<SourceLink> {
        val query = PlaybackQuery.fromData(data, title, year) ?: return emptyList()
        val enabled = enabledAdapters()
        if (enabled.isEmpty()) return emptyList()
        val engine = MultiSourceEngine(enabled)
        val offers = engine.find(query)
        if (offers.isEmpty()) return emptyList()
        // Freshly resolved, non-expired HTTPS links are the positive health
        // observation. Failed searches or resolutions never become healthy.
        val resolved = engine.resolve(offers, nowMillis)
        val observations = enabled.map { adapter ->
            SourceHealthObservation(adapter.id,
                resolved.any { it.provider == adapter.id }, nowMillis)
        }
        val healthy = PlaybackSourceHealth.healthyIds(observations, nowMillis)
        return resolved.filter { it.provider in healthy }
    }

    suspend fun sourceGroups(
        data: String,
        title: String,
        year: Int?,
        nowMillis: Long
    ): List<PlaybackSourceList.Entry> =
        PlaybackSourceList.group(alternatives(data, title, year, nowMillis), nowMillis)
}
