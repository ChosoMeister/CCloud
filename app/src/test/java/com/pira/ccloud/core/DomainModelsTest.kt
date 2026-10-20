package com.pira.ccloud.core

import com.pira.ccloud.core.model.Availability
import com.pira.ccloud.core.model.ContentType
import com.pira.ccloud.core.model.DataSource
import com.pira.ccloud.core.model.FeatureFlags
import com.pira.ccloud.core.model.Rating
import com.pira.ccloud.core.model.toCatalogTitle
import com.pira.ccloud.core.network.FailureClassifier
import com.pira.ccloud.core.network.FailureKind
import com.pira.ccloud.core.network.RetryPolicy
import com.pira.ccloud.core.playback.PlaybackProgress
import com.pira.ccloud.data.model.Country
import com.pira.ccloud.data.model.FavoriteItem
import com.pira.ccloud.data.model.Genre
import com.pira.ccloud.data.model.Movie
import com.pira.ccloud.data.model.Series
import com.pira.ccloud.data.model.Source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class DomainModelsTest {
    private fun movie(sources: List<Source>, imdb: Double = 7.5, rating: Double = 0.0, type: String = "movie") = Movie(
        id = 10, type = type, title = "T", description = "d", year = 2020, imdb = imdb, rating = rating,
        duration = null, image = "https://img/p.jpg", cover = "", genres = listOf(Genre(1, "Drama")),
        sources = sources, country = listOf(Country(1, "Iran", "")))

    @Test fun movieWithoutUsableSourcesIsNotPlayable() {
        val t = movie(listOf(Source(1, "1080p", "mp4", "  "))).toCatalogTitle()
        assertEquals(Availability.NoStreams, t.availability)
        assertFalse(t.availability.isPlayable)
        assertNull(t.backdropUrl)
    }

    @Test fun movieWithSourcesIsPlayableWithDistinctQualities() {
        val a = movie(listOf(Source(1, "720p", "mp4", "u1"), Source(2, "720p", "mp4", "u2"), Source(3, "1080p", "m3u8", "u3")))
            .toCatalogTitle().availability as Availability.Playable
        assertEquals(3, a.sourceCount)
        assertEquals(listOf("720p", "1080p"), a.qualities)
    }

    @Test fun seriesAreEpisodeLevelAvailability() {
        val s = Series(1, "serie", "S", "", 2019, 8.0, 0.0, null, "", "", emptyList(), emptyList()).toCatalogTitle()
        assertEquals(ContentType.SERIES, s.ref.type)
        assertEquals(Availability.EpisodesOnDemand, s.availability)
    }

    @Test fun ratingsKeepTheirTrueSource() {
        val r = movie(emptyList(), imdb = 7.5, rating = 6.0).toCatalogTitle().ratings
        assertEquals(Rating.Kind.IMDB, r[0].kind)
        assertEquals(DataSource.CATALOG, r[0].reportedBy)
        assertEquals(Rating.Kind.CATALOG, r[1].kind)
        assertTrue(movie(emptyList(), imdb = 0.0).toCatalogTitle().ratings.isEmpty())
        assertEquals("7.5", r[0].formatted())
    }

    @Test fun existingFavoritesMapWithoutMigration() {
        val f = FavoriteItem(5, "series", "F", "", 2001, 0.0, 0.0, null, "", "", emptyList(), emptyList())
        assertEquals("SERIES:5", f.toCatalogTitle().stableKey)
    }

    @Test fun catalogTypeMapping() {
        assertEquals(ContentType.SERIES, ContentType.fromCatalogType("serie"))
        assertEquals(ContentType.MOVIE, ContentType.fromCatalogType(null))
        assertEquals(ContentType.ANIME, ContentType.fromCatalogType("Anime"))
    }

    @Test fun tmdbDisabledWithoutToken() {
        assertFalse(FeatureFlags.resolve(null).tmdbEnabled)
        assertFalse(FeatureFlags.resolve("  ").tmdbEnabled)
        assertTrue(FeatureFlags.resolve("abc").tmdbEnabled)
    }
}

class PlaybackProgressTest {
    private val hour = 3_600_000L

