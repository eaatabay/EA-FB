package com.eafb

import java.net.URI
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** All three values stay unset in the checked-in public plugin. */
object WatchdogDeliveryConfig {
    const val enabled: Boolean = false
    const val endpoint: String = ""
    const val approvedOrigin: String = ""

    /** A remote setting cannot enable delivery: only reviewed APK-compiled pins can. */
    fun productionOptions() = WatchdogRefreshOptions(
        enabled = enabled,
        endpoint = endpoint,
        approvedOrigin = approvedOrigin,
        trustedKeysAndAdaptersInstalled =
            WatchdogTrustConfig.pinnedPublicKeys.isNotEmpty() &&
            WatchdogTrustConfig.installedAdapterVersions.isNotEmpty()
    )
}

data class WatchdogRefreshOptions(
    val enabled: Boolean = false,
    val endpoint: String = "",
    val approvedOrigin: String = "",
    val trustedKeysAndAdaptersInstalled: Boolean = false
)

data class WatchdogHttpSnapshot(
    val status: Int,
    val contentType: String?,
    val body: ByteArray
)

/** Implementations must NOT follow redirects or send credentials. */
fun interface WatchdogSnapshotTransport {
    suspend fun get(endpoint: String): WatchdogHttpSnapshot
}

data class WatchdogRefreshResult(
    val snapshot: VerifiedSourceSnapshot?,
    val networkAttempted: Boolean,
    val status: String,
    val retryAt: Long?
)

/**
 * Explicit, safe on-demand refresh. Does not start a timer, fetch on plugin
 * load or bypass the two independent production approval gates. Network
 * transport is injected; all source bytes require the pinned Ed25519 verifier
 * in WatchdogClientStore before either use or durable storage.
 */
class WatchdogSnapshotRefresh(
    private val options: WatchdogRefreshOptions,
    private val transport: WatchdogSnapshotTransport,
    private val acceptSigned: (String, Long) -> SnapshotCheck,
    private val restoreOffline: (Long) -> VerifiedSourceSnapshot?
) {
    private val mutex = Mutex()
    private var lastAttempt: Long? = null
    private var failureCount = 0
    private var nextAttempt: Long = 0L

    suspend fun refresh(now: Long): WatchdogRefreshResult = mutex.withLock {
        if (!isApprovedConfiguration(options) || now < 0L || now > MAX_SAFE) {
            return@withLock WatchdogRefreshResult(null, false, "not_configured", null)
        }
        val previous = lastAttempt
        if (previous != null && now < previous) {
            // A backwards wall-clock jump must not trigger an old signed list.
            return@withLock WatchdogRefreshResult(null, false, "clock_rollback", null)
        }
        val cached = restoreOffline(now)?.takeIf { it.usableAt(now).isNotEmpty() }
        if (now < nextAttempt) {
            return@withLock WatchdogRefreshResult(cached, false, "throttled", nextAttempt)
        }
        lastAttempt = now
        val reply = try { transport.get(options.endpoint) } catch (_: Exception) {
            return@withLock failure(now, cached, "network_unavailable")
        }
        if (reply.status != 200) {
            // Redirects (including 304/Location) are never followed or trusted.
            return@withLock failure(now, cached, "http_unavailable")
        }
        val contentType = reply.contentType?.trim().orEmpty()
        if (!contentType.matches(Regex("application/json(?:\\s*;\\s*charset=utf-8)?",
                RegexOption.IGNORE_CASE)) || reply.body.isEmpty() ||
            reply.body.size > MAX_BODY_BYTES) {
            return@withLock failure(now, cached, "invalid_response")
        }
        val json = try {
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(reply.body)).toString()
        } catch (_: CharacterCodingException) {
            return@withLock failure(now, cached, "invalid_response")
        }
        val verified = try { acceptSigned(json, now) } catch (_: Exception) {
            SnapshotCheck.Rejected("invalid_envelope")
        }
        if (verified !is SnapshotCheck.Accepted) {
            return@withLock failure(now, cached, "untrusted_snapshot")
        }
        failureCount = 0
        nextAttempt = (now + MIN_REFRESH_MS).coerceAtMost(MAX_SAFE)
        WatchdogRefreshResult(verified.snapshot, true, "updated", nextAttempt)
    }

    private fun failure(now: Long, cached: VerifiedSourceSnapshot?, reason: String): WatchdogRefreshResult {
        failureCount = (failureCount + 1).coerceAtMost(4)
        val delay = (MIN_REFRESH_MS * (1L shl (failureCount - 1))).coerceAtMost(MAX_RETRY_MS)
        nextAttempt = (now + delay).coerceAtMost(MAX_SAFE)
        return WatchdogRefreshResult(cached, true, reason, nextAttempt)
    }

    companion object {
        const val MAX_BODY_BYTES = 32_768
        private const val MAX_SAFE = 9_007_199_254_740_991L
        private const val MIN_REFRESH_MS = 15 * 60_000L
        private const val MAX_RETRY_MS = 60 * 60_000L

        /** Endpoint host must exactly match the separately reviewed origin. */
        fun isApprovedConfiguration(options: WatchdogRefreshOptions): Boolean {
            if (!options.enabled || !options.trustedKeysAndAdaptersInstalled ||
                options.endpoint.length !in 24..256 || options.approvedOrigin.length !in 12..200) {
                return false
            }
            return try {
                val endpoint = URI(options.endpoint)
                val origin = URI(options.approvedOrigin)
                val host = endpoint.host?.lowercase() ?: return false
                val safeHost = host.matches(Regex("[a-z0-9-]+(\\.[a-z0-9-]+)+")) &&
                    host !in setOf("localhost") &&
                    listOf(".local", ".localhost", ".internal", ".invalid")
                        .none { host.endsWith(it) } &&
                    !host.matches(Regex("[0-9]+(\\.[0-9]+){3}"))
                safeHost && endpoint.scheme == "https" && endpoint.port == -1 &&
                    endpoint.userInfo == null && endpoint.rawQuery == null &&
                    endpoint.rawFragment == null && endpoint.rawPath == "/v1/sources" &&
                    endpoint.toASCIIString() == options.endpoint &&
                    origin.scheme == "https" && origin.port == -1 &&
                    origin.userInfo == null && origin.rawQuery.isNullOrEmpty() &&
                    origin.rawFragment == null && origin.rawPath.isNullOrEmpty() &&
                    origin.host?.lowercase() == host &&
                    options.approvedOrigin == "https://$host"
            } catch (_: Exception) { false }
        }
    }
}
