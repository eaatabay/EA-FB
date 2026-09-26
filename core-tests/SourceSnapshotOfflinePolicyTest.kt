package com.eafb

import org.bouncycastle.util.encoders.Base64
import kotlinx.coroutines.CancellationException

private const val PUBLIC_TEST_PIN = "3XThw1FOoxQye8ObEatzSwW1lRlo/g9iZSRuClkOjak="
private const val PUBLIC_TEST_SIGNATURE = "pcw-_1oE7BZMdmSjTJgVuurK04lxCVpHrcCLEchUueMtiI3B7lrrCl1NbbWNELG-HgEbw12OA14c4nC9gtp1DA"
private const val FIXTURE_TIME = 1_800_000_000_000L
private val signedFixture = SignedSourceEnvelope(
    1, "Ed25519", "test-only-2026",
    SourceSnapshot(1, 42, FIXTURE_TIME, FIXTURE_TIME + 900_000L, listOf(
        SnapshotSource("licensed-demo", "movie", "https://licensed.example.org", 3),
        SnapshotSource("new-adapter", "series", "https://series.example.org", 1)
    )), PUBLIC_TEST_SIGNATURE
)
private val verifier = SourceSnapshotTrust(
    mapOf("test-only-2026" to Base64.decode(PUBLIC_TEST_PIN)),
    mapOf("licensed-demo" to 3)
)
private fun restored(
    raw: String? = "test-signed",
    now: Long = FIXTURE_TIME,
    revision: Long = 42,
    generatedAt: Long = FIXTURE_TIME,
    envelope: SignedSourceEnvelope? = signedFixture,
    trust: SourceSnapshotTrust = verifier
): VerifiedSourceSnapshot? = SourceSnapshotOfflinePolicy.restore(
    raw, now, revision, generatedAt, { envelope }, trust
)

fun main() {
    var passed = 0
    fun checked(condition: Boolean, label: String) {
        check(condition) { label }
        passed++
    }
    checked(restored()?.usableSources?.map { it.id } == listOf("licensed-demo"),
        "valid signed cached snapshot with a bundled compatible adapter")
    checked(restored(raw = null) == null, "no saved JSON")
    checked(restored(raw = "x".repeat(32_769)) == null, "oversized cached JSON")
    checked(restored(revision = 43) == null, "revision ahead of signed bytes")
    checked(restored(revision = 41) == null, "revision behind signed bytes")
    checked(restored(generatedAt = FIXTURE_TIME + 1) == null, "generation mismatch")
    checked(restored(now = FIXTURE_TIME + 900_000L) == null, "TTL expired")
    checked(restored(now = FIXTURE_TIME - 300_001L) == null, "implausible future generation")
    checked(restored(envelope = null) == null, "invalid JSON")
    checked(restored(envelope = signedFixture.copy(payload = signedFixture.payload.copy(
        sources = listOf(signedFixture.payload.sources.first().copy(
            baseUrl = "https://attacker.example.org"))))) == null, "tampered signed source")
    checked(restored(trust = SourceSnapshotTrust(emptyMap(), mapOf("licensed-demo" to 3))) == null,
        "missing production pin fails closed")
    checked(restored(trust = SourceSnapshotTrust(
        mapOf("test-only-2026" to Base64.decode(PUBLIC_TEST_PIN)),
        mapOf("licensed-demo" to 4)))?.usableSources?.isEmpty() == true,
        "uninstalled adapter version cannot be activated")
    checked(restored(raw = "x".repeat(32768) + "é") == null, "UTF-8 byte cap")
    checked(WatchdogTrustConfig.pinnedPublicKeys.isEmpty() &&
        WatchdogTrustConfig.installedAdapterVersions.isEmpty(),
        "tracked build has no live keys/adapters")
    checked(runCatching { SourceSnapshotOfflinePolicy.restore("signed", FIXTURE_TIME,
        42, FIXTURE_TIME, { throw CancellationException("cancelled") }, verifier)
    }.exceptionOrNull() is CancellationException,
        "cancelled offline parser propagates instead of returning empty cache")
    println("PASS: $passed/$passed signed offline snapshot policy checks")
}
