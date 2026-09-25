package com.eafb

/** Test-only stub; production compiles the strict real org.json parser. */
object WatchdogSnapshotJson {
    var decode: (String) -> SignedSourceEnvelope? = { null }
    fun parse(raw: String): SignedSourceEnvelope? = decode(raw)
}
