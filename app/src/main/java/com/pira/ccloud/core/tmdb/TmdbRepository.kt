package com.pira.ccloud.core.tmdb

import android.content.Context
import androidx.core.content.edit
import com.pira.ccloud.core.cache.CacheTtl
import com.pira.ccloud.core.cache.TtlCache
import com.pira.ccloud.core.matching.ContentMatcher
import com.pira.ccloud.core.matching.MatchQuery
import com.pira.ccloud.core.matching.MatchResult
import com.pira.ccloud.core.model.ContentType
import com.pira.ccloud.core.network.FailureClassifier
import com.pira.ccloud.core.network.FailureKind
import com.pira.ccloud.core.network.RetryPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * User-supplied TMDB credential. Stored in app-private prefs; this is NOT secret-grade storage,
 * and no credential is ever embedded in the APK. Accepts a v4 Read Access Token (JWT, "eyJ...")
 * or a v3 API key (32 hex chars).
 */
class TmdbCredentials(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("cinematic_tmdb", Context.MODE_PRIVATE)
    var value: String?
        get() = prefs.getString(KEY, null)?.trim()?.takeIf { it.isNotEmpty() }
        set(v) = prefs.edit { if (v.isNullOrBlank()) remove(KEY) else putString(KEY, v.trim()) }

    companion object { private const val KEY = "token" }
}

/** HTTP result abstraction so the repository can be tested with fakes. */
fun interface TmdbTransport {
    /** Returns body on 2xx; throws [TmdbHttpException] otherwise. */
    suspend fun get(path: String, query: Map<String, String>): String
}

class TmdbHttpException(val code: Int, val retryAfter: String?) : Exception("TMDB HTTP $code")

class OkHttpTmdbTransport(
    private val credential: () -> String?,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build(),
) : TmdbTransport {
    override suspend fun get(path: String, query: Map<String, String>): String = withContext(Dispatchers.IO) {
        val cred = credential() ?: throw TmdbHttpException(401, null)
        val isBearer = cred.startsWith("eyJ")
        val url = "https://api.themoviedb.org/3$path".toHttpUrl().newBuilder().apply {
            query.forEach { (k, v) -> addQueryParameter(k, v) }
            if (!isBearer) addQueryParameter("api_key", cred)
        }.build()
        val req = Request.Builder().url(url).header("Accept", "application/json")
            .apply { if (isBearer) header("Authorization", "Bearer $cred") }.build()
        client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw TmdbHttpException(r.code, r.header("Retry-After"))
            r.body?.string() ?: throw TmdbHttpException(r.code, null)
        }
    }
}

sealed interface EnrichmentResult {
    data class Ready(val enrichment: Enrichment, val confidence: Double) : EnrichmentResult
    /** Low confidence / no candidates: show catalog data only, never guessed metadata. */
    data object NotMatched : EnrichmentResult
    data object Disabled : EnrichmentResult
    data class Failed(val failure: FailureKind) : EnrichmentResult
}

/**
 * Optional TMDB enrichment for a catalog title. Enrichment is fetched only when a details
 * screen opens (never per card while scrolling). Bounded concurrency, request coalescing,
 * transient retries, separate hit/miss caches keyed with the matcher version.
 */
