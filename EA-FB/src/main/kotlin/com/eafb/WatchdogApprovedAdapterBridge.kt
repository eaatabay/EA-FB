package com.eafb

import kotlinx.coroutines.CancellationException

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
        // An empty release-compiled rights registry is a hard stop, even if
        // a future build enables delivery or accidentally bundles adapters.
        // Never read private cached snapshot data for an unlicensed release.
        if (kind == MediaKind.LIVE || now < 0L ||
            ReviewedSourcePermits.bundled.isEmpty()) return emptyList()
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
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { null }
        // Cryptographic validity does not by itself grant distribution rights.
        // Every signed source also needs an unexpired, release-compiled
        // rights/host/path permit; default v6 ships an EMPTY permit list.
        val reviewed = ReviewedSourcePermitPolicy.restrict(
            snapshot, now, ReviewedSourcePermits.bundled
        )
        return WatchdogAdapterSelection.forNewSearch(
            reviewed, kind, now, bundledAdapters
        )
    }
}
