package com.pira.ccloud.core.playback

/**
 * Pure playback-progress rules (local only — the catalog API has no verified progress-sync
 * endpoint, so nothing is synced remotely). Thresholds informed by vhdmsm/CCloud
 * `PlaybackPositions.kt` (MIT) and common streaming conventions.
 */
object PlaybackProgress {
    /** Positions before this are not worth resuming. */
    const val MIN_RESUME_MS = 30_000L
    /** At/after this fraction of the duration the item counts as finished. */
    const val FINISHED_FRACTION = 0.92
    /** Credits-length tail treated as finished for long content even below the fraction. */
    const val FINISHED_TAIL_MS = 3 * 60_000L

    enum class State { NOT_STARTED, IN_PROGRESS, FINISHED }

    fun state(positionMs: Long, durationMs: Long): State {
        if (positionMs < MIN_RESUME_MS) return State.NOT_STARTED
        if (durationMs <= 0) return State.IN_PROGRESS
        val remaining = durationMs - positionMs
        val longForm = durationMs >= 20 * 60_000L
        return if (positionMs >= durationMs * FINISHED_FRACTION || (longForm && remaining <= FINISHED_TAIL_MS))
            State.FINISHED else State.IN_PROGRESS
    }

    /** Fraction in [0,1] for progress bars; 0 when duration unknown. */
    fun fraction(positionMs: Long, durationMs: Long): Float =
        if (durationMs <= 0) 0f else (positionMs.toDouble() / durationMs).coerceIn(0.0, 1.0).toFloat()

    /** Where to start playback: resume in-progress items, restart finished/not-started ones. */
    fun resumePosition(positionMs: Long, durationMs: Long): Long =
        if (state(positionMs, durationMs) == State.IN_PROGRESS) (positionMs - 3_000L).coerceAtLeast(0L) else 0L

    /** Stable storage key; episodes include season and episode ids. */
    fun key(catalogId: Int, seasonId: Int? = null, episodeId: Int? = null): String =
        if (episodeId != null) "s$catalogId:${seasonId ?: 0}:$episodeId" else "m$catalogId"
}
