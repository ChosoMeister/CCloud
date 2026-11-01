package com.pira.ccloud.core.catalog

import com.pira.ccloud.core.matching.TitleNormalizer
import com.pira.ccloud.core.model.ContentType
import com.pira.ccloud.data.model.Poster
import com.pira.ccloud.data.repository.SearchRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/**
 * Finds a TMDB title in the playable catalog via the catalog search API.
 * Only exact-ish title matches of the same type (and year ±1 when both known) are accepted,
 * so a TMDB item is never shown as available unless the catalog really has it.
 */
object CatalogLookup {
    private val repo = SearchRepository()
    private val cache = ConcurrentHashMap<String, Result>()
    private val permits = Semaphore(4)

    private data class Result(val poster: Poster?)

    suspend fun find(title: String, originalTitle: String?, year: Int?, type: ContentType): Poster? {
        val key = "${type.name}|${TitleNormalizer.normalize(title)}|$year"
        cache[key]?.let { return it.poster }
        val found = try {
            permits.withPermit {
                var hit: Poster? = null
                for (q in listOfNotNull(title, originalTitle?.takeIf { !it.equals(title, true) })) {
                    hit = repo.search(q).posters.firstOrNull { matches(it, title, originalTitle, year, type) }
                    if (hit != null) break
                }
                hit
            }
        } catch (e: CancellationException) { throw e } catch (_: Exception) { return null } // don't cache failures
        cache[key] = Result(found)
        return found
    }

    internal fun matches(p: Poster, title: String, originalTitle: String?, year: Int?, type: ContentType): Boolean {
        val typeOk = if (type == ContentType.MOVIE) p.isMovie() else p.isSeries()
        if (!typeOk) return false
        if (year != null && p.year > 0 && abs(p.year - year) > 1) return false
        val sim = maxOf(TitleNormalizer.similarity(p.title, title), TitleNormalizer.similarity(p.title, originalTitle))
        return sim >= 0.95
    }
}
