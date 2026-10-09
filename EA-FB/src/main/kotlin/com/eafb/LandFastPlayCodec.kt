package com.eafb

import java.net.URI

/** Pure LAND codec: no Android, no network or credentials. */
object LandFastPlayCodec {
    private val spgPattern = Regex("""SPG\s*\.\s*cerceve\s*\(\s*[^,]+\s*,\s*['\"]([^'\"]+)['\"]\s*,\s*['\"]([^'\"]+)['\"]\s*\)""")
    private val spPattern = Regex("""['\"]sp['\"]\s*:\s*['\"]([^'\"]+)['\"]""")
    private val spTimePattern = Regex("""['\"]spT['\"]\s*:\s*['\"]?(\d{10,13})['\"]?""")
    private val streamPattern = Regex("""(?:['\"]?(?:stream|src)['\"]?)\s*:\s*['\"]([^'\"]+)['\"]""")
    private val manifestPattern = Regex("""/manifests/[\w-]+(?:/[^\s'\"<>]*)?""")
    private val resolutionPattern = Regex("""RESOLUTION=\d+x(\d+)""", RegexOption.IGNORE_CASE)
    private val subtitlePattern = Regex("""#EXT-X-MEDIA:[^\r\n]*TYPE=SUBTITLES[^\r\n]*""", RegexOption.IGNORE_CASE)
    private val uriAttribute = Regex("""URI=['\"]([^'\"]+)['\"]""", RegexOption.IGNORE_CASE)
    private val nameAttribute = Regex("""NAME=['\"]([^'\"]+)['\"]""", RegexOption.IGNORE_CASE)

    fun spgOperands(body: String): Pair<String, String>? =
        spgPattern.find(body)?.let { it.groupValues[1] to it.groupValues[2] }

    fun xorDecrypt(data: ByteArray, key: ByteArray): String? {
        if (data.isEmpty() || key.isEmpty()) return null
        return buildString(data.size) {
            data.indices.forEach { index ->
                append(((data[index].toInt() xor key[index % key.size].toInt()) and 255).toChar())
            }
        }
    }

    fun playerUrl(decrypted: String): String? = safeHttps(decrypted.substringBefore('|').trim())
    fun sp(body: String): String? = spPattern.find(body)?.groupValues?.get(1)
    fun timestamp(body: String, nowMillis: Long): Long =
        spTimePattern.find(body)?.groupValues?.get(1)?.toLongOrNull()?.let { value ->
            if (value in 1_000_000_000L..9_999_999_999L) value * 1000L else value
        } ?: nowMillis

    fun streamUrl(body: String, playerUrl: String): String? {
        val raw = streamPattern.find(body)?.groupValues?.get(1)
            ?: manifestPattern.find(body)?.value ?: return null
        return normalizeHttps(playerUrl, raw)
    }

    fun normalizeHttps(pageUrl: String, raw: String): String? = try {
        val base = URI(pageUrl)
        val candidate = base.resolve(raw.trim().replace("&amp;", "&"))
        safeHttps(candidate.toString())
    } catch (_: Exception) { null }

    private fun safeHttps(url: String): String? {
        return try {
            val u = URI(url)
            val host = u.host?.lowercase() ?: return null
            if (u.scheme != "https" || u.userInfo != null || u.fragment != null ||
                host == "localhost" || host == "127.0.0.1" || host == "::1" ||
                host.endsWith(".local") || host.endsWith(".internal") ||
                host.startsWith("10.") || host.startsWith("192.168.") ||
                Regex("""172\.(1[6-9]|2\d|3[01])\..*""").matches(host)) null else u.toString()
        } catch (_: Exception) { null }
    }

    fun fnv1a32Hex(value: String): String {
        var hash = 2166136261L
        for (b in value.toByteArray(Charsets.UTF_8))
            hash = (hash xor (b.toLong() and 255L)) * 16777619L and 0xffffffffL
        return java.lang.Long.toHexString(hash)
    }

    /** Reference format; requires permission and device-side validation. */
    fun xSp(sp: String, timestamp: Long, randomBase36: String): String? {
        if (sp.isBlank() || timestamp <= 0 || !Regex("""[0-9a-z]{6}""").matches(randomBase36)) return null
        return timestamp.toString() + "." + randomBase36 + "." + fnv1a32Hex(sp + "|" + timestamp + "|" + randomBase36)
    }

    fun isHls(body: String): Boolean = body.trimStart('\ufeff', '\n', '\r', ' ', '\t').startsWith("#EXTM3U")
    fun quality(body: String): Int? = resolutionPattern.findAll(body)
        .mapNotNull { it.groupValues[1].toIntOrNull() }.maxOrNull()
    fun subtitles(body: String, masterUrl: String): List<Pair<String, String>> =
        subtitlePattern.findAll(body).mapNotNull { match ->
            val line = match.value
            val name = nameAttribute.find(line)?.groupValues?.get(1)?.takeIf { it.isNotBlank() } ?: "Altyazı"
            val raw = uriAttribute.find(line)?.groupValues?.get(1) ?: return@mapNotNull null
            normalizeHttps(masterUrl, raw)?.let { name to it }
        }.distinct().toList()
}
