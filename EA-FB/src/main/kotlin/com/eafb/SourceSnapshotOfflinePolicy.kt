package com.eafb

import java.nio.charset.StandardCharsets

/**
 * Recheck a saved signed envelope before EVERY offline use. A cached source is
 * never trusted merely because it was accepted yesterday, or because the
 * SharedPreferences replay counters match. No network or adapter activation.
 */
object SourceSnapshotOfflinePolicy {
    private const val MAX_ENVELOPE_BYTES = 32_768

    fun restore(
        signedJson: String?,
        now: Long,
        persistedRevision: Long,
        persistedGeneratedAt: Long,
        parseEnvelope: (String) -> SignedSourceEnvelope?,
        verifier: SourceSnapshotTrust
    ): VerifiedSourceSnapshot? {
        if (signedJson == null || signedJson.length > MAX_ENVELOPE_BYTES ||
            persistedRevision < 0L || persistedGeneratedAt < 0L || now < 0L ||
            signedJson.toByteArray(StandardCharsets.UTF_8).size > MAX_ENVELOPE_BYTES) {
            return null
        }
        val envelope = runCatching { parseEnvelope(signedJson) }.getOrNull()
            ?: return null
        // Cache bytes and anti-replay high-water marks must be from ONE
        // successful, atomic SharedPreferences.Editor.commit() transaction.
        if (envelope.payload.revision != persistedRevision ||
            envelope.payload.generatedAt != persistedGeneratedAt) return null
        // Deliberately validate without the NEW-response replay precondition:
        // replaying the same bytes from the cache is expected, but signature,
        // expiry, future clock, installed adapter versions and pins are not.
        val result = verifier.verify(envelope, now)
        return (result as? SnapshotCheck.Accepted)?.snapshot
    }
}
