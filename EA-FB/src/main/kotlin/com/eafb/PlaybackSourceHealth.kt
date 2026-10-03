package com.eafb

/**
 * Explicit health observations, not an authorization source. A source is
 * eligible only while its latest observation is healthy and unexpired.
 * Health can never override user opt-in.
 */
data class SourceHealthObservation(
    val sourceId: String,
    val healthy: Boolean,
    val observedAtMillis: Long
)

object PlaybackSourceHealth {
    fun healthyIds(
        observations: List<SourceHealthObservation>,
        nowMillis: Long,
        ttlMillis: Long = 15 * 60 * 1000L
    ): Set<String> {
        require(nowMillis >= 0 && ttlMillis > 0)
        return observations.asSequence()
            .filter { it.sourceId.isNotBlank() && it.observedAtMillis >= 0 }
            .groupBy { it.sourceId }
            .mapNotNull { (id, history) ->
                val latest = history.maxByOrNull { it.observedAtMillis } ?: return@mapNotNull null
                if (latest.healthy && latest.observedAtMillis <= nowMillis &&
                    nowMillis - latest.observedAtMillis < ttlMillis) id else null
            }.toSet()
    }
}
