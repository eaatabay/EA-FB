package com.eafb

import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets

/** Boundary between untrusted HTTPS bytes and the strict signed data model. */
object WatchdogSnapshotJson {
    private val envelopeKeys = setOf("envelopeVersion", "algorithm", "keyId", "payload", "signature")
    private val payloadKeys = setOf("schemaVersion", "revision", "generatedAt", "expiresAt", "sources")
    private val sourceKeys = setOf("id", "mediaKind", "baseUrl", "adapterVersion")
    private const val MAX_SAFE_JS_INTEGER = 9_007_199_254_740_991L

    fun parse(raw: String): SignedSourceEnvelope? = runCatching {
        require(raw.toByteArray(StandardCharsets.UTF_8).size <= 32_768)
        val envelope = JSONObject(raw)
        require(envelope.keys().asSequence().toSet() == envelopeKeys)
        val payload = envelope.getJSONObject("payload")
        require(payload.keys().asSequence().toSet() == payloadKeys)
        val jsonSources: JSONArray = payload.getJSONArray("sources")
        require(jsonSources.length() <= 256)
        val sources = (0 until jsonSources.length()).map { index ->
            val source = jsonSources.getJSONObject(index)
            require(source.keys().asSequence().toSet() == sourceKeys)
            SnapshotSource(
                string(source, "id"), string(source, "mediaKind"),
                string(source, "baseUrl"), exactInt(source, "adapterVersion")
            )
        }
        SignedSourceEnvelope(
            exactInt(envelope, "envelopeVersion"),
            string(envelope, "algorithm"), string(envelope, "keyId"),
            SourceSnapshot(
                exactInt(payload, "schemaVersion"), exactLong(payload, "revision"),
                exactLong(payload, "generatedAt"), exactLong(payload, "expiresAt"), sources
            ),
            string(envelope, "signature")
        )
    }.getOrNull()

    private fun string(json: JSONObject, name: String): String =
        json.get(name) as? String ?: error("non_string_field")

    private fun exactLong(json: JSONObject, name: String): Long {
        val value = json.get(name)
        require(value is Number && value !is Float && value !is Double)
        val literal = value.toString()
        require(literal.matches(Regex("0|[1-9][0-9]*")))
        val parsed = literal.toLongOrNull() ?: error("integer_overflow")
        require(parsed in 0..MAX_SAFE_JS_INTEGER)
        return parsed
    }

    private fun exactInt(json: JSONObject, name: String): Int {
        val value = exactLong(json, name)
        require(value <= Int.MAX_VALUE)
        return value.toInt()
    }
}
