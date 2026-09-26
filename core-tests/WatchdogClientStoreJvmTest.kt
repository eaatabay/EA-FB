package com.eafb

import android.content.Context
import android.content.SharedPreferences
import org.bouncycastle.util.encoders.Base64
import kotlinx.coroutines.CancellationException

private const val PUBLIC_KEY = "3XThw1FOoxQye8ObEatzSwW1lRlo/g9iZSRuClkOjak="
private const val SIGNATURE = "pcw-_1oE7BZMdmSjTJgVuurK04lxCVpHrcCLEchUueMtiI3B7lrrCl1NbbWNELG-HgEbw12OA14c4nC9gtp1DA"
private const val NOW = 1_800_000_000_000L
private val fixture = SignedSourceEnvelope(1,"Ed25519","test-only-2026",
    SourceSnapshot(1,42,NOW,NOW+900_000L,listOf(
        SnapshotSource("licensed-demo","movie","https://licensed.example.org",3),
        SnapshotSource("new-adapter","series","https://series.example.org",1))),SIGNATURE)
private val trust = SourceSnapshotTrust(
    mapOf("test-only-2026" to Base64.decode(PUBLIC_KEY)),mapOf("licensed-demo" to 3))

private class FakePreferences: SharedPreferences {
    val values=mutableMapOf<String,Any>()
    var allowCommit=true
    var updateMemoryOnFailure=false
    var commitError: Exception?=null
    override fun getLong(key:String,default:Long):Long=(values[key] as? Long)?:default
    override fun getString(key:String,default:String?):String?=(values[key] as? String)?:default
    override fun edit():SharedPreferences.Editor=object:SharedPreferences.Editor {
        val pending=mutableMapOf<String,Any>()
        override fun putString(key:String,value:String):SharedPreferences.Editor={
            pending[key]=value;this
        }()
        override fun putLong(key:String,value:Long):SharedPreferences.Editor={
            pending[key]=value;this
        }()
        override fun commit():Boolean {
            commitError?.let { error ->
                if(updateMemoryOnFailure)values.putAll(pending)
                throw error
            }
            if(!allowCommit){
                if(updateMemoryOnFailure)values.putAll(pending)
                return false
            }
            values.putAll(pending)
            return true
        }
    }
}
private class FakeContext(val prefs:FakePreferences=FakePreferences()):Context(){
    override fun getSharedPreferences(name:String,mode:Int):SharedPreferences=prefs
}
fun main(){
    var total=0
    fun ok(value:Boolean,label:String){check(value){label};total++}
    val ctx=FakeContext()
    val parse:(String)->SignedSourceEnvelope?={if(it=="signed")fixture else null}
    val client=WatchdogClientStore(ctx,trust,parse)
    ok(client.restoreVerifiedOffline(NOW)==null,"no cached snapshot before acceptance")
    ok(client.approvedRefreshClient() == null,
        "production refresh client cannot activate with missing keys or endpoint")
    ok(client.acceptSignedJson("signed",NOW) is SnapshotCheck.Accepted,"valid signature accepted")
    ok(ctx.prefs.values.size==3,"signed raw and both replay guards stored in one commit")
    ok(client.restoreVerifiedOffline(NOW)?.usableSources?.map{it.id}==listOf("licensed-demo"),
        "valid unexpired signed envelope works offline")
    ok(client.restoreVerifiedOffline(NOW+900_000L)==null,"expired cache unavailable")
    ok(client.acceptSignedJson("signed",NOW) is SnapshotCheck.Rejected,"replaying signed response denied")
    ctx.prefs.values["last_signed_envelope"]="forged"
    ok(client.restoreVerifiedOffline(NOW)==null,"poisoned raw signed cache denied")
    ctx.prefs.values["last_signed_envelope"]="signed"
    ctx.prefs.values["last_revision"]=43L
    ok(client.restoreVerifiedOffline(NOW)==null,"forged higher persisted revision denied")
    ctx.prefs.values["last_revision"]=42L
    ctx.prefs.values["last_generated_at"]=NOW+1
    ok(client.restoreVerifiedOffline(NOW)==null,"forged generation timestamp denied")
    ctx.prefs.values["last_generated_at"]=NOW
    ok(client.restoreVerifiedOffline(NOW)!=null,"matching persisted high-water marks recover")
    val missing=WatchdogClientStore(FakeContext())
    ok(missing.acceptSignedJson("signed",NOW) is SnapshotCheck.Rejected,
        "production defaults with no signing pins fail closed")
    val failureCtx=FakeContext()
    failureCtx.prefs.allowCommit=false
    val failureStore=WatchdogClientStore(failureCtx,trust,parse)
    val fail=failureStore.acceptSignedJson("signed",NOW)
    ok(fail is SnapshotCheck.Rejected && fail.reason=="cannot_persist_replay_guard",
        "disk persistence failure rejects otherwise valid signed payload")
    ok(failureStore.restoreVerifiedOffline(NOW)==null,"failed write cannot expose cache")
    ok(failureCtx.prefs.values.isEmpty(),"failed commit leaves no replay counters")
    ok(client.acceptSignedJson("x".repeat(32_769),NOW) is SnapshotCheck.Rejected,
        "oversized signed payload rejected")
    ok(ctx.prefs.getLong("last_revision",-1L)==42L,
        "rejected response never overwrites durable replay guards")
    val volatileCtx=FakeContext()
    volatileCtx.prefs.allowCommit=false
    volatileCtx.prefs.updateMemoryOnFailure=true
    val volatileStore=WatchdogClientStore(volatileCtx,trust,parse)
    ok(volatileStore.acceptSignedJson("signed",NOW) is SnapshotCheck.Rejected,
        "Android in-memory write with failed disk commit is rejected")
    ok(volatileCtx.prefs.values.size==3 && volatileStore.restoreVerifiedOffline(NOW)==null,
        "non-durable in-memory signed snapshot remains inaccessible")
    val siblingStore=WatchdogClientStore(volatileCtx,trust,parse)
    ok(siblingStore.restoreVerifiedOffline(NOW)==null,
        "new store sharing failed SharedPreferences cannot expose volatile cache")
    ok(siblingStore.acceptSignedJson("signed",NOW) is SnapshotCheck.Rejected,
        "new store sharing failed preferences cannot bypass persistence guard")
    volatileCtx.prefs.allowCommit=true
    val stillRejected=volatileStore.acceptSignedJson("signed",NOW)
    ok(stillRejected is SnapshotCheck.Rejected &&
        stillRejected.reason=="cannot_persist_replay_guard",
        "failed persistence keeps this store fail-closed even after disk recovers")
    val cancelledStore=WatchdogClientStore(FakeContext(),trust,
        { throw CancellationException("cancelled") })
    ok(runCatching { cancelledStore.acceptSignedJson("signed",NOW) }
        .exceptionOrNull() is CancellationException,
        "cancelled signed JSON parser propagates to caller")
    val throwingCtx=FakeContext()
    throwingCtx.prefs.updateMemoryOnFailure=true
    throwingCtx.prefs.commitError=IllegalStateException("disk unavailable")
    val throwingStore=WatchdogClientStore(throwingCtx,trust,parse)
    val thrown=throwingStore.acceptSignedJson("signed",NOW)
    ok(thrown is SnapshotCheck.Rejected && thrown.reason=="cannot_persist_replay_guard" &&
        throwingStore.restoreVerifiedOffline(NOW)==null,
        "throwing commit cannot expose volatile signed cache")
    ok(WatchdogClientStore(throwingCtx,trust,parse).restoreVerifiedOffline(NOW)==null,
        "second store cannot bypass throwing commit guard")
    val cancelledCtx=FakeContext()
    cancelledCtx.prefs.updateMemoryOnFailure=true
    cancelledCtx.prefs.commitError=CancellationException("commit interrupted")
    val cancelledCommitStore=WatchdogClientStore(cancelledCtx,trust,parse)
    ok(runCatching { cancelledCommitStore.acceptSignedJson("signed",NOW) }
        .exceptionOrNull() is CancellationException,
        "commit cancellation propagates to caller")
    ok(cancelledCommitStore.restoreVerifiedOffline(NOW)==null &&
        WatchdogClientStore(cancelledCtx,trust,parse).restoreVerifiedOffline(NOW)==null,
        "cancelled commit cannot expose in-memory signed snapshot")
    println("PASS: $total/$total Android store JVM atomic-cache tests (stub Context/JSON)")
}
