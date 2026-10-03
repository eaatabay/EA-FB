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
        listOf(DiziYouAdapter(), DiziBoxAdapter())

    /** Re-read persisted switches for each playback request. A fresh health
     * observation is still required; an enabled switch alone is not health. */
    private fun runtime(nowMillis: Long): PlaybackSourceRuntime =
        PlaybackSourceRuntime.fromHealthObservations(
            installedAdapters = installedAdapters,
            userEnabledIds = installedAdapters.map { it.id }
                .filter { EASettings.sourceEnabled(it.id) }.toSet(),
            observations = emptyList(),
            nowMillis = nowMillis
        )

    fun isCatalogIdentity(data: String): Boolean = PlaybackData.parse(data) != null

    fun canResolve(data: String): Boolean = runtime(System.currentTimeMillis()).canResolve(data)

    suspend fun alternatives(
        data: String,
        title: String,
        year: Int?,
        nowMillis: Long
    ): List<SourceLink> = runtime(nowMillis).alternatives(data, title, year, nowMillis)

    suspend fun sourceGroups(
        data: String,
        title: String,
        year: Int?,
        nowMillis: Long
    ): List<PlaybackSourceList.Entry> = runtime(nowMillis).sourceGroups(data, title, year, nowMillis)
}
