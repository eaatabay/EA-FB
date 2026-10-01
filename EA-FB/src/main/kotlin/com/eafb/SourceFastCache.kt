package com.eafb

/**
 * Device-process-only playback candidate cache. This does not persist tokens, cookies,
 * headers or URLs in D1. An entry is only a candidate, not proof of playability.
 */
class SourceFastCache(
    private val ttlMillis: Long = 10 * 60_000L,
    private val capacity: Int = 128
) {
    init {
        require(ttlMillis in 1_000L..3_600_000L)
        require(capacity in 1..1_024)
    }

    data class Key(
        val kind: MediaKind,
        val tmdbId: Int,
        val season: Int? = null,
        val episode: Int? = null
    ) {
        init {
            require(tmdbId > 0 && kind != MediaKind.LIVE)
            if (kind == MediaKind.SERIES) {
                require(season != null && season >= 0 && episode != null && episode > 0)
            } else {
                require(season == null && episode == null)
            }
        }
    }

    private data class Entry(val savedAt: Long, val links: List<SourceLink>)
    private val entries = LinkedHashMap<Key, Entry>(16, 0.75f, true)

    @Synchronized
    fun put(key: Key, links: List<SourceLink>, nowMillis: Long) {
        if (nowMillis < 0) return
        val safe = SourcePicker.unique(links).filter { link ->
            link.url.startsWith("https://") &&
                !link.requiresPrivateSession &&
                // Signed/expiring URLs may be used only within their actual lifetime.
                (link.expiresAtMillis == null || link.expiresAtMillis > nowMillis)
        }.take(48)
        if (safe.isEmpty()) {
            entries.remove(key)
            return
        }
        entries[key] = Entry(nowMillis, safe)
        while (entries.size > capacity) entries.remove(entries.keys.first())
    }

    @Synchronized
    fun get(
        key: Key,
        nowMillis: Long,
        preferredLanguage: String = "tr",
        maxQuality: Int = 1080
    ): List<SourceLink> {
        if (nowMillis < 0) return emptyList()
        val entry = entries[key] ?: return emptyList()
        if (nowMillis < entry.savedAt || nowMillis - entry.savedAt >= ttlMillis) {
            entries.remove(key)
            return emptyList()
        }
        val safe = SourcePicker.preferred(entry.links, nowMillis, preferredLanguage, maxQuality)
            .filterNot { it.requiresPrivateSession }
        if (safe.isEmpty()) entries.remove(key)
        return safe
    }

    @Synchronized
    fun invalidate(key: Key) { entries.remove(key) }

    @Synchronized
    fun clear() { entries.clear() }
}
