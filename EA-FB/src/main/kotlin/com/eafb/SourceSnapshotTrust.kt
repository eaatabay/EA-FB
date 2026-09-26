package com.eafb

import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.Locale
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.bouncycastle.util.encoders.Base64

/** No live keys or adapters are bundled in v6. This module never fetches URLs. */
data class SnapshotSource(
    val id: String,
    val mediaKind: String,
    val baseUrl: String,
    val adapterVersion: Int
)

data class SourceSnapshot(
    val schemaVersion: Int,
    val revision: Long,
    val generatedAt: Long,
    val expiresAt: Long,
    val sources: List<SnapshotSource>
)

data class SignedSourceEnvelope(
    val envelopeVersion: Int,
    val algorithm: String,
    val keyId: String,
    val payload: SourceSnapshot,
    val signature: String
)

data class VerifiedSourceSnapshot(
    val revision: Long,
    val generatedAt: Long,
    val expiresAt: Long,
    val usableSources: List<SnapshotSource>
) {
    /** Never start new searches after the signed source list expires. */
    fun usableAt(now: Long): List<SnapshotSource> =
        if (now >= 0 && now < expiresAt) usableSources else emptyList()
}

sealed class SnapshotCheck {
    data class Accepted(val snapshot: VerifiedSourceSnapshot) : SnapshotCheck()
    data class Rejected(val reason: String) : SnapshotCheck()
}

/**
 * Pure Kotlin verifier shared by Android and JVM tests. Uses bundled BC
 * Ed25519Signer because legacy Mi Box Android versions lack JCA Ed25519.
 * Key IDs -> raw 32-byte Ed25519 public key, pinned in the installed .cs3.
 */
class SourceSnapshotTrust(
    pinnedPublicKeys: Map<String, ByteArray>,
    installedAdapterVersions: Map<String, Int>
) {
    private val pins = pinnedPublicKeys.mapValues { it.value.copyOf() }
    private val adapters = installedAdapterVersions.toMap()

    fun verify(envelope: SignedSourceEnvelope, now: Long,
        lastRevision: Long = -1L, lastGeneratedAt: Long = -1L): SnapshotCheck {
        fun reject(reason: String) = SnapshotCheck.Rejected(reason)
        val snapshot = envelope.payload
        if (envelope.envelopeVersion != 1 || envelope.algorithm != "Ed25519" ||
            !envelope.keyId.matches(Regex("[A-Za-z0-9_-]{3,48}")) ||
            now < 0 || now > MAX_SAFE || lastRevision < -1 ||
            lastGeneratedAt < -1 || !validSnapshot(snapshot, now)) {
            return reject("invalid_envelope")
        }
        if (snapshot.revision < lastRevision ||
            (snapshot.revision == lastRevision &&
                (lastGeneratedAt < 0 || snapshot.generatedAt <= lastGeneratedAt))) {
            return reject("stale_revision")
        }
        val rawKey = pins[envelope.keyId] ?: return reject("unknown_signing_key")
        if (rawKey.size != 32) return reject("unknown_signing_key")
        if (!envelope.signature.matches(Regex("[A-Za-z0-9_-]{86}"))) {
            return reject("invalid_signature")
        }
        val signature = try {
            Base64.decode(envelope.signature.replace('-', '+').replace('_', '/') + "==")
        } catch (_: Exception) { return reject("invalid_signature") }
        if (signature.size != 64) return reject("invalid_signature")
        val message = (CONTEXT + canonical(snapshot)).toByteArray(StandardCharsets.UTF_8)
        val valid = try {
            val signer = Ed25519Signer()
            signer.init(false, Ed25519PublicKeyParameters(rawKey, 0))
            signer.update(message, 0, message.size)
            signer.verifySignature(signature)
        } catch (_: Exception) { false }
        if (!valid) return reject("invalid_signature")
        return SnapshotCheck.Accepted(VerifiedSourceSnapshot(
            snapshot.revision, snapshot.generatedAt, snapshot.expiresAt,
            snapshot.sources.filter { adapters[it.id] == it.adapterVersion }
        ))
    }

    private fun validSnapshot(s: SourceSnapshot, now: Long): Boolean {
        if (s.schemaVersion != 1 || s.revision !in 0..MAX_SAFE ||
            s.generatedAt !in 0..MAX_SAFE || s.expiresAt !in 0..MAX_SAFE ||
            s.generatedAt > now + 300_000 || s.expiresAt <= now ||
            s.expiresAt <= s.generatedAt || s.expiresAt - s.generatedAt > 3_600_000 ||
            s.sources.size > 256) return false
        val ids = HashSet<String>()
        return s.sources.all { x ->
            x.id.matches(Regex("[a-z][a-z0-9-]{2,63}")) && ids.add(x.id) &&
                x.mediaKind in setOf("movie", "series", "both") &&
                validHttpsBaseUrl(x.baseUrl) && x.adapterVersion in 1..1_000_000
        }
    }

    private fun validHttpsBaseUrl(value: String): Boolean {
        if (value.isEmpty() || value.length > 2048 || !value.startsWith("https://")) return false
        return try {
            val uri = URI(value)
            val host = uri.host?.lowercase(Locale.ROOT) ?: return false
            if (uri.scheme != "https" || uri.userInfo != null || uri.port != -1 ||
                uri.rawQuery != null || uri.rawFragment != null ||
                !host.matches(Regex("[a-z0-9](?:[a-z0-9-]*[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]*[a-z0-9])?)+")) ||
                host.length > 253 || host.split('.').any { it.length > 63 } ||
                host.endsWith(".local") || host.endsWith(".localhost") ||
                host.endsWith(".internal") || host.endsWith(".invalid") ||
                host.matches(Regex("[0-9]+(\\.[0-9]+){3}"))) return false
            val path = uri.rawPath.orEmpty()
            val normalizedPath = if (path == "/") "" else path.trimEnd('/')
            val reconstructed = "https://$host$normalizedPath"
            reconstructed == value && !path.contains('%') && !path.contains("//") &&
                !path.split('/').any { it == "." || it == ".." }
        } catch (_: Exception) { false }
    }

    /** JS canonical() sorts keys alphabetically and preserves source array order. */
    private fun canonical(s: SourceSnapshot): String = buildString {
        append("{\"expiresAt\":").append(s.expiresAt)
        append(",\"generatedAt\":").append(s.generatedAt)
        append(",\"revision\":").append(s.revision)
        append(",\"schemaVersion\":").append(s.schemaVersion)
        append(",\"sources\":[")
        s.sources.forEachIndexed { index, x ->
            if (index > 0) append(',')
            append("{\"adapterVersion\":").append(x.adapterVersion)
            append(",\"baseUrl\":").append(jsonString(x.baseUrl))
            append(",\"id\":").append(jsonString(x.id))
            append(",\"mediaKind\":").append(jsonString(x.mediaKind)).append('}')
        }
        append("]}")
    }

    private fun jsonString(value: String): String = buildString {
        append('"')
        for (char in value) when (char) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\b' -> append("\\b")
            '\u000c' -> append("\\f")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> if (char.code < 32) {
                append("\\u").append(char.code.toString(16).padStart(4, '0'))
            } else append(char)
        }
        append('"')
    }

    private companion object {
        const val MAX_SAFE = 9_007_199_254_740_991L
        const val CONTEXT = "EA-FB/SOURCE-SNAPSHOT/V1\n"
    }
}

/** Production pins must be approved before enabling any remote source. */
object WatchdogTrustConfig {
    val pinnedPublicKeys: Map<String, ByteArray> = emptyMap()
    val installedAdapterVersions: Map<String, Int> = emptyMap()
}
