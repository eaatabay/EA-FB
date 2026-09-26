package com.eafb

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.Job
import kotlin.coroutines.coroutineContext
import org.bouncycastle.util.encoders.Base64

private const val PUBLIC_TEST_PIN = "3XThw1FOoxQye8ObEatzSwW1lRlo/g9iZSRuClkOjak="
private const val PUBLIC_TEST_SIGNATURE = "pcw-_1oE7BZMdmSjTJgVuurK04lxCVpHrcCLEchUueMtiI3B7lrrCl1NbbWNELG-HgEbw12OA14c4nC9gtp1DA"
private const val T = 1_800_000_000_000L
private const val Q = 900_000L
private val signed = SignedSourceEnvelope(1, "Ed25519", "test-only-2026",
    SourceSnapshot(1, 42, T, T+Q, listOf(
        SnapshotSource("licensed-demo", "movie", "https://licensed.example.org", 3),
        SnapshotSource("new-adapter", "series", "https://series.example.org", 1)
    )), PUBLIC_TEST_SIGNATURE)
private val verifier = SourceSnapshotTrust(
    mapOf("test-only-2026" to Base64.decode(PUBLIC_TEST_PIN)),
    mapOf("licensed-demo" to 3)
)
private val good = WatchdogRefreshOptions(true,
    "https://watchdog.example.org/v1/sources", "https://watchdog.example.org", true)
private class FakeStore {
    var raw: String? = null
    var rev: Long = -1L
    var generated: Long = -1L
    var accepts = 0
    fun accept(json: String, now: Long): SnapshotCheck {
        accepts++
        val payload = when (json) {
            "signed" -> signed
            "altered" -> signed.copy(payload=signed.payload.copy(
                sources=listOf(signed.payload.sources.first().copy(
                    baseUrl="https://attacker.example.org"))))
            else -> return SnapshotCheck.Rejected("invalid_envelope")
        }
        val check = verifier.verify(payload, now, rev, generated)
        if (check is SnapshotCheck.Accepted) {
            raw=json;rev=check.snapshot.revision;generated=check.snapshot.generatedAt
        }
        return check
    }
    fun offline(now: Long): VerifiedSourceSnapshot? = SourceSnapshotOfflinePolicy.restore(
        raw, now, rev, generated,
        { text -> if(text=="signed") signed else null }, verifier
    )
}
private class FakeTransport(var reply: WatchdogHttpSnapshot = response()): WatchdogSnapshotTransport {
    var calls=0
    var fails=false
    override suspend fun get(endpoint: String): WatchdogHttpSnapshot {
        calls++
        if(fails) throw IllegalStateException("simulated disconnect")
        return reply
    }
}
private fun response(status: Int=200, body: ByteArray="signed".toByteArray(),
    type: String?="application/json")=WatchdogHttpSnapshot(status,type,body)
private fun coordinator(store:FakeStore,transport:FakeTransport, options:WatchdogRefreshOptions=good)=
    WatchdogSnapshotRefresh(options,transport,store::accept,store::offline)

