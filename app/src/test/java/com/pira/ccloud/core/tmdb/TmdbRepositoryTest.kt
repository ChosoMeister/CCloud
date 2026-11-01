package com.pira.ccloud.core.tmdb

import com.pira.ccloud.core.model.ContentType
import com.pira.ccloud.core.network.FailureKind
import com.pira.ccloud.core.network.RetryPolicy
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class TmdbRepositoryTest {
    private val find = """{"movie_results":[{"id":42,"title":"North","original_title":"North","release_date":"2025-03-01"}],"tv_results":[]}"""
    private val emptySearch = """{"results":[]}"""
    private val details = """{"id":42,"title":"North","vote_average":7.1,"vote_count":120}"""

    private fun repo(enabled: Boolean = true, t: TmdbTransport) =
        TmdbRepository(t, { enabled }, retry = RetryPolicy(maxAttempts = 3, baseDelayMs = 1, maxDelayMs = 5))

    private fun router(calls: AtomicInteger? = null, onPath: (String) -> String) = TmdbTransport { p, _ ->
        calls?.incrementAndGet(); onPath(p)
    }

    private fun ok(p: String) = when {
        p.startsWith("/find") -> find
        p.startsWith("/search") -> emptySearch
        else -> details
    }

    @Test fun disabledShortCircuits() = runBlocking {
        val calls = AtomicInteger()
        val r = repo(false, router(calls, ::ok)).enrich("m1", listOf("North"), 2025, ContentType.MOVIE, "tt1")
        assertEquals(EnrichmentResult.Disabled, r); assertEquals(0, calls.get())
    }

    @Test fun imdbMatchReturnsReady() = runBlocking {
        val r = repo(t = router(onPath = ::ok)).enrich("m1", listOf("North"), 2025, ContentType.MOVIE, "tt1")
        assertTrue(r is EnrichmentResult.Ready)
    }

    @Test fun noCandidatesIsNotMatchedAndMissCached() = runBlocking {
        val calls = AtomicInteger()
        val repo = repo(t = router(calls) { emptySearch })
        assertEquals(EnrichmentResult.NotMatched, repo.enrich("m2", listOf("Zzz"), 2020, ContentType.MOVIE))
        val after = calls.get()
        assertEquals(EnrichmentResult.NotMatched, repo.enrich("m2", listOf("Zzz"), 2020, ContentType.MOVIE))
        assertEquals(after, calls.get())
    }

    @Test fun unauthorizedNotRetried() = runBlocking {
        val calls = AtomicInteger()
        val r = repo(t = router(calls) { throw TmdbHttpException(401, null) })
            .enrich("m1", listOf("North"), 2025, ContentType.MOVIE, "tt1")
        assertEquals(EnrichmentResult.Failed(FailureKind.UNAUTHORIZED), r); assertEquals(1, calls.get())
    }

    @Test fun serverErrorRetriedThenSucceeds() = runBlocking {
        val calls = AtomicInteger()
        val r = repo(t = router(calls) { p -> if (calls.get() == 1) throw TmdbHttpException(503, null) else ok(p) })
            .enrich("m1", listOf("North"), 2025, ContentType.MOVIE, "tt1")
        assertTrue(r is EnrichmentResult.Ready)
    }

    @Test fun rateLimitExhaustsAttempts() = runBlocking {
        val calls = AtomicInteger()
        val r = repo(t = router(calls) { throw TmdbHttpException(429, "0") })
            .enrich("m1", listOf("North"), 2025, ContentType.MOVIE, "tt1")
        assertEquals(EnrichmentResult.Failed(FailureKind.RATE_LIMITED), r); assertEquals(3, calls.get())
    }

    @Test fun invalidJsonIsFailure() = runBlocking {
        val r = repo(t = router { "not json" }).enrich("m1", listOf("North"), 2025, ContentType.MOVIE, "tt1")
        assertTrue(r is EnrichmentResult.Failed)
    }

    @Test fun cacheHitAvoidsNetwork() = runBlocking {
        val calls = AtomicInteger()
        val repo = repo(t = router(calls, ::ok))
        repo.enrich("m1", listOf("North"), 2025, ContentType.MOVIE, "tt1")
        val n = calls.get()
        assertTrue(repo.enrich("m1", listOf("North"), 2025, ContentType.MOVIE, "tt1") is EnrichmentResult.Ready)
        assertEquals(n, calls.get())
    }

    @Test fun concurrentRequestsCoalesce() = runBlocking {
        val calls = AtomicInteger()
        val repo = repo(t = TmdbTransport { p, _ -> calls.incrementAndGet(); delay(50); ok(p) })
        val rs = (1..5).map { async { repo.enrich("m1", listOf("North"), 2025, ContentType.MOVIE, "tt1") } }.awaitAll()
        assertTrue(rs.all { it is EnrichmentResult.Ready })
        assertEquals(2, calls.get()) // one /find + one details
    }
}
