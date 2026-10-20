package com.pira.ccloud.core.tmdb

import com.pira.ccloud.core.cache.TtlCache
import com.pira.ccloud.core.model.ContentType
import com.pira.ccloud.core.model.DataSource
import com.pira.ccloud.core.model.Rating
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TmdbParserTest {
    private val searchJson = """
        {"page":1,"results":[
          {"id":438631,"title":"Dune","original_title":"Dune","release_date":"2021-09-15","poster_path":"/d.jpg","vote_average":7.8,"vote_count":12000,"extra":1},
          {"id":841,"title":"Dune","release_date":"1984-12-14"},
          {"id":5,"media_type":"person","name":"Someone"}
        ]}""".trimIndent()

    private val detailsJson = """
        {"id":438631,"title":"Dune","overview":"Paul...","tagline":"","runtime":155,"release_date":"2021-09-15",
         "backdrop_path":"/b.jpg","poster_path":"/p.jpg","vote_average":7.8,"vote_count":12000,"imdb_id":"tt1160419",
         "genres":[{"id":1,"name":"Science Fiction"}],
         "credits":{"cast":[{"id":2,"name":"Zendaya","character":"Chani","order":1},{"id":1,"name":"Timothée Chalamet","character":"Paul","order":0,"profile_path":"/t.jpg"}],
                    "crew":[{"id":9,"name":"Denis Villeneuve","job":"Director"},{"id":9,"name":"Denis Villeneuve","job":"Director"},{"id":8,"name":"X","job":"Editor"}]},
         "videos":{"results":[{"key":"t1","site":"YouTube","type":"Teaser","official":true},{"key":"t2","site":"YouTube","type":"Trailer","official":true},{"key":"v","site":"Vimeo","type":"Trailer"}]},
         "reviews":{"total_results":3,"results":[{"id":"r1","author":"","content":"  Great  ","author_details":{"rating":9.0}},{"id":"r2","author":"B","content":""}]},
         "recommendations":{"results":[{"id":693134,"title":"Dune: Part Two","release_date":"2024-02-27","vote_average":8.2,"vote_count":5000}]}}
    """.trimIndent()

    @Test fun searchSkipsPeopleAndParsesYears() {
        val c = TmdbParser.searchCandidates(searchJson, ContentType.MOVIE)
        assertEquals(listOf(438631, 841), c.map { it.providerId })
        assertEquals(2021, c[0].year)
    }

    @Test fun findAttachesImdbIdAndType() {
        val c = TmdbParser.findCandidates("""{"movie_results":[],"tv_results":[{"id":1399,"name":"Game of Thrones","first_air_date":"2011-04-17"}]}""", "tt0944947")
        assertEquals(ContentType.SERIES, c.single().type)
        assertEquals("tt0944947", c.single().imdbId)
    }

    @Test fun detailsMapping() {
        val e = TmdbParser.details(detailsJson, ContentType.MOVIE)
        assertEquals("Timothée Chalamet", e.cast.first().name) // sorted by order
        assertEquals("https://image.tmdb.org/t/p/w185/t.jpg", e.cast.first().imageUrl)
        assertEquals(1, e.directors.size)
        assertEquals("t2", e.trailer!!.youTubeKey) // Trailer preferred over Teaser, YouTube only
        assertNull(e.tagline)
        assertEquals(155, e.runtimeMinutes)
        assertEquals("tt1160419", e.imdbId)
        assertEquals(1, e.reviews.size)
        assertEquals("Anonymous", e.reviews[0].author)
        assertEquals("Great", e.reviews[0].content)
        assertEquals(3, e.totalReviews)
        assertEquals(DataSource.TMDB, e.source)
    }

    @Test fun tmdbScoreIsNeverLabeledImdb() {
        val e = TmdbParser.details(detailsJson, ContentType.MOVIE)
        assertEquals(Rating.Kind.TMDB, e.rating!!.kind)
        assertEquals(DataSource.TMDB, e.rating!!.reportedBy)
    }

    @Test fun lowVoteCountHidesRating() {
        val e = TmdbParser.details("""{"id":1,"title":"x","vote_average":9.9,"vote_count":3}""", ContentType.MOVIE)
        assertNull(e.rating)
        assertTrue(e.cast.isEmpty())
        assertNull(e.trailer)
    }

    @Test fun seriesRuntimeFromEpisodeRunTime() {
        val e = TmdbParser.details("""{"id":1,"name":"S","episode_run_time":[0,52],"first_air_date":"2011-01-01"}""", ContentType.SERIES)
        assertEquals(52, e.runtimeMinutes)
        assertEquals(2011, e.year)
    }

    @Test(expected = kotlinx.serialization.SerializationException::class)
    fun invalidJsonThrows() { TmdbParser.details("{not json", ContentType.MOVIE) }

    @Test fun imageHelpersRejectBadPaths() {
        assertNull(TmdbImages.poster(null))
        assertNull(TmdbImages.poster("http://evil/x.jpg"))
    }
}

class TtlCacheTest {
    private var now = 0L
    private val cache = TtlCache<String, String>(maxEntries = 2) { now }

    @Test fun expiresAfterTtl() {
        cache.put("a", "1", ttlMs = 100)
        now = 100; assertEquals("1", cache.get("a"))
        now = 101; assertNull(cache.get("a"))
        assertEquals(0, cache.size())
    }

    @Test fun evictsLeastRecentlyUsed() {
        cache.put("a", "1", 1000); cache.put("b", "2", 1000)
        cache.get("a") // touch a
        cache.put("c", "3", 1000)
        assertNull(cache.get("b"))
        assertEquals("1", cache.get("a"))
    }

    @Test fun invalidateAndClear() {
        cache.put("a", "1", 1000); cache.invalidate("a"); assertNull(cache.get("a"))
        cache.put("b", "2", 1000); cache.clear(); assertEquals(0, cache.size())
    }
}
