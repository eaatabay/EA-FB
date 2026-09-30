package com.eafb

/**
 * Keeps pagination identity only for the current in-memory provider session.
 * Page 1 resets a feed so normal home refreshes are never suppressed.
 */
class CatalogPageDeduper(private val maxFeeds: Int = 64) {
    private val seenByFeed = LinkedHashMap<String, MutableSet<String>>()

    @Synchronized
    fun <T> filter(feed: String, page: Int, items: List<T>, identity: (T) -> String): List<T> {
        if (page <= 1) {
            if (!seenByFeed.containsKey(feed) && seenByFeed.size >= maxFeeds) {
                val oldest = seenByFeed.keys.firstOrNull()
                if (oldest != null) seenByFeed.remove(oldest)
            }
            seenByFeed[feed] = LinkedHashSet()
        }
        val seen = seenByFeed.getOrPut(feed) { LinkedHashSet() }
        return items.filter { seen.add(identity(it)) }
    }

    @Synchronized
    fun reset(feed: String) {
        seenByFeed.remove(feed)
    }
}
