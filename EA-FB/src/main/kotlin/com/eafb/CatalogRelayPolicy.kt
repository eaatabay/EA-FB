package com.eafb

/**
 * Public metadata relay pin. A remotely editable GitHub config must never
 * silently redirect film searches or detail requests to an arbitrary origin.
 * Changing the Worker host requires a reviewed plugin release.
 */
object CatalogRelayPolicy {
    const val approvedOrigin = "https://ea-fb-catalog.eaatabay.workers.dev"
    private const val MAX_STALE_MS = 24 * 60 * 60_000L

    fun approved(rawOrigin: String, status: String): String? =
        approvedOrigin.takeIf { status == "ready" && rawOrigin == it }

    /** Use a previously pinned origin briefly during a config fetch outage. */
    fun usableCached(origin: String?, checkedAt: Long, now: Long): String? {
        if (origin != approvedOrigin || checkedAt <= 0L || now < checkedAt) return null
        return origin.takeIf { now - checkedAt <= MAX_STALE_MS }
    }
}
