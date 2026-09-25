package com.eafb

import android.content.Context
import java.nio.charset.StandardCharsets

/**
 * All replay metadata and the signed cache are committed atomically. The
 * offline cache is never an authority: its signature, exact high-water marks,
 * expiry and bundled adapter versions are rechecked on each restoration.
 */
class WatchdogClientStore(
    context: Context,
    private val verifier: SourceSnapshotTrust = SourceSnapshotTrust(
        WatchdogTrustConfig.pinnedPublicKeys,
        WatchdogTrustConfig.installedAdapterVersions
    ),
    private val parseEnvelope: (String) -> SignedSourceEnvelope? = WatchdogSnapshotJson::parse
) {
    private val preferences = context.getSharedPreferences(
        "ea_fb_watchdog_trust_v1", Context.MODE_PRIVATE
    )

    @Synchronized
    fun acceptSignedJson(raw: String, now: Long): SnapshotCheck {
        if (raw.length > 32_768 ||
            raw.toByteArray(StandardCharsets.UTF_8).size > 32_768) {
            return SnapshotCheck.Rejected("invalid_envelope")
        }
        val envelope = runCatching { parseEnvelope(raw) }.getOrNull()
            ?: return SnapshotCheck.Rejected("invalid_envelope")
        val previousRevision = preferences.getLong("last_revision", -1L)
        val previousGeneratedAt = preferences.getLong("last_generated_at", -1L)
        val result = verifier.verify(envelope, now, previousRevision, previousGeneratedAt)
        if (result !is SnapshotCheck.Accepted) return result

        // Store the ORIGINAL signed JSON, never a reconstructed unsigned
        // payload. Android SharedPreferences Editor commits all 3 entries
        // together or exposes none of them to this instance.
        val durable = preferences.edit()
            .putLong("last_revision", result.snapshot.revision)
            .putLong("last_generated_at", result.snapshot.generatedAt)
            .putString("last_signed_envelope", raw)
            .commit()
        return if (durable) result
            else SnapshotCheck.Rejected("cannot_persist_replay_guard")
    }

    /**
     * Recover an unexpired cached snapshot with NO network connection.
     * Expired or tampered caches fail closed; the replay high-water marks are
     * intentionally retained to block a previously signed old revision.
     */
    @Synchronized
    fun restoreVerifiedOffline(now: Long): VerifiedSourceSnapshot? =
        SourceSnapshotOfflinePolicy.restore(
            preferences.getString("last_signed_envelope", null), now,
            preferences.getLong("last_revision", -1L),
            preferences.getLong("last_generated_at", -1L),
            parseEnvelope, verifier
        )
}
