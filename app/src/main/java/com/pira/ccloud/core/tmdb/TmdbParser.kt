package com.pira.ccloud.core.tmdb

import com.pira.ccloud.core.matching.MatchCandidate
import com.pira.ccloud.core.model.ContentType
import com.pira.ccloud.core.model.DataSource
import com.pira.ccloud.core.model.Rating

/** Enrichment only. Nothing here is playable; availability always comes from the catalog. */
data class Enrichment(
    val tmdbId: Int,
    val type: ContentType,
    val title: String,
    val overview: String?,
    val tagline: String?,
    val runtimeMinutes: Int?,
    val year: Int?,
    val backdropUrl: String?,
    val posterUrl: String?,
    val rating: Rating?,
    val imdbId: String?,
    val genres: List<String>,
    val cast: List<Person>,
    val directors: List<Person>,
    val trailer: Trailer?,
    val reviews: List<Review>,
    val totalReviews: Int,
    val recommendations: List<MetadataTitle>,
) {
    val source: DataSource get() = DataSource.TMDB
}

data class Person(val id: Int, val name: String, val role: String?, val imageUrl: String?)
data class Trailer(val youTubeKey: String, val name: String) {
    val url: String get() = "https://www.youtube.com/watch?v=$youTubeKey"
}
data class Review(val id: String, val author: String, val content: String, val createdAt: String?,
                  val authorRating: Double?, val url: String?)

/** A TMDB title (e.g. a recommendation). Metadata-only until matched to the catalog. */
data class MetadataTitle(val tmdbId: Int, val type: ContentType, val title: String, val year: Int?,
                         val posterUrl: String?, val rating: Rating?)

/** A TMDB trending title with full art. Metadata-only until matched to the catalog. */
data class TrendingTitle(val tmdbId: Int, val type: ContentType, val title: String, val originalTitle: String?,
                         val year: Int?, val overview: String?, val backdropUrl: String?, val posterUrl: String?,
                         val rating: Rating?)

object TmdbImages {
    private const val BASE = "https://image.tmdb.org/t/p/"
    /** Sized URLs; never request "original" for cards. */
    fun poster(path: String?, size: String = "w342") = path?.takeIf { it.startsWith("/") }?.let { BASE + size + it }
    fun backdrop(path: String?, size: String = "w1280") = path?.takeIf { it.startsWith("/") }?.let { BASE + size + it }
    fun profile(path: String?, size: String = "w185") = path?.takeIf { it.startsWith("/") }?.let { BASE + size + it }
}

/** Pure parsing from TMDB JSON into domain types. Throws SerializationException on invalid JSON. */
object TmdbParser {
    private fun yearOf(date: String?): Int? = date?.take(4)?.toIntOrNull()?.takeIf { it in 1870..2100 }

    private fun ratingOf(avg: Double?, count: Int?): Rating? {
        if (avg == null || avg <= 0.0 || (count ?: 0) < 10) return null // too few votes to be meaningful
        return Rating(avg, 10.0, Rating.Kind.TMDB, DataSource.TMDB)
    }

    internal fun candidate(item: TmdbSearchItem, defaultType: ContentType): MatchCandidate {
        val type = when (item.mediaType) { "tv" -> ContentType.SERIES; "movie" -> ContentType.MOVIE; else -> defaultType }
        return MatchCandidate(
            providerId = item.id, type = type,
            title = item.title ?: item.name ?: "",
            originalTitle = item.originalTitle ?: item.originalName,
            year = yearOf(item.releaseDate ?: item.firstAirDate),
        )
    }

    fun searchCandidates(json: String, type: ContentType): List<MatchCandidate> =
        tmdbJson.decodeFromString<TmdbSearchResponse>(json).results
            .filter { it.mediaType != "person" }
            .map { candidate(it, type) }

    /** `/trending/...` or `/movie/now_playing` lists. Items without a backdrop are dropped. */
    fun trending(json: String, defaultType: ContentType): List<TrendingTitle> =
        tmdbJson.decodeFromString<TmdbSearchResponse>(json).results
            .filter { it.mediaType != "person" && it.backdropPath != null }
            .map {
                val c = candidate(it, defaultType)
                TrendingTitle(it.id, c.type, c.title, c.originalTitle, c.year, it.overview,
                    TmdbImages.backdrop(it.backdropPath, "original"), TmdbImages.poster(it.posterPath, "w780"),
                    ratingOf(it.voteAverage, it.voteCount))
            }

    /** `/find/{imdb}`: candidates carry the IMDb id so the matcher can accept them by external id. */
    fun findCandidates(json: String, imdbId: String): List<MatchCandidate> {
        val r = tmdbJson.decodeFromString<TmdbFindResponse>(json)
        return r.movieResults.map { candidate(it, ContentType.MOVIE).copy(imdbId = imdbId) } +
            r.tvResults.map { candidate(it, ContentType.SERIES).copy(imdbId = imdbId) }
    }

    fun details(json: String, type: ContentType, maxCast: Int = 20, maxReviews: Int = 10): Enrichment {
        val d = tmdbJson.decodeFromString<TmdbDetailsResponse>(json)
        val trailer = d.videos?.results.orEmpty()
            .filter { it.site.equals("YouTube", true) && it.key.isNotBlank() && it.type in setOf("Trailer", "Teaser") }
            .sortedWith(compareByDescending<TmdbVideo> { it.type == "Trailer" }.thenByDescending { it.official })
            .firstOrNull()?.let { Trailer(it.key, it.name) }
        return Enrichment(
            tmdbId = d.id, type = type,
            title = d.title ?: d.name ?: "",
            overview = d.overview?.takeIf { it.isNotBlank() },
            tagline = d.tagline?.takeIf { it.isNotBlank() },
            runtimeMinutes = d.runtime?.takeIf { it > 0 } ?: d.episodeRunTime.firstOrNull { it > 0 },
            year = yearOf(d.releaseDate ?: d.firstAirDate),
            backdropUrl = TmdbImages.backdrop(d.backdropPath),
            posterUrl = TmdbImages.poster(d.posterPath),
            rating = ratingOf(d.voteAverage, d.voteCount),
            imdbId = (d.imdbId ?: d.externalIds?.imdbId)?.takeIf { it.startsWith("tt") },
            genres = d.genres.map { it.name }.filter { it.isNotBlank() },
            cast = d.credits?.cast.orEmpty().sortedBy { it.order }.take(maxCast)
                .map { Person(it.id, it.name, it.character?.takeIf { c -> c.isNotBlank() }, TmdbImages.profile(it.profilePath)) },
            directors = d.credits?.crew.orEmpty().filter { it.job == "Director" || it.job == "Creator" }.distinctBy { it.id }
                .map { Person(it.id, it.name, it.job, TmdbImages.profile(it.profilePath)) },
            trailer = trailer,
            reviews = d.reviews?.results.orEmpty().filter { it.content.isNotBlank() }.take(maxReviews)
                .map { Review(it.id, it.author.ifBlank { "Anonymous" }, it.content.trim(), it.createdAt, it.authorDetails?.rating, it.url) },
            totalReviews = d.reviews?.totalResults ?: 0,
            recommendations = d.recommendations?.results.orEmpty().map {
                val c = candidate(it, if (type == ContentType.MOVIE) ContentType.MOVIE else ContentType.SERIES)
                MetadataTitle(it.id, c.type, c.title, c.year, TmdbImages.poster(it.posterPath, "w185"), ratingOf(it.voteAverage, it.voteCount))
            },
        )
    }
}
