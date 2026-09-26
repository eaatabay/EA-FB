package com.eafb

import java.net.URI
import java.util.Locale

/**
 * Human-reviewed rights records must be explicitly compiled into a release.
 * Metadata in a downloaded snapshot (or an 'authorized' JSON boolean) is NOT
 * a license. This checked-in v6 release intentionally approves no real source.
 */
data class ReviewedSourcePermit(
    val id: String,
    val adapterVersion: Int,
    val mediaKind: String,
    val approvedHosts: Set<String>,
    val approvedPathPrefix: String,
    val evidenceReference: String,
    val reviewedAt: Long,
    val validUntil: Long
)

object ReviewedSourcePermits {
    val bundled: List<ReviewedSourcePermit> = emptyList()
}

/**
 * Third approval gate, following signed snapshot and compiled adapter version:
 * source rights, exact approved HTTPS hosts and path scope are release-pinned.
 * Pure Kotlin. No network, runtime permission editing or playback side effects.
 */
object ReviewedSourcePermitPolicy {
    private const val DAY_MS = 86_400_000L
    private const val MAX_REVIEW_AGE_MS = 366L * DAY_MS
    private val id = Regex("[a-z][a-z0-9-]{2,63}")
    private val host = Regex("[a-z0-9](?:[a-z0-9-]*[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]*[a-z0-9])?)+")
    private val path = Regex("/(?:[a-zA-Z0-9_.-]+(?:/[a-zA-Z0-9_.-]+)*)?")
    private val evidence = Regex("rights/[a-zA-Z0-9_./-]{8,180}\\.md")

    private fun validHost(value: String): Boolean =
        value.length in 4..253 && host.matches(value) &&
            value.split('.').all { it.length in 1..63 } &&
            !value.endsWith(".local") && !value.endsWith(".localhost") &&
            !value.endsWith(".internal") && !value.endsWith(".invalid") &&
            !value.matches(Regex("[0-9]+(?:\\.[0-9]+){3}"))

    private fun validPath(value: String): Boolean =
        path.matches(value) &&
            value.split('/').drop(1).all { it != "." && it != ".." } &&
            !value.contains("//") && !value.contains("\\") &&
            !value.contains('%')

    private fun allowedBaseUrl(value: String, permit: ReviewedSourcePermit): Boolean {
        return try {
            val uri = URI(value)
            val hostname = uri.host?.lowercase(Locale.ROOT) ?: return false
            if (uri.scheme != "https" || uri.userInfo != null || uri.port != -1 ||
                uri.rawQuery != null || uri.rawFragment != null ||
                value != uri.toASCIIString() || uri.rawAuthority != hostname ||
                !validHost(hostname) ||
                !permit.approvedHosts.contains(hostname)) return false
            val rawPath = uri.rawPath?.ifEmpty { "/" } ?: "/"
            val prefix = permit.approvedPathPrefix
            validPath(rawPath) && (prefix == "/" || rawPath == prefix ||
                rawPath.startsWith("$prefix/"))
        } catch (_: Exception) { false }
    }

    private fun validPermit(permit: ReviewedSourcePermit, now: Long): Boolean =
        id.matches(permit.id) && permit.adapterVersion in 1..1_000_000 &&
            permit.mediaKind in setOf("movie", "series", "both") &&
            permit.approvedHosts.size in 1..12 &&
            permit.approvedHosts.all(::validHost) &&
            validPath(permit.approvedPathPrefix) &&
            evidence.matches(permit.evidenceReference) &&
            // Never let one source silently cite another source's review record.
            // Only rights/YYYY/<this-source-id>.md is a canonical reference.
            Regex("rights/[0-9]{4}/" + Regex.escape(permit.id) + "\\.md")
                .matches(permit.evidenceReference) &&
            // Evidence is a repository-local review record, never a URL or
            // a path that can normalize to a different reviewed document.
            permit.evidenceReference.removePrefix("rights/")
                .split('/').all { it.isNotEmpty() && it != "." && it != ".." } &&

            permit.reviewedAt in 0..now &&
            permit.validUntil > now &&
            permit.validUntil > permit.reviewedAt &&
            // A positive bounded difference also rejects Long overflow.
            (permit.validUntil - permit.reviewedAt) in 1..MAX_REVIEW_AGE_MS

    private fun permittedKind(permit: String, signed: String): String? =
        when {
            signed !in setOf("movie", "series", "both") -> null
            permit == "both" -> signed
            signed == "both" || signed == permit -> permit
            else -> null
        }

    /**
     * A missing/expired/malformed permit list fails closed for ALL sources;
     * an otherwise valid list may exclude individual signed source entries.
     */
    fun restrict(
        snapshot: VerifiedSourceSnapshot?,
        now: Long,
        permits: List<ReviewedSourcePermit>
    ): VerifiedSourceSnapshot? {
        if (snapshot == null || now < 0 || permits.isEmpty() || permits.size > 32 ||
            snapshot.usableSources.map { it.id }.toSet().size != snapshot.usableSources.size ||
            permits.map { it.id }.toSet().size != permits.size ||
            permits.any { !validPermit(it, now) }) return null
        val byId = permits.associateBy { it.id }
        val filtered = snapshot.usableAt(now).mapNotNull { source ->
            val permit = byId[source.id] ?: return@mapNotNull null
            if (source.adapterVersion != permit.adapterVersion ||
                !allowedBaseUrl(source.baseUrl, permit)) return@mapNotNull null
            val authorizedKind = permittedKind(permit.mediaKind, source.mediaKind)
                ?: return@mapNotNull null
            source.copy(mediaKind = authorizedKind)
        }
        return snapshot.copy(usableSources = filtered)
    }
}
