package com.pira.ccloud.core.cache

/**
 * Bounded in-memory LRU with per-entry TTL. Thread-safe. Values may be null-like markers
 * (e.g. cached "no match") by using a wrapper type at the call site.
 */
class TtlCache<K : Any, V : Any>(
    private val maxEntries: Int,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private data class Entry<V>(val value: V, val storedAt: Long, val ttlMs: Long)

    private val map = object : LinkedHashMap<K, Entry<V>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, Entry<V>>?) = size > maxEntries
    }

    @Synchronized fun put(key: K, value: V, ttlMs: Long) { map[key] = Entry(value, clock(), ttlMs) }

    /** Fresh value or null (expired entries are evicted). */
    @Synchronized fun get(key: K): V? {
        val e = map[key] ?: return null
        if (clock() - e.storedAt > e.ttlMs) { map.remove(key); return null }
        return e.value
    }

    @Synchronized fun invalidate(key: K) { map.remove(key) }
    @Synchronized fun clear() = map.clear()
    @Synchronized fun size() = map.size
}

/** Initial TTLs, matched to data volatility (values from 30nama-tizenos tmdb.js, MIT). */
object CacheTtl {
    private const val HOUR = 3_600_000L
    const val DETAILS = 168 * HOUR      // cast, reviews, static metadata: 7 days
    const val MATCH_HIT = 168 * HOUR    // successful catalog→TMDB mapping: 7 days
    const val MATCH_MISS = 24 * HOUR    // failed mapping retried daily
    const val TRENDING = 6 * HOUR
}
