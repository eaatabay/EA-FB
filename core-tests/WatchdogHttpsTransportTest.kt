package com.eafb

import java.io.ByteArrayInputStream
import java.io.IOException
import java.security.cert.Certificate
import javax.net.ssl.HttpsURLConnection
import java.net.URL
import kotlinx.coroutines.runBlocking

private val sampleUrl="https://watchdog.example.org/v1/sources"
private class FakeHttps(
    var httpStatus:Int=200,
    var mime:String="application/json",
    var encoding:String?=null,
    var bytes:ByteArray="signed".toByteArray(),
    var declaredLength:Long=-1L,
    var failRead:Boolean=false
):HttpsURLConnection(URL(sampleUrl)){
    var disconnected=false
    var openedStream=false
    val headers=mutableMapOf<String,String>()
    override fun connect() {}
    override fun disconnect(){disconnected=true}
    override fun usingProxy():Boolean=false
    override fun getCipherSuite():String="TEST_ONLY"
    override fun getLocalCertificates():Array<Certificate>?=null
    override fun getServerCertificates():Array<Certificate> = emptyArray()
    override fun getResponseCode():Int=httpStatus
    override fun getContentType():String=mime
    override fun getContentEncoding():String?=encoding
    override fun getContentLengthLong():Long=declaredLength
    override fun getInputStream():ByteArrayInputStream {
        openedStream=true
        if(failRead)throw IOException("simulated read error")
        return ByteArrayInputStream(bytes)
    }
    override fun setRequestProperty(key:String?,value:String?){
        if(key!=null&&value!=null)headers[key]=value
    }
}
fun main()=runBlocking {
    var count=0
    fun yes(ok:Boolean,label:String){check(ok){label};count++}
    val healthy=FakeHttps()
    val ok=WatchdogHttpsTransport{healthy}.get(sampleUrl)
    yes(ok.status==200&&ok.contentType=="application/json" &&
        ok.body.decodeToString()=="signed","valid JSON response read")
    yes(healthy.disconnected&&healthy.openedStream,"connection closed after valid response")
    yes(!healthy.instanceFollowRedirects&& !healthy.useCaches &&
        healthy.connectTimeout==4000&&healthy.readTimeout==4000&&
        healthy.requestMethod=="GET","redirect/caching disabled; bounded timeouts")
    yes(healthy.headers["Accept"]=="application/json"&&
        healthy.headers["Cache-Control"]=="no-store"&&
        healthy.headers["Accept-Encoding"]=="identity", "no caching/encoding transformation")
    val redirect=FakeHttps(httpStatus=302)
    val moved=WatchdogHttpsTransport{redirect}.get(sampleUrl)
    yes(moved.status==302&&moved.body.isEmpty()&&!redirect.openedStream&&
        redirect.disconnected,"redirect never followed/read")
    val declared=FakeHttps(bytes=ByteArray(32768),declaredLength=32769)
    val tooLong=WatchdogHttpsTransport{declared}.get(sampleUrl)
    yes(tooLong.body.isEmpty()&&!declared.openedStream&&declared.disconnected,
        "oversized declared Content-Length rejected before streaming")
    val actual=FakeHttps(bytes=ByteArray(32769),declaredLength=-1)
    yes(WatchdogHttpsTransport{actual}.get(sampleUrl).body.isEmpty()&&actual.disconnected,
        "streaming body cap enforced with no Content-Length")
    val gz=FakeHttps(encoding="gzip")
    yes(WatchdogHttpsTransport{gz}.get(sampleUrl).body.isEmpty()&&!gz.openedStream,
        "unexpected content encoding rejected")
    val fails=FakeHttps(failRead=true)
    val failed=runCatching{WatchdogHttpsTransport{fails}.get(sampleUrl)}
    yes(failed.isFailure&&fails.disconnected,"I/O failure still closes connection")
    println("PASS: $count/$count fake HTTPS connection transport safety checks")
}
