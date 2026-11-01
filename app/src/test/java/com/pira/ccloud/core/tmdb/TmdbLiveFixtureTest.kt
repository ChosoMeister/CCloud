package com.pira.ccloud.core.tmdb

import com.pira.ccloud.core.model.ContentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Parses real TMDB responses captured on 2026-10-20 (public data, no credentials). */
class TmdbLiveFixtureTest {
    private fun res(name: String) = javaClass.classLoader!!.getResource("tmdb/$name")!!.readText()

    @Test fun parsesRealFindResponse() {
        val c = TmdbParser.findCandidates(res("find_tt0133093.json"), "tt0133093")
        assertEquals(603, c.first().providerId)
    }

    @Test fun parsesRealDetailsResponse() {
        val e = TmdbParser.details(res("movie_603.json"), ContentType.MOVIE)
        assertEquals("The Matrix", e.title)
        assertEquals(1999, e.year)
        assertEquals("tt0133093", e.imdbId)
        assertTrue(e.cast.any { it.name == "Keanu Reeves" })
        assertTrue(e.directors.isNotEmpty())
        assertNotNull(e.rating)
        assertTrue(e.cast.size <= 20)
        e.trailer?.let { assertTrue(it.youTubeKey.isNotBlank()) }
    }
}