class TmdbRepository(
    private val transport: TmdbTransport,
    private val isEnabled: () -> Boolean,
    private val language: () -> String = { "en-US" },
    private val retry: RetryPolicy = RetryPolicy(maxAttempts = 3),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private sealed interface MatchCache {
        data class Hit(val tmdbId: Int, val type: ContentType, val confidence: Double) : MatchCache
        data object Miss : MatchCache
    }

    private val matches = TtlCache<String, MatchCache>(500, clock)
    private val details = TtlCache<String, Enrichment>(100, clock)
    private val inflight = mutableMapOf<String, Deferred<EnrichmentResult>>()
    private val lock = Mutex()
    private val permits = Semaphore(4)

    suspend fun enrich(catalogKey: String, titles: List<String>, year: Int?, type: ContentType, imdbId: String? = null): EnrichmentResult {
        if (!isEnabled()) return EnrichmentResult.Disabled
        val key = "$catalogKey|v${ContentMatcher.ALGORITHM_VERSION}|${language()}"
        return coroutineScope {
            val job = lock.withLock {
                inflight[key] ?: async { doEnrich(key, titles, year, type, imdbId) }.also { inflight[key] = it }
            }
            try { job.await() } finally { lock.withLock { if (inflight[key] === job) inflight.remove(key) } }
        }
    }

    /** Lets users fix a wrong mapping or refresh after changing token/language. */
    fun invalidate(catalogKey: String) {
        val key = "$catalogKey|v${ContentMatcher.ALGORITHM_VERSION}|${language()}"
        matches.invalidate(key)
    }

    fun clearCache() { matches.clear(); details.clear() }

    @Volatile private var trendingCache: Pair<Long, List<TrendingTitle>>? = null

    /**
     * Home spotlight feed. TMDB has no weekly box-office figures, so "now playing" sorted by
     * popularity is the box-office proxy; it is followed by this week's trending movies and series.
     */
    suspend fun trending(): List<TrendingTitle> {
        if (!isEnabled()) return emptyList()
        trendingCache?.let { (t, list) -> if (clock() - t < 30 * 60_000L && list.isNotEmpty()) return list }
        val lang = mapOf("language" to language())
        val lists = coroutineScope {
            listOf(
                async { runCatching { TmdbParser.trending(call("/movie/now_playing", lang + ("page" to "1")), ContentType.MOVIE) }.getOrDefault(emptyList()) },
                async { runCatching { TmdbParser.trending(call("/trending/movie/week", lang), ContentType.MOVIE) }.getOrDefault(emptyList()) },
                async { runCatching { TmdbParser.trending(call("/trending/tv/week", lang), ContentType.SERIES) }.getOrDefault(emptyList()) },
                async { runCatching { TmdbParser.trending(call("/trending/movie/week", lang + ("page" to "2")), ContentType.MOVIE) }.getOrDefault(emptyList()) },
            ).map { it.await() }
        }
        val merged = lists.flatten().distinctBy { "${it.type}:${it.tmdbId}" }
        if (merged.isNotEmpty()) trendingCache = clock() to merged
        return merged
    }

    private suspend fun doEnrich(key: String, titles: List<String>, year: Int?, type: ContentType, imdbId: String?): EnrichmentResult = try {
        val match = when (val cached = matches.get(key)) {
            is MatchCache.Hit -> cached
            MatchCache.Miss -> null
            null -> resolveMatch(titles, year, type, imdbId).also {
                if (it != null) matches.put(key, it, CacheTtl.MATCH_HIT) else matches.put(key, MatchCache.Miss, CacheTtl.MATCH_MISS)
            }
        }
        if (match !is MatchCache.Hit) EnrichmentResult.NotMatched
        else EnrichmentResult.Ready(loadDetails(match.tmdbId, match.type), match.confidence)
    } catch (e: CancellationException) { throw e
    } catch (e: TmdbHttpException) { EnrichmentResult.Failed(FailureClassifier.fromHttp(e.code))
    } catch (e: Exception) { EnrichmentResult.Failed(FailureClassifier.fromThrowable(e)) }

    private suspend fun resolveMatch(titles: List<String>, year: Int?, type: ContentType, imdbId: String?): MatchCache.Hit? {
        val query = MatchQuery(titles.filter { it.isNotBlank() }, year, type, imdbId)
        if (query.titles.isEmpty() && imdbId == null) return null
        imdbId?.takeIf { it.startsWith("tt") }?.let { id ->
            val c = TmdbParser.findCandidates(call("/find/$id", mapOf("external_source" to "imdb_id")), id)
            (ContentMatcher.match(query, c) as? MatchResult.Matched)?.let { return it.toHit() }
        }
        val searchTypes = when (type) { ContentType.MOVIE -> listOf("movie"); ContentType.SERIES -> listOf("tv"); ContentType.ANIME -> listOf("tv", "movie") }
        for (st in searchTypes) {
            val ct = if (st == "tv") ContentType.SERIES else ContentType.MOVIE
            for (title in query.titles.take(2)) {
                val params = buildMap {
                    put("query", title); put("include_adult", "false"); put("language", language())
                    if (year != null) put(if (st == "tv") "first_air_date_year" else "year", year.toString())
                }
                val candidates = TmdbParser.searchCandidates(call("/search/$st", params), ct)
                (ContentMatcher.match(query, candidates) as? MatchResult.Matched)?.let { return it.toHit() }
            }
        }
        return null
    }

    private fun MatchResult.Matched.toHit() = MatchCache.Hit(candidate.providerId, candidate.type, confidence)

    private suspend fun loadDetails(tmdbId: Int, type: ContentType): Enrichment {
        val kind = if (type == ContentType.MOVIE) "movie" else "tv"
        val dKey = "$kind:$tmdbId:${language()}"
        details.get(dKey)?.let { return it }
        val json = call("/$kind/$tmdbId", mapOf(
            "language" to language(),
            "append_to_response" to "credits,videos,reviews,recommendations,external_ids",
            "include_video_language" to "en,null",
        ))
        return TmdbParser.details(json, type).also { details.put(dKey, it, CacheTtl.DETAILS) }
    }

    private suspend fun call(path: String, query: Map<String, String>): String = permits.withPermit {
        var attempt = 1
        while (true) {
            try { return@withPermit transport.get(path, query) }
            catch (e: TmdbHttpException) {
                val d = retry.delayFor(attempt, FailureClassifier.fromHttp(e.code), e.retryAfter, clock()) ?: throw e
                delay(d)
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                val d = retry.delayFor(attempt, FailureClassifier.fromThrowable(e), null, clock()) ?: throw e
                delay(d)
            }
            attempt++
        }
        @Suppress("UNREACHABLE_CODE") error("unreachable")
    }
}
