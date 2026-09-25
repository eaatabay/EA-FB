package com.eafb

import android.content.Context

/**
 * This does NOT fetch an endpoint or activate any external adapter. New
 * searches may use only a signature-verified, unexpired snapshot. The app
 * persists replay protection before exposing an accepted snapshot to callers.
 */
class WatchdogClientStore(context: Context) {
    private val preferences = context.getSharedPreferences("ea_fb_watchdog_trust_v1", Context.MODE_PRIVATE)

    fun acceptSignedJson(raw: String, now: Long): SnapshotCheck {
        val envelope = WatchdogSnapshotJson.parse(raw)
            ?: return SnapshotCheck.Rejected("invalid_envelope")
        // Production currently has NO live signing keys or external playback
        // adapters. Until approved pins are bundled, this fails closed.
        val verifier = SourceSnapshotTrust(
            WatchdogTrustConfig.pinnedPublicKeys,
            WatchdogTrustConfig.installedAdapterVersions
        )
        val oldRevision = preferences.getLong("last_revision", -1L)
        val oldGeneratedAt = preferences.getLong("last_generated_at", -1L)
        val result = verifier.verify(envelope, now, oldRevision, oldGeneratedAt)
        if (result !is SnapshotCheck.Accepted) return result
        val signed = result.snapshot
        val stored = preferences.edit()
            .putLong("last_revision", signed.revision)
            .putLong("last_generated_at", signed.generatedAt)
            .commit() // fail closed if durable storage fails
        return if (stored) result else SnapshotCheck.Rejected("cannot_persist_replay_guard")
    }
}
