package com.eafb

/** An adapter whose implementation/version is bundled in the installed .cs3. */
interface VerifiedBaseUrlAdapter : MediaSourceAdapter {
    val adapterVersion: Int
    /** May configure ONLY the verified base URL, never download Kotlin/parser code. */
    fun configuredForVerifiedBaseUrl(baseUrl: String): MediaSourceAdapter
}

object SourceSnapshotGate {
    /** Only for NEW searches. Does not tear down an already-playing stream. */
    fun forNewSearch(
        snapshot: VerifiedSourceSnapshot?,
        now: Long,
        bundledAdapters: List<VerifiedBaseUrlAdapter>
    ): List<MediaSourceAdapter> {
        if (snapshot == null) return emptyList()
        val available = bundledAdapters.associateBy { it.id }
        if (available.size != bundledAdapters.size) return emptyList() // ambiguous IDs fail closed
        return snapshot.usableAt(now).mapNotNull { source ->
            val adapter = available[source.id] ?: return@mapNotNull null
            if (adapter.adapterVersion != source.adapterVersion) return@mapNotNull null
            runCatching { adapter.configuredForVerifiedBaseUrl(source.baseUrl) }
                .getOrNull()?.takeIf { it.id == source.id }
        }
    }
}
