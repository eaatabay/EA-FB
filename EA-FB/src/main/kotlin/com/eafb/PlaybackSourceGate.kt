package com.eafb

/**
 * A fail-closed boundary between catalog playback identities and source discovery.
 * It is deliberately independent of CloudStream UI, network and D1.
 * A user-enabled source alone does not authorize an adapter: both release rights
 * and current health must independently permit it.
 */
object PlaybackSourceGate {
    fun permitted(
        installed: List<MediaSourceAdapter>,
        releaseApprovedIds: Set<String>,
        userEnabledIds: Set<String>,
        healthyIds: Set<String>
    ): List<MediaSourceAdapter> {
        if (releaseApprovedIds.isEmpty() || userEnabledIds.isEmpty() || healthyIds.isEmpty())
            return emptyList()
        return installed.filter { adapter ->
            adapter.id in releaseApprovedIds &&
                adapter.id in userEnabledIds &&
                adapter.id in healthyIds &&
                !adapter.id.startsWith("fixture-")
        }.distinctBy { it.id }
    }

    fun query(data: String, title: String, year: Int?): MediaQuery? =
        PlaybackQuery.fromData(data, title, year)
}
