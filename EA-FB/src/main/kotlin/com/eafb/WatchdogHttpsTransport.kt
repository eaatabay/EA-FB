package com.eafb

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Strict read-only HTTPS transport for a separately reviewed first-party
 * Watchdog endpoint. No third-party adapters, cookies, auth or redirects.
 * This transport does NOT pin remote IPs for source probing and must never
 * be reused for the server-side Source Watchdog probe network.
 */
class WatchdogHttpsTransport(
    private val open: (String) -> HttpsURLConnection = { endpoint ->
        URL(endpoint).openConnection() as HttpsURLConnection
    },
    private val nanoClock: () -> Long = System::nanoTime
) : WatchdogSnapshotTransport {
    override suspend fun get(endpoint: String): WatchdogHttpSnapshot = withContext(Dispatchers.IO) {
        val started = nanoClock()
        val context = currentCoroutineContext()
        val connection = open(endpoint)
        fun checkBudget() {
            context.ensureActive()
            if (nanoClock() - started >= 12_000_000_000L) {
                throw IOException("watchdog_response_deadline_exceeded")
            }
        }
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 4_000
            connection.readTimeout = 4_000
            connection.useCaches = false
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Cache-Control", "no-store")
            connection.setRequestProperty("Accept-Encoding", "identity")
            val status = connection.responseCode
            checkBudget()
            if (status != 200) return@withContext WatchdogHttpSnapshot(status, null, byteArrayOf())
            if (connection.contentLengthLong > WatchdogSnapshotRefresh.MAX_BODY_BYTES ||
                connection.contentEncoding?.lowercase() !in listOf(null, "identity")) {
                return@withContext WatchdogHttpSnapshot(200, null, byteArrayOf())
            }
            val body = ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val block = ByteArray(4096)
                while (true) {
                    checkBudget()
                    val n = input.read(block)
                    checkBudget()
                    if (n < 0) break
                    if (body.size() + n > WatchdogSnapshotRefresh.MAX_BODY_BYTES) {
                        return@withContext WatchdogHttpSnapshot(200, null, byteArrayOf())
                    }
                    body.write(block, 0, n)
                }
            }
            WatchdogHttpSnapshot(200, connection.contentType, body.toByteArray())
        } finally { connection.disconnect() }
    }
}
