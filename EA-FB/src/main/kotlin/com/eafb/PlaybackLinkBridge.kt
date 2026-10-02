package com.eafb

/**
 * Explicit, immutable playback adapter configuration. No UI setting or
 * remotely supplied flag can add an adapter or grant distribution rights.
 * Only adapters included in a reviewed release can enter installedAdapters.
 */
class PlaybackSourceRuntime(
    private val installedAdapters: List<MediaSourceAdapter>,
    private val releaseApprovedIds: Set<String>,
    private val userEnabledIds: Set<String>,
    private val healthyIds: Set<String>
) {
    init {
        require(installedAdapters.map { it.id }.distinct().size == installedAdapters.size)
        require(installedAdapters.none { it.id.isBlank() })
    }

    companion object {
        /**
         * A fresh health snapshot is required when assembling a runtime.
         * A healthy observation never grants rights or enables a source.
         */
        fun fromHealthObservations(
            installedAdapters: List<MediaSourceAdapter>,
            releaseApprovedIds: Set<String>,
            userEnabledIds: Set<String>,
            observations: List<SourceHealthObservation>,
            nowMillis: Long,
            ttlMillis: Long = 15 * 60 * 1000L
        ): PlaybackSourceRuntime = PlaybackSourceRuntime(
            installedAdapters, releaseApprovedIds, userEnabledIds,
            PlaybackSourceHealth.healthyIds(observations, nowMillis, ttlMillis)
        )
    }

    private fun permitted(): List<MediaSourceAdapter> =
        PlaybackSourceGate.permitted(
            installedAdapters, releaseApprovedIds, userEnabledIds, healthyIds
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
            data, title, year, permitted(), releaseApprovedIds,
            userEnabledIds, healthyIds, nowMillis
        )
    }
}

/**
 * V49-compatible default: no unreviewed sources, no network discovery,
 * no credential sharing. Wiring a reviewed runtime is a separate release step.
 */
object PlaybackLinkBridge {
    private val runtime = PlaybackSourceRuntime(
        installedAdapters = emptyList(),
        releaseApprovedIds = emptySet(),
        userEnabledIds = emptySet(),
        healthyIds = emptySet()
    )

    fun isCatalogIdentity(data: String): Boolean = PlaybackData.parse(data) != null

    fun canResolve(data: String): Boolean = runtime.canResolve(data)

    suspend fun alternatives(
        data: String,
        title: String,
        year: Int?,
        nowMillis: Long
    ): List<SourceLink> = runtime.alternatives(data, title, year, nowMillis)
}
