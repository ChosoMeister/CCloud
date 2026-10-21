package com.pira.ccloud.core.tmdb

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Raw TMDB v3 response shapes (only fields we use). Unknown fields ignored. */
@Serializable
internal data class TmdbSearchResponse(
    val results: List<TmdbSearchItem> = emptyList(),
    @SerialName("total_pages") val totalPages: Int = 1,
)

@Serializable
internal data class TmdbPersonCreditsResponse(val cast: List<TmdbSearchItem> = emptyList())

@Serializable
internal data class TmdbSearchItem(
    val id: Int,
    val title: String? = null,
    val name: String? = null,
    @SerialName("original_title") val originalTitle: String? = null,
    @SerialName("original_name") val originalName: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
    @SerialName("media_type") val mediaType: String? = null,
    @SerialName("original_language") val originalLanguage: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("vote_average") val voteAverage: Double? = null,
    @SerialName("vote_count") val voteCount: Int? = null,
    val overview: String? = null,
    @SerialName("genre_ids") val genreIds: List<Int> = emptyList(),
)

@Serializable
internal data class TmdbFindResponse(
    @SerialName("movie_results") val movieResults: List<TmdbSearchItem> = emptyList(),
    @SerialName("tv_results") val tvResults: List<TmdbSearchItem> = emptyList(),
)

@Serializable
internal data class TmdbDetailsResponse(
    val id: Int,
    val title: String? = null,
    val name: String? = null,
    val overview: String? = null,
    val tagline: String? = null,
    val runtime: Int? = null,
    @SerialName("episode_run_time") val episodeRunTime: List<Int> = emptyList(),
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("vote_average") val voteAverage: Double? = null,
    @SerialName("vote_count") val voteCount: Int? = null,
    @SerialName("imdb_id") val imdbId: String? = null,
    val genres: List<TmdbNamed> = emptyList(),
    val credits: TmdbCredits? = null,
    val videos: TmdbVideos? = null,
    val reviews: TmdbReviews? = null,
    val recommendations: TmdbSearchResponse? = null,
    @SerialName("external_ids") val externalIds: TmdbExternalIds? = null,
)

@Serializable internal data class TmdbNamed(val id: Int = 0, val name: String = "")
@Serializable internal data class TmdbExternalIds(@SerialName("imdb_id") val imdbId: String? = null)
@Serializable internal data class TmdbCredits(val cast: List<TmdbCast> = emptyList(), val crew: List<TmdbCrew> = emptyList())
@Serializable internal data class TmdbCast(val id: Int, val name: String = "", val character: String? = null,
    @SerialName("profile_path") val profilePath: String? = null, val order: Int = 999)
@Serializable internal data class TmdbCrew(val id: Int, val name: String = "", val job: String? = null,
    @SerialName("profile_path") val profilePath: String? = null)
@Serializable internal data class TmdbVideos(val results: List<TmdbVideo> = emptyList())
@Serializable internal data class TmdbVideo(val key: String = "", val site: String = "", val type: String = "",
    val official: Boolean = false, val name: String = "")
@Serializable internal data class TmdbReviews(val results: List<TmdbReview> = emptyList(),
    @SerialName("total_results") val totalResults: Int = 0)
@Serializable internal data class TmdbReview(val id: String = "", val author: String = "", val content: String = "",
    @SerialName("created_at") val createdAt: String? = null, val url: String? = null,
    @SerialName("author_details") val authorDetails: TmdbAuthorDetails? = null)
@Serializable internal data class TmdbAuthorDetails(val rating: Double? = null)

internal val tmdbJson = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true; explicitNulls = false }

@Serializable
data class TmdbGenre(val id: Int, val name: String)

@Serializable
internal data class TmdbGenreResponse(val genres: List<TmdbGenre> = emptyList())