    @Test fun earlyPositionIsNotStarted() =
        assertEquals(PlaybackProgress.State.NOT_STARTED, PlaybackProgress.state(10_000, 2 * hour))

    @Test fun middleIsInProgressAndResumesSlightlyEarlier() {
        assertEquals(PlaybackProgress.State.IN_PROGRESS, PlaybackProgress.state(hour, 2 * hour))
        assertEquals(hour - 3_000, PlaybackProgress.resumePosition(hour, 2 * hour))
    }

    @Test fun nearEndIsFinishedAndRestarts() {
        assertEquals(PlaybackProgress.State.FINISHED, PlaybackProgress.state(2 * hour - 120_000, 2 * hour))
        assertEquals(0L, PlaybackProgress.resumePosition(2 * hour - 60_000, 2 * hour))
    }

    @Test fun shortClipUsesFractionOnly() {
        val d = 10 * 60_000L // 10 min: tail rule must not apply
        assertEquals(PlaybackProgress.State.IN_PROGRESS, PlaybackProgress.state(8 * 60_000L, d))
    }

    @Test fun unknownDurationStaysInProgress() {
        assertEquals(PlaybackProgress.State.IN_PROGRESS, PlaybackProgress.state(hour, 0))
        assertEquals(0f, PlaybackProgress.fraction(hour, 0))
    }

    @Test fun fractionIsClamped() = assertEquals(1f, PlaybackProgress.fraction(3 * hour, 2 * hour))

    @Test fun keysDistinguishMoviesAndEpisodes() {
        assertEquals("m7", PlaybackProgress.key(7))
        assertEquals("s7:2:31", PlaybackProgress.key(7, 2, 31))
    }
}

class RetryPolicyTest {
    @Test fun httpClassification() {
        assertEquals(FailureKind.RATE_LIMITED, FailureClassifier.fromHttp(429))
        assertEquals(FailureKind.UNAUTHORIZED, FailureClassifier.fromHttp(401))
        assertEquals(FailureKind.SERVER, FailureClassifier.fromHttp(503))
        assertEquals(FailureKind.TIMEOUT, FailureClassifier.fromThrowable(java.net.SocketTimeoutException()))
        assertEquals(FailureKind.OFFLINE, FailureClassifier.fromThrowable(java.net.UnknownHostException()))
    }

    @Test fun nonTransientFailuresAreNotRetried() {
        val p = RetryPolicy()
        assertNull(p.delayFor(1, FailureKind.NOT_FOUND))
        assertNull(p.delayFor(1, FailureKind.FORBIDDEN))
        assertNull(p.delayFor(1, FailureKind.INVALID_DATA))
    }

    @Test fun stopsAfterMaxAttempts() = assertNull(RetryPolicy(maxAttempts = 3).delayFor(3, FailureKind.SERVER))

    @Test fun backoffGrowsWithJitterWithinBounds() {
        val p = RetryPolicy(maxAttempts = 10, baseDelayMs = 500, maxDelayMs = 8_000, random = Random(42))
        repeat(50) {
            val d1 = p.delayFor(1, FailureKind.SERVER)!!
            assertTrue(d1 in 250..500)
            val d5 = p.delayFor(5, FailureKind.SERVER)!!
            assertTrue(d5 in 4_000..8_000)
            assertTrue(p.delayFor(9, FailureKind.SERVER)!! <= 8_000)
        }
    }

    @Test fun retryAfterSecondsAndDate() {
        assertEquals(20_000L, RetryPolicy(maxAttempts = 3).delayFor(1, FailureKind.RATE_LIMITED, "20"))
        assertNull(RetryPolicy(maxAttempts = 3).delayFor(1, FailureKind.RATE_LIMITED, "120")) // over cap: give up
        val now = 1_792_497_600_000L // Tue, 20 Oct 2026 12:00:00 GMT
        assertEquals(30_000L, RetryPolicy.parseRetryAfter("Tue, 20 Oct 2026 12:00:30 GMT", now))
        assertEquals(0L, RetryPolicy.parseRetryAfter("Tue, 20 Oct 2026 11:00:00 GMT", now))
        assertNull(RetryPolicy.parseRetryAfter("garbage"))
        assertNull(RetryPolicy.parseRetryAfter(null))
    }
}
