package com.pira.ccloud.core.model

import com.pira.ccloud.data.model.FavoriteItem
import com.pira.ccloud.data.model.Movie
import com.pira.ccloud.data.model.Series
import com.pira.ccloud.data.model.Source

/** Lightweight, UI-ready projection of a catalog title used by rails/cards. */
data class CatalogTitle(
    val ref: CatalogRef,
    val title: String,
    val year: Int?,
    val posterUrl: String?,
    val backdropUrl: String?,
    val synopsis: String,
    val genres: List<String>,
    val countries: List<String>,
    val ratings: List<Rating>,
    val availability: Availability,
) {
    val stableKey: String get() = "${ref.type}:${ref.catalogId}"
}

internal fun String?.cleanUrl(): String? = this?.trim()?.takeIf { it.isNotEmpty() && it != "null" }

/** Usable sources are those with a non-blank URL. */
internal fun List<Source>.usable(): List<Source> = filter { it.url.isNotBlank() }

fun availabilityForSources(sources: List<Source>): Availability {
    val usable = sources.usable()
    return if (usable.isEmpty()) Availability.NoStreams
    else Availability.Playable(usable.size, usable.map { it.quality.trim() }.filter { it.isNotEmpty() }.distinct())
}

/** The catalog reports `imdb` and `rating`; we label them exactly as the catalog reports them. */
internal fun catalogRatings(imdb: Double, rating: Double): List<Rating> = buildList {
    val imdbRating = Rating(imdb, 10.0, Rating.Kind.IMDB, DataSource.CATALOG)
    if (imdbRating.isPresent) add(imdbRating)
    val catalogRating = Rating(rating, 10.0, Rating.Kind.CATALOG, DataSource.CATALOG)
    if (catalogRating.isPresent && rating != imdb) add(catalogRating)
}

fun Movie.toCatalogTitle(): CatalogTitle {
    val contentType = ContentType.fromCatalogType(type)
    return CatalogTitle(
        ref = CatalogRef(id, contentType),
        title = title,
        year = year.takeIf { it in 1870..2100 },
        posterUrl = image.cleanUrl(),
        backdropUrl = cover.cleanUrl(),
        synopsis = description,
        genres = genres.map { it.title },
        countries = country.map { it.title },
        ratings = catalogRatings(imdb, rating),
        availability = if (contentType == ContentType.MOVIE) availabilityForSources(sources)
        else Availability.EpisodesOnDemand,
    )
}

fun Series.toCatalogTitle(): CatalogTitle = CatalogTitle(
    ref = CatalogRef(id, ContentType.fromCatalogType(type).let { if (it == ContentType.MOVIE) ContentType.SERIES else it }),
    title = title,
    year = year.takeIf { it in 1870..2100 },
    posterUrl = image.cleanUrl(),
    backdropUrl = cover.cleanUrl(),
    synopsis = description,
    genres = genres.map { it.title },
    countries = country.map { it.title },
    ratings = catalogRatings(imdb, rating),
    availability = Availability.EpisodesOnDemand,
)

/** Existing favorites (favorites.json) map onto the same projection, so no migration is needed. */
fun FavoriteItem.toCatalogTitle(): CatalogTitle {
    val contentType = ContentType.fromCatalogType(type)
    return CatalogTitle(
        ref = CatalogRef(id, contentType),
        title = title,
        year = year.takeIf { it in 1870..2100 },
        posterUrl = image.cleanUrl(),
        backdropUrl = cover.cleanUrl(),
        synopsis = description,
        genres = genres.map { it.title },
        countries = country.map { it.title },
        ratings = catalogRatings(imdb, rating),
        availability = if (contentType == ContentType.MOVIE) availabilityForSources(sources)
        else Availability.EpisodesOnDemand,
    )
}

fun CatalogTitle.toIdentity(): ContentIdentity = ContentIdentity(
    catalog = ref,
    type = ref.type,
    title = title,
    year = year,
    provenance = DataSource.CATALOG,
)
