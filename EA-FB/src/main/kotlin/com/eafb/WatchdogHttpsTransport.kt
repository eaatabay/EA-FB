package com.eafb

import java.io.ByteArrayOutputStream
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Strict read-only HTTPS transport for a separately reviewed first-party
 * Watchdog endpoint. No third-party adapters, cookies, auth or redirects.
 * This transport does NOT pin remote IPs for source probing and must never
 * be reused for the server-side Source Watchdog probe network.
 */
class WatchdogHttpsTransport : WatchdogSnapshotTransport {
    override suspend fun get(endpoint: String): WatchdogHttpSnapshot = withContext(Dispatchers.IO) {
        val connection = (URL(endpoint).openConnection() as HttpsURLConnection)
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
            if (status != 200) return@withContext WatchdogHttpSnapshot(status, null, byteArrayOf())
            if (connection.contentLengthLong > WatchdogSnapshotRefresh.MAX_BODY_BYTES ||
                connection.contentEncoding?.lowercase() !in listOf(null, "identity")) {
                return@withContext WatchdogHttpSnapshot(200, null, byteArrayOf())
            }
            val body = ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val block = ByteArray(4096)
                while (true) {
                    val n = input.read(block)
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
