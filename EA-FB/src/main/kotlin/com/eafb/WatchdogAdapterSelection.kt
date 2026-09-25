package com.eafb

/**
 * Connects a cryptographically VERIFIED snapshot to code already bundled
 * with the installed .cs3. This policy never fetches a source, downloads code,
 * makes an HTTP request or changes an existing playback session.
 *
 * Never pass an unverified network payload here. The production bridge obtains
 * snapshots only via WatchdogClientStore.restoreVerifiedOffline().
 */
object WatchdogAdapterSelection {
    fun forNewSearch(
        snapshot: VerifiedSourceSnapshot?,
        kind: MediaKind,
        now: Long,
        bundledAdapters: List<VerifiedBaseUrlAdapter>
    ): List<MediaSourceAdapter> {
        if (kind == MediaKind.LIVE || now < 0L || bundledAdapters.isEmpty() ||
            bundledAdapters.size > 32 || snapshot == null ||
            bundledAdapters.map { it.id }.toSet().size != bundledAdapters.size) {
            return emptyList()
        }
        val kindName = when (kind) {
            MediaKind.MOVIE -> "movie"
            MediaKind.SERIES -> "series"
            MediaKind.LIVE -> return emptyList()
        }
        // The signed mediaKind is an authorization boundary. A movie-only
        // source must never be queried for a television series (and vice versa).
        val permitted = snapshot.usableAt(now).filter {
            it.mediaKind == kindName || it.mediaKind == "both"
        }
        if (permitted.isEmpty()) return emptyList()
        return SourceSnapshotGate.forNewSearch(
            snapshot.copy(usableSources = permitted), now, bundledAdapters
        )
    }
}
