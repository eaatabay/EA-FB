package com.eafb

/**
 * Offline guard: unknown playback identities and missing approved offers
 * must never reach any source adapter.
 */
fun main() {
    val engine = MultiSourceEngine(emptyList())
    val resolution = PlaybackResolution(engine)
    kotlinx.coroutines.runBlocking {
        check(resolution.resolve(
            "ea-fb:movie:42", "Film", 2026, emptyList(), 1L
        ).isEmpty())
        check(resolution.resolve(
            "ea-fb:episode:42:1:2", "Dizi", 2026, emptyList(), 1L
        ).isEmpty())
        check(resolution.resolve(
            "ea-fb:episode:42:1:0", "Dizi", 2026, emptyList(), 1L
        ).isEmpty())
        check(resolution.resolve(
            "ea-fb:open:big-buck-bunny", "Big Buck Bunny", 2008,
            emptyList(), 1L
        ).isEmpty())
    }
    println("PASS: playback handoff rejects missing offers and invalid identities")
}
