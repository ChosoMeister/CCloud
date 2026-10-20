package com.pira.ccloud.core.playback

import android.content.Context
import androidx.core.content.edit

/** Minimal key/value abstraction so the store is unit-testable without Android. */
interface KeyValueStore {
    fun all(): Map<String, String>
    fun put(key: String, value: String)
    fun remove(keys: Collection<String>)
}

class SharedPrefsKeyValueStore(context: Context, name: String = "cinematic_progress") : KeyValueStore {
    private val prefs = context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)
    override fun all(): Map<String, String> = prefs.all.mapNotNull { (k, v) -> (v as? String)?.let { k to it } }.toMap()
    override fun put(key: String, value: String) { prefs.edit { putString(key, value) } }
    override fun remove(keys: Collection<String>) { prefs.edit { keys.forEach { remove(it) } } }
}

data class ProgressEntry(
    val key: String,
    val catalogId: Int,
    val seasonId: Int?,
    val episodeId: Int?,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
) {
    val fraction: Float get() = PlaybackProgress.fraction(positionMs, durationMs)
}

/**
 * Local, bounded playback-progress store (no remote sync: no verified API exists).
 * Value format: "position,duration,updatedAt" (compatible with vhdmsm/CCloud PlaybackPositions).
 */
class ProgressStore(
    private val kv: KeyValueStore,
    private val maxEntries: Int = 200,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun get(key: String): ProgressEntry? = kv.all()[key]?.let { parse(key, it) }

    /** Saves in-progress positions; removes not-started/finished ones. Returns the resulting state. */
    fun save(catalogId: Int, seasonId: Int?, episodeId: Int?, positionMs: Long, durationMs: Long): PlaybackProgress.State {
        val key = PlaybackProgress.key(catalogId, seasonId, episodeId)
        val state = PlaybackProgress.state(positionMs, durationMs)
        if (state == PlaybackProgress.State.IN_PROGRESS) {
            kv.put(key, "$positionMs,$durationMs,${clock()}")
            prune()
        } else kv.remove(listOf(key))
        return state
    }

    /** Most recent first: input for the Continue Watching rail. */
    fun recent(limit: Int = 20): List<ProgressEntry> =
        kv.all().mapNotNull { (k, v) -> parse(k, v) }.sortedByDescending { it.updatedAt }.take(limit)

    /** Latest in-progress entry per title (one card per series). */
    fun recentByTitle(limit: Int = 20): List<ProgressEntry> =
        recent(Int.MAX_VALUE).distinctBy { it.catalogId to (it.episodeId != null) }.take(limit)

    fun remove(key: String) = kv.remove(listOf(key))
    fun clear() = kv.remove(kv.all().keys)

    private fun prune() {
        val all = kv.all()
        if (all.size <= maxEntries) return
        val oldest = all.mapNotNull { (k, v) -> parse(k, v) }.sortedBy { it.updatedAt }.take(all.size - maxEntries)
        kv.remove(oldest.map { it.key } + all.keys.filter { parseKeyOrNull(it) == null })
    }

    companion object {
        private val episodeKey = Regex("^s(\\d+):(\\d+):(\\d+)$")
        private val movieKey = Regex("^m(\\d+)$")

        internal fun parseKeyOrNull(key: String): Triple<Int, Int?, Int?>? {
            movieKey.matchEntire(key)?.let { return Triple(it.groupValues[1].toInt(), null, null) }
            episodeKey.matchEntire(key)?.let { m ->
                return Triple(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
            }
            return null
        }

        internal fun parse(key: String, value: String): ProgressEntry? {
            val (id, season, episode) = parseKeyOrNull(key) ?: return null
            val parts = value.split(',')
            if (parts.size != 3) return null
            val pos = parts[0].toLongOrNull() ?: return null
            val dur = parts[1].toLongOrNull() ?: return null
            val at = parts[2].toLongOrNull() ?: return null
            return ProgressEntry(key, id, season, episode, pos, dur, at)
        }
    }
}
