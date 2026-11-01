package com.pira.ccloud.core.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressStoreTest {
    private class MemKv : KeyValueStore {
        val map = linkedMapOf<String, String>()
        override fun all() = map.toMap()
        override fun put(key: String, value: String) { map[key] = value }
        override fun remove(keys: Collection<String>) { keys.forEach { map.remove(it) } }
    }

    private val hour = 3_600_000L
    private var now = 1_000L
    private val kv = MemKv()
    private val store = ProgressStore(kv, maxEntries = 3) { now++ }

    @Test fun savesInProgressAndReadsBack() {
        assertEquals(PlaybackProgress.State.IN_PROGRESS, store.save(7, null, null, hour, 2 * hour))
        val e = store.get("m7")!!
        assertEquals(hour, e.positionMs)
        assertEquals(0.5f, e.fraction)
    }

    @Test fun finishedRemovesEntry() {
        store.save(7, null, null, hour, 2 * hour)
        assertEquals(PlaybackProgress.State.FINISHED, store.save(7, null, null, 2 * hour - 1000, 2 * hour))
        assertNull(store.get("m7"))
    }

    @Test fun prunesOldestBeyondLimit() {
        (1..5).forEach { store.save(it, null, null, hour, 2 * hour) }
        assertEquals(3, kv.map.size)
        assertEquals(listOf(5, 4, 3), store.recent().map { it.catalogId })
    }

    @Test fun recentByTitleKeepsLatestEpisodePerSeries() {
        store.save(9, 1, 101, hour, 2 * hour)
        store.save(9, 1, 102, hour, 2 * hour)
        store.save(8, null, null, hour, 2 * hour)
        val r = store.recentByTitle()
        assertEquals(2, r.size)
        assertEquals(102, r.first { it.catalogId == 9 }.episodeId)
    }

    @Test fun corruptValuesAreIgnored() {
        kv.put("m1", "garbage")
        kv.put("weird", "1,2,3")
        assertTrue(store.recent().isEmpty())
    }

    @Test fun clearRemovesEverything() {
        store.save(1, null, null, hour, 2 * hour)
        store.clear()
        assertTrue(kv.map.isEmpty())
    }
}