fun main()=runBlocking {
    var count=0
    fun checked(value:Boolean,label:String){check(value){label};count++}
    val invalid=listOf(
        good.copy(enabled=false),good.copy(trustedKeysAndAdaptersInstalled=false),
        good.copy(endpoint="http://watchdog.example.org/v1/sources"),
        good.copy(endpoint="https://watchdog.example.org:8443/v1/sources"),
        good.copy(endpoint="https://watchdog.example.org/v1/sources?secret=x"),
        good.copy(endpoint="https://watchdog.example.org/v1/sources#fragment"),
        good.copy(endpoint="https://user@watchdog.example.org/v1/sources"),
        good.copy(endpoint="https://watchdog.example.org.evil.tld/v1/sources"),
        good.copy(endpoint="https://127.0.0.1/v1/sources",approvedOrigin="https://127.0.0.1"),
        good.copy(approvedOrigin="https://other.example.org"),
        good.copy(endpoint="https://watchdog.example.org/v1/other"),
        good.copy(endpoint="https://watchdog.example.org/v1/sources/../sources")
    )
    checked(invalid.all{!WatchdogSnapshotRefresh.isApprovedConfiguration(it)},
        "twelve unsafe/disabled endpoint configurations are rejected")
    checked(WatchdogSnapshotRefresh.isApprovedConfiguration(good),
        "exact separately approved HTTPS endpoint with pins/adapters")
    checked(!WatchdogSnapshotRefresh.isApprovedConfiguration(
        WatchdogDeliveryConfig.productionOptions()),
        "app-compiled production options remain fail-closed")
    checked(!WatchdogDeliveryConfig.enabled && WatchdogDeliveryConfig.endpoint.isEmpty() &&
        WatchdogDeliveryConfig.approvedOrigin.isEmpty() &&
        WatchdogTrustConfig.pinnedPublicKeys.isEmpty() &&
        WatchdogTrustConfig.installedAdapterVersions.isEmpty(),
        "checked-in production network, trust pins and adapters stay disabled")
    val store=FakeStore();val net=FakeTransport();val sync=coordinator(store,net)
    val success=sync.refresh(T)
    checked(success.status=="updated"&&success.networkAttempted &&
        success.snapshot?.usableSources?.map{it.id}==listOf("licensed-demo"),
        "valid pinned signature admits only bundled exact-version adapter")
    checked(store.raw=="signed" && store.rev==42L && store.generated==T,
        "successful signed response persisted unchanged")
    checked(sync.refresh(T+1).status=="throttled" && net.calls==1,
        "fifteen-minute cadence prevents duplicate network requests")
    checked(sync.refresh(T-1).status=="clock_rollback" && net.calls==1,
        "wall-clock rollback prevents request")
    checked(sync.refresh(T+Q).status=="untrusted_snapshot" && net.calls==2,
        "expired repeated signed response cannot be accepted as new")
    val offline=FakeStore().apply{accept("signed",T)}
    val outage=FakeTransport().apply{fails=true}
    val backoff=coordinator(offline,outage)
    checked(backoff.refresh(T+1).status=="network_unavailable" &&
        backoff.refresh(T+2).status=="throttled" && outage.calls==1,
        "connection failure falls back to signed unexpired cache and throttles")
    checked(backoff.refresh(T+Q+1).snapshot==null && outage.calls==2,
        "expired offline cache is never exposed during outage")
    val noWire=FakeTransport()
    checked(coordinator(offline,noWire,good.copy(enabled=false)).refresh(T).snapshot==null &&
        noWire.calls==0,"disabled production delivery never exposes cache or starts a request")
    val redirect=FakeTransport(response(302))
    checked(coordinator(offline,redirect).refresh(T+1).status=="http_unavailable" &&
        redirect.calls==1,"HTTP redirect not followed")
    checked(coordinator(offline,FakeTransport(response(200,byteArrayOf()))).refresh(T+1).status==
        "invalid_response","HTTP 200 empty is never success")
    checked(coordinator(offline,FakeTransport(response(type="text/html"))).refresh(T+1).status==
        "invalid_response","HTML masquerading as snapshot rejected")
    checked(coordinator(offline,FakeTransport(response(body=byteArrayOf(-61,40)))).refresh(T+1).status==
        "invalid_response","malformed UTF-8 rejected without replacement")
    checked(coordinator(offline,FakeTransport(response(body=ByteArray(32_769)))).refresh(T+1).status==
        "invalid_response","oversized response discarded")
    checked(coordinator(offline,FakeTransport(response(body="altered".toByteArray()))).refresh(T+1).status==
        "untrusted_snapshot","tampered envelope cannot replace valid cache")
    checked(coordinator(offline,FakeTransport(response(429))).refresh(T+1).status==
        "http_unavailable","rate-limited HTTP response never accepted")
    checked(store.offline(T+Q)==null,"cache is unusable exactly at expiry")
    val brokenCache=WatchdogSnapshotRefresh(good,FakeTransport().apply{fails=true},
        offline::accept,{ throw IllegalStateException("corrupt local preferences") })
    checked(brokenCache.refresh(T+1).snapshot==null,
        "corrupt offline preferences fail closed without crashing network retry")
    val interrupted=WatchdogSnapshotRefresh(good,
        WatchdogSnapshotTransport { throw CancellationException("screen gone") },
        offline::accept,offline::offline)
    checked(runCatching{interrupted.refresh(T+1)}.exceptionOrNull() is CancellationException,
        "coroutine cancellation propagates and frees refresh mutex")
    val concurrentStore=FakeStore();val concurrentNet=FakeTransport()
    val concurrent=coordinator(concurrentStore,concurrentNet)
    val all=(0..4).map{async{concurrent.refresh(T)}}.awaitAll()
    checked(concurrentNet.calls==1&&all.count{it.status=="updated"}==1&&
        all.count{it.status=="throttled"}==4,
        "concurrent callers coalesce under Mutex")
    val cacheCancelled=WatchdogSnapshotRefresh(good,FakeTransport(),offline::accept,
        { throw CancellationException("cancelled") })
    checked(runCatching{cacheCancelled.refresh(T+1)}.exceptionOrNull() is CancellationException,
        "cancelled cache restoration propagates")
    val verifyCancelled=WatchdogSnapshotRefresh(good,FakeTransport(),
        { _, _ -> throw CancellationException("cancelled") },offline::offline)
    checked(runCatching{verifyCancelled.refresh(T+1)}.exceptionOrNull() is CancellationException,
        "cancelled verification propagates")
    var lateAccepts=0
    val cancelledAfterResponse=async {
        val job=coroutineContext[Job]!!
        val refresh=WatchdogSnapshotRefresh(good,
            WatchdogSnapshotTransport { job.cancel(); response() },
            { _, _ -> lateAccepts++; SnapshotCheck.Rejected("unexpected") },
            { null })
        refresh.refresh(T+1)
    }
    checked(runCatching { cancelledAfterResponse.await() }.exceptionOrNull() is CancellationException &&
        lateAccepts==0,
        "cancellation after transport response prevents signed snapshot persistence")
    println("PASS: $count/$count offline and injected-transport refresh checks")
}
