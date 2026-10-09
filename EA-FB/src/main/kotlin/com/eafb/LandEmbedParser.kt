package com.eafb

import java.net.URI
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup

/** Read an HTTPS embed from either JSON or HTML AJAX response formats. */
object LandEmbedParser {
    fun find(body: String, site: String): String? {
        fun valid(value: String): String? {
            val raw = value.trim().replace("&amp;", "&")
            if (raw.isBlank()) return null
            val absolute = when {
                raw.startsWith("https://") -> raw
                raw.startsWith("//") -> "https:" + raw
                raw.startsWith("/") -> runCatching { URI(site).resolve(raw).toString() }.getOrNull()
                else -> null
            } ?: return null
            return absolute.takeIf {
                runCatching {
                    val u = URI(it)
                    u.scheme == "https" && u.host != null && u.userInfo == null
                }.getOrDefault(false)
            }
        }
        fun scan(value: Any?, depth: Int): String? {
            if (depth > 6 || value == null || value == JSONObject.NULL) return null
            when (value) {
                is JSONObject -> {
                    for (key in listOf("url", "stream", "embed", "src", "file", "html", "data")) {
                        scan(value.opt(key), depth + 1)?.let { return it }
                    }
                    val keys = value.keys()
                    while (keys.hasNext()) scan(value.opt(keys.next()), depth + 1)?.let { return it }
                }
                is JSONArray -> for (i in 0 until value.length()) {
                    scan(value.opt(i), depth + 1)?.let { return it }
                }
                is String -> {
                    valid(value)?.let { return it }
                    val decoded = value.trim()
                    if (decoded.startsWith("{"))
                        runCatching { JSONObject(decoded) }.getOrNull()?.let {
                            scan(it, depth + 1)?.let { result -> return result }
                        }
                    val doc = Jsoup.parse(decoded, site)
                    for (node in doc.select("iframe[data-src], iframe[src], source[src], video[src]")) {
                        val raw = node.attr("data-src").ifBlank { node.attr("src") }
                        valid(raw)?.let { return it }
                    }
                }
            }
            return null
        }
        val json = runCatching { JSONObject(body) }.getOrNull()
        return scan(json ?: body, 0)
    }
}
