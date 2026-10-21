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
    private val baseUrl: String = "https://api.themoviedb.org/3",
) : TmdbTransport {
    override suspend fun get(path: String, query: Map<String, String>): String = withContext(Dispatchers.IO) {
        val cred = credential() ?: throw TmdbHttpException(401, null)
        val isBearer = cred.startsWith("eyJ")
        val url = (baseUrl.trimEnd('/') + path).toHttpUrl().newBuilder().apply {
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
    private val genreCache = TtlCache<String, List<TmdbGenre>>(4, clock)
    private val browseCache = TtlCache<String, List<TrendingTitle>>(40, clock)
    private val browsePageCache = TtlCache<String, TmdbBrowsePage>(80, clock)
    private val inflight = mutableMapOf<String, Deferred<EnrichmentResult>>()
    private val lock = Mutex()
    private val permits = Semaphore(4)

    suspend fun enrich(catalogKey: String, titles: List<String>, year: Int?, type: ContentType, imdbId: String? = null,
                       countries: List<String> = emptyList()): EnrichmentResult {
        if (!isEnabled()) return EnrichmentResult.Disabled
        val key = matchingKey(catalogKey, countries)
        return coroutineScope {
            val job = lock.withLock {
                inflight[key] ?: async { doEnrich(key, titles, year, type, imdbId, countries) }.also { inflight[key] = it }
            }
            try { job.await() } finally { lock.withLock { if (inflight[key] === job) inflight.remove(key) } }
        }
    }

    /** Non-blocking snapshot for repeat details visits, avoiding a loading/reflow frame. */
    fun cachedEnrichment(catalogKey: String, countries: List<String> = emptyList()): Enrichment? {
        val key = matchingKey(catalogKey, countries)
        val hit = matches.get(key) as? MatchCache.Hit ?: return null
        val kind = if (hit.type == ContentType.MOVIE) "movie" else "tv"
        return details.get("$kind:${hit.tmdbId}:${language()}")
    }

    /** Lets users fix a wrong mapping or refresh after changing token/language. */
    fun invalidate(catalogKey: String) {
        matches.invalidate(matchingKey(catalogKey, emptyList()))
        matches.invalidate(matchingKey(catalogKey, listOf("South Korea")))
    }

    fun clearCache() { matches.clear(); details.clear(); genreCache.clear(); browseCache.clear(); browsePageCache.clear() }

    /** Official type-specific TMDB genres. Movie and TV IDs are never interchanged. */
    suspend fun genres(type: ContentType): List<TmdbGenre> {
        if (!isEnabled()) return emptyList()
        val kind = if (type == ContentType.MOVIE) "movie" else "tv"
        val key = "$kind:${language()}"
        genreCache.get(key)?.let { return it }
        return TmdbParser.genres(call("/genre/$kind/list", mapOf("language" to language()))).also {
            genreCache.put(key, it, 24 * 60 * 60_000L)
        }
    }

    /** /search has no with_genres parameter; filter its returned genre_ids locally. */
    suspend fun browse(
        type: ContentType,
        genreId: Int? = null,
        query: String = "",
        pages: Int = 2,
        personId: Int? = null,
        startPage: Int = 1,
    ): List<TrendingTitle> {
        if (!isEnabled()) return emptyList()
        val firstPage = startPage.coerceAtLeast(1)
        val pageCount = pages.coerceAtLeast(1)
        val kind = if (type == ContentType.MOVIE) "movie" else "tv"
        val text = query.trim()
        val key = "$kind:$genreId:$personId:$text:${language()}:$firstPage:$pageCount"
        browseCache.get(key)?.let { return it }
        if (personId != null) {
            val credits = TmdbParser.personCredits(
                call("/person/$personId/combined_credits", mapOf("language" to language())),
                if (type == ContentType.MOVIE) ContentType.MOVIE else ContentType.SERIES,
                genreId,
                text,
            )
            return credits.also { browseCache.put(key, it, 30 * 60_000L) }
        }
        val path = if (text.isBlank()) "/discover/$kind" else "/search/$kind"
        val params = buildMap {
            put("language", language()); put("include_adult", "false")
            if (text.isNotBlank()) put("query", text)
            else {
                put("sort_by", "popularity.desc")
                genreId?.let { put("with_genres", it.toString()) }
            }
        }
        val titles = coroutineScope {
            (firstPage until firstPage + pageCount).map { page ->
                async { TmdbParser.browse(call(path, params + ("page" to page.toString())), type, genreId) }
            }.flatMap { it.await() }
        }.distinctBy { "${it.type}:${it.tmdbId}" }
        browseCache.put(key, titles, 30 * 60_000L)
        return titles
    }

    /** Fetch one discover/search page together with TMDB's own pagination boundary. */
    suspend fun browsePage(
        type: ContentType,
        genreId: Int? = null,
        query: String = "",
        personId: Int? = null,
        page: Int = 1,
    ): TmdbBrowsePage {
        val requestedPage = page.coerceAtLeast(1)
        if (!isEnabled()) return TmdbBrowsePage(emptyList(), requestedPage, requestedPage)
        val kind = if (type == ContentType.MOVIE) "movie" else "tv"
        val text = query.trim()
        val key = "$kind:$genreId:$personId:$text:${language()}:$requestedPage"
        browsePageCache.get(key)?.let { return it }
        if (personId != null) {
            val credits = TmdbParser.personCredits(
                call("/person/$personId/combined_credits", mapOf("language" to language())),
                if (type == ContentType.MOVIE) ContentType.MOVIE else ContentType.SERIES,
                genreId,
                text,
            )
            return TmdbBrowsePage(credits, requestedPage, requestedPage).also {
                browsePageCache.put(key, it, 30 * 60_000L)
            }
        }
        val path = if (text.isBlank()) "/discover/$kind" else "/search/$kind"
        val params = buildMap {
            put("language", language()); put("include_adult", "false")
            if (text.isNotBlank()) put("query", text)
            else {
                put("sort_by", "popularity.desc")
                genreId?.let { put("with_genres", it.toString()) }
            }
        }
        return TmdbParser.browsePage(
            call(path, params + ("page" to requestedPage.toString())), type, genreId, requestedPage,
        ).also { browsePageCache.put(key, it, 30 * 60_000L) }
    }

    @Volatile private var trendingCache: Pair<Long, List<TrendingTitle>>? = null
    private val listCache = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, List<TrendingTitle>>>()

    /** A TMDB list endpoint (e.g. `/trending/movie/week`), merged over [pages]. Cached 30 min. */
    suspend fun list(path: String, type: ContentType, pages: Int = 2, extra: Map<String, String> = emptyMap()): List<TrendingTitle> {
        if (!isEnabled()) return emptyList()
        val key = "$path|$pages|$extra|${language()}"
        listCache[key]?.let { (t, l) -> if (clock() - t < 30 * 60_000L) return l }
        val result = coroutineScope {
            (1..pages).map { p ->
                async { runCatching { TmdbParser.trending(call(path, extra + mapOf("language" to language(), "page" to "$p")), type) }.getOrDefault(emptyList()) }
            }.flatMap { it.await() }
        }.distinctBy { "${it.type}:${it.tmdbId}" }
        if (result.isNotEmpty()) listCache[key] = clock() to result
        return result
    }

    /** Home discovery feed: interleave current releases and weekly movie/TV trends. */
    suspend fun trending(): List<TrendingTitle> {
        if (!isEnabled()) return emptyList()
        trendingCache?.let { (t, list) -> if (clock() - t < 30 * 60_000L && list.isNotEmpty()) return list }
        val lists = coroutineScope {
            listOf(
                async { runCatching { list("/movie/now_playing", ContentType.MOVIE, pages = 2) }.getOrDefault(emptyList()) },
                async { runCatching { list("/trending/movie/week", ContentType.MOVIE, pages = 2) }.getOrDefault(emptyList()) },
                async { runCatching { list("/trending/tv/week", ContentType.SERIES, pages = 2) }.getOrDefault(emptyList()) },
            ).map { it.await() }
        }
        val merged = buildList {
            for (index in 0 until (lists.maxOfOrNull { it.size } ?: 0)) {
                lists.forEach { source -> source.getOrNull(index)?.let(::add) }
            }
        }.distinctBy { "${it.type}:${it.tmdbId}" }
        if (merged.isNotEmpty()) trendingCache = clock() to merged
        return merged
    }


    private suspend fun doEnrich(key: String, titles: List<String>, year: Int?, type: ContentType, imdbId: String?,
                                 countries: List<String>): EnrichmentResult = try {
        val match = when (val cached = matches.get(key)) {
            is MatchCache.Hit -> cached
            MatchCache.Miss -> null
            null -> resolveMatch(titles, year, type, imdbId, countries).also {
                if (it != null) matches.put(key, it, CacheTtl.MATCH_HIT) else matches.put(key, MatchCache.Miss, CacheTtl.MATCH_MISS)
            }
        }
        if (match !is MatchCache.Hit) EnrichmentResult.NotMatched
        else EnrichmentResult.Ready(loadDetails(match.tmdbId, match.type), match.confidence)
    } catch (e: CancellationException) { throw e
    } catch (e: TmdbHttpException) { EnrichmentResult.Failed(FailureClassifier.fromHttp(e.code))
    } catch (e: Exception) { EnrichmentResult.Failed(FailureClassifier.fromThrowable(e)) }

    private suspend fun resolveMatch(titles: List<String>, year: Int?, type: ContentType, imdbId: String?,
                                     countries: List<String>): MatchCache.Hit? {
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
                // Use a year-prioritized search first, then retry without the year only
                // when the provider returns no safe result. This avoids excluding regional
                // release-year variants while leaving identity decisions to ContentMatcher.
                for (withYear in listOf(true, false)) {
                    if (!withYear && year == null) continue
                    val params = buildMap {
                        put("query", title); put("include_adult", "false"); put("language", language())
                        if (withYear && year != null) {
                            if (st == "tv") put("first_air_date_year", year.toString())
                            else put("primary_release_year", year.toString())
                        }
                    }
                    val candidates = TmdbParser.searchCandidates(call("/search/$st", params), ct)
                    (ContentMatcher.match(query, candidates) as? MatchResult.Matched)?.let { return it.toHit() }
                    if (candidates.isEmpty()) continue
                    // Don't broaden a non-empty result set after it was rejected as a remake,
                    // low-confidence candidate, or ambiguous match.
                    break
                }
            }
        }
        val expectedLanguage = expectedOriginalLanguage(countries) ?: return null
        for (st in searchTypes) {
            val ct = if (st == "tv") ContentType.SERIES else ContentType.MOVIE
            val params = buildMap {
                put("query", titles.firstOrNull().orEmpty()); put("include_adult", "false"); put("language", "en-US")
            }
            val candidates = TmdbParser.searchCandidates(call("/search/$st", params), ct)
            val exact = candidates.filter { candidate ->
                val title = candidate.title.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
                title == titles.firstOrNull()?.lowercase()?.replace(Regex("[^\\p{L}\\p{N}]+"), " ")?.trim() &&
                    candidate.originalLanguage == expectedLanguage
            }.distinctBy { it.providerId }
            if (exact.size == 1) {
                val candidate = exact.single()
                return MatchCache.Hit(candidate.providerId, candidate.type, 0.95)
            }
        }
        return null
    }

    private fun expectedOriginalLanguage(countries: List<String>): String? {
        val normalized = countries.joinToString(" ").lowercase()
        return when {
            "korea" in normalized || "한국" in normalized || "کور" in normalized -> "ko"
            else -> null
        }
    }

    private fun matchingKey(catalogKey: String, countries: List<String>) =
        "$catalogKey|v${ContentMatcher.ALGORITHM_VERSION}|${language()}|${expectedOriginalLanguage(countries).orEmpty()}"

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
