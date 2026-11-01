package com.pira.ccloud.core.model

/**
 * Provider-neutral content domain for the cinematic UI.
 *
 * Key rule: catalog/playback availability (the CCloud streaming server) is kept strictly
 * separate from enrichment metadata (e.g. TMDB). A [ContentIdentity] originating from a
 * metadata provider is never playable by itself; only [CatalogRef]s can carry playable sources.
 */
enum class ContentType { MOVIE, SERIES, ANIME;

    companion object {
        /** Maps the catalog server's `type` field ("movie", "serie", "series", ...). */
        fun fromCatalogType(raw: String?): ContentType = when (raw?.trim()?.lowercase()) {
            "serie", "series", "tv", "show" -> SERIES
            "anime" -> ANIME
            else -> MOVIE
        }
    }
}

/** Where a piece of data came from. Shown to users for ratings and used for attribution. */
enum class DataSource(val displayName: String) {
    /** The existing CCloud catalog/streaming API (server-hi-speed-iran.info + helper hosts). */
    CATALOG("CCloud"),
    TMDB("TMDB"),
    LOCAL("Local"),
}

/** Reference to an item in the streaming catalog (the only source of playable items). */
data class CatalogRef(val catalogId: Int, val type: ContentType)

/**
 * A rating value always carries its scale and true origin. IMDb scores must only be labeled
 * IMDb when they are actually reported as IMDb by [reportedBy]; TMDB vote averages are TMDB.
 */
data class Rating(
    val value: Double,
    val scale: Double,
    val kind: Kind,
    val reportedBy: DataSource,
) {
    enum class Kind(val label: String) { IMDB("IMDb"), TMDB("TMDB"), CATALOG("Rating") }

    val isPresent: Boolean get() = value > 0.0 && value <= scale

    /** Single-decimal label, e.g. "7.8". Locale-independent; localize digits at the UI layer. */
    fun formatted(): String = String.format(java.util.Locale.US, "%.1f", value)
}

/**
 * Availability of a title for playback. UI must map each state to an explicit, friendly
 * component and must never route [isPlayable] == false items into the player.
 */
sealed interface Availability {
    val isPlayable: Boolean get() = false

    data class Playable(val sourceCount: Int, val qualities: List<String>) : Availability {
        override val isPlayable: Boolean get() = true
    }
    /** Series: playability decided per episode after seasons are loaded. */
    data object EpisodesOnDemand : Availability
    data object NoStreams : Availability
    data object MetadataOnly : Availability
    data object SubscriptionRequired : Availability
    data object TemporarilyUnavailable : Availability
}

/**
 * Canonical cross-provider identity. Created by the matcher; carries provenance so mappings
 * can be invalidated when the matching algorithm changes.
 */
data class ContentIdentity(
    val catalog: CatalogRef?,
    val type: ContentType,
    val title: String,
    val year: Int?,
    val tmdbId: Int? = null,
    val imdbId: String? = null,
    val alternateTitles: List<String> = emptyList(),
    val originalLanguage: String? = null,
    val matchConfidence: Double? = null,
    val provenance: DataSource,
    val mappingVersion: Int? = null,
) {
    val isCatalogBacked: Boolean get() = catalog != null
}
