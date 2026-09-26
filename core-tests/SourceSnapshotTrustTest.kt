package com.eafb

import org.bouncycastle.util.encoders.Base64

/** Node WebCrypto-generated PUBLIC test vector; the private key is not stored. */
private const val TEST_PUBLIC = "3XThw1FOoxQye8ObEatzSwW1lRlo/g9iZSRuClkOjak="
private const val TEST_SIGNATURE = "pcw-_1oE7BZMdmSjTJgVuurK04lxCVpHrcCLEchUueMtiI3B7lrrCl1NbbWNELG-HgEbw12OA14c4nC9gtp1DA"
private const val NOW = 1_800_000_000_000L

private fun source() = SourceSnapshot(
    schemaVersion = 1, revision = 42, generatedAt = NOW,
    expiresAt = NOW + 900_000,
    sources = listOf(
        SnapshotSource("licensed-demo", "movie", "https://licensed.example.org", 3),
        SnapshotSource("new-adapter", "series", "https://series.example.org", 1),
    )
)
private fun signed(payload: SourceSnapshot = source()) = SignedSourceEnvelope(
    envelopeVersion = 1, algorithm = "Ed25519", keyId = "test-only-2026",
    payload = payload, signature = TEST_SIGNATURE
)
private fun verifier(versions: Map<String, Int> = mapOf("licensed-demo" to 3)) =
    SourceSnapshotTrust(mapOf("test-only-2026" to Base64.decode(TEST_PUBLIC)), versions)
private fun rejected(value: SnapshotCheck, reason: String) {
    check(value is SnapshotCheck.Rejected && value.reason == reason) { "Expected $reason, got $value" }
}

fun main() {
    var tested = 0
    val accepted = verifier().verify(signed(), NOW)
    check(accepted is SnapshotCheck.Accepted)
    check(accepted.snapshot.revision == 42L)
    check(accepted.snapshot.usableSources.map { it.id } == listOf("licensed-demo"))
    check(accepted.snapshot.usableAt(NOW + 899_999).size == 1)
    check(accepted.snapshot.usableAt(NOW + 900_000).isEmpty())
    tested++

    rejected(verifier().verify(signed(source().copy(
        sources = listOf(source().sources[0].copy(baseUrl = "https://attacker.example.org"),
            source().sources[1]))), NOW), "invalid_signature")
    tested++
    rejected(verifier().verify(signed(), NOW, lastRevision = 42), "stale_revision")
    tested++
    rejected(verifier().verify(signed(), NOW + 900_000), "invalid_envelope")
    tested++
    rejected(verifier().verify(signed().copy(keyId = "unknown-key"), NOW), "unknown_signing_key")
    tested++
    rejected(SourceSnapshotTrust(emptyMap(), mapOf("licensed-demo" to 3))
        .verify(signed(), NOW), "unknown_signing_key")
    tested++
    val mismatched = verifier(mapOf("licensed-demo" to 4)).verify(signed(), NOW)
    check(mismatched is SnapshotCheck.Accepted && mismatched.snapshot.usableSources.isEmpty())
    tested++
    for (host in listOf("-invalid.example.org", "invalid-.example.org",
        "a".repeat(64) + ".example.org")) {
        rejected(verifier().verify(signed(source().copy(sources = listOf(
            source().sources[0].copy(baseUrl = "https://$host")
        ))), NOW), "invalid_envelope")
        tested++
    }
    rejected(verifier().verify(signed(source().copy(sources = listOf(
        source().sources[0].copy(baseUrl = "https://user:pass@licensed.example.org")
    ))), NOW), "invalid_envelope")
    tested++
    rejected(verifier().verify(signed(source().copy(sources = listOf(
        source().sources[0], source().sources[0]
    ))), NOW), "invalid_envelope")
    tested++
    rejected(verifier().verify(signed(source().copy(generatedAt = NOW + 301_000)), NOW),
        "invalid_envelope")
    tested++
    val altered = signed().copy(signature = TEST_SIGNATURE.reversed())
    rejected(verifier().verify(altered, NOW), "invalid_signature")
    tested++
    val reordered = source().copy(sources = source().sources.reversed())
    rejected(verifier().verify(signed(reordered), NOW), "invalid_signature")
    tested++
    val both = verifier(mapOf("licensed-demo" to 3,"new-adapter" to 1)).verify(signed(), NOW)
    check(both is SnapshotCheck.Accepted && both.snapshot.usableSources.size == 2)
    tested++
    check(WatchdogTrustConfig.pinnedPublicKeys.isEmpty())
    check(WatchdogTrustConfig.installedAdapterVersions.isEmpty())
    tested++
    println("PASS: $tested/$tested cross-language Ed25519/Android snapshot trust checks")
}
