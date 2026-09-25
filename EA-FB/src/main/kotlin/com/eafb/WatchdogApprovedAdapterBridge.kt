package com.eafb

/**
 * Explicit future integration point for EAProvider. The current public v6
 * package pins NO signing keys, approves NO Watchdog endpoint and bundles NO
 * external source adapters; therefore this bridge returns an empty list.
 * Constructing it never starts a timer or performs network I/O.
 */
class WatchdogApprovedAdapterBridge(
    private val store: WatchdogClientStore,
    private val bundledAdapters: List<VerifiedBaseUrlAdapter>
) {
    /** Resolve only on a NEW search, not during an already-started stream. */
    fun forNewSearch(kind: MediaKind, now: Long): List<MediaSourceAdapter> {
        val options = WatchdogDeliveryConfig.productionOptions()
        if (!WatchdogSnapshotRefresh.isApprovedConfiguration(options) ||
            bundledAdapters.isEmpty() || bundledAdapters.size > 32) {
            return emptyList()
        }
        // Runtime bundle must match exactly the adapters explicitly approved
        // in this .cs3. A remote signed list cannot introduce executable code.
        val installed = WatchdogTrustConfig.installedAdapterVersions
        if (bundledAdapters.size != installed.size ||
            bundledAdapters.map { it.id }.toSet() != installed.keys ||
            bundledAdapters.any { installed[it.id] != it.adapterVersion }) {
            return emptyList()
        }
        val snapshot = try { store.restoreVerifiedOffline(now) }
            catch (_: Exception) { null }
        return WatchdogAdapterSelection.forNewSearch(
            snapshot, kind, now, bundledAdapters
        )
    }
}
