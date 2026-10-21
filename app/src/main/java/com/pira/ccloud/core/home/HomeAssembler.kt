package com.pira.ccloud.core.home

import com.pira.ccloud.core.model.Availability
import com.pira.ccloud.core.model.CatalogTitle
import com.pira.ccloud.core.network.FailureKind
import com.pira.ccloud.core.playback.ProgressEntry

/** Rails the Home screen can show. Sorting semantics are server-side for the whole catalog. */
enum class RailKind { CONTINUE_WATCHING, NEW_MOVIES, TOP_IMDB_MOVIES, NEW_SERIES, TOP_IMDB_SERIES, MY_LIST, TMDB }

data class ContinueItem(val title: CatalogTitle, val progress: ProgressEntry)

data class HomeRail(val kind: RailKind, val items: List<CatalogTitle>, val continueItems: List<ContinueItem> = emptyList(),
                    /** Display title for TMDB-curated rails (only titles available in the catalog). */
                    val customTitle: String? = null) {
    val isEmpty: Boolean get() = items.isEmpty() && continueItems.isEmpty()
    val key: String get() = customTitle?.let { "tmdb:$it" } ?: kind.name
}

data class HomeContent(
    val hero: CatalogTitle?,
    val rails: List<HomeRail>,
    /** Rails whose source failed; used for a non-blocking "partially loaded" notice. */
    val failedRails: Map<RailKind, FailureKind>,
)

/** Outcome of one catalog request. */
sealed interface RailResult {
    data class Ok(val items: List<CatalogTitle>) : RailResult
    data class Err(val failure: FailureKind) : RailResult
}

/**
 * Pure assembly of Home: dedupes within each rail, omits empty rails (no filler),
 * and picks a hero that has a backdrop and is actually playable or browsable.
 */
object HomeAssembler {
    const val MAX_RAIL_ITEMS = 20

    fun assemble(
        sources: Map<RailKind, RailResult>,
        continueWatching: List<ContinueItem>,
        myList: List<CatalogTitle>,
    ): HomeContent {
        val failed = sources.mapNotNull { (k, v) -> (v as? RailResult.Err)?.let { k to it.failure } }.toMap()
        fun items(kind: RailKind) = (sources[kind] as? RailResult.Ok)?.items.orEmpty()
            .distinctBy { it.stableKey }.take(MAX_RAIL_ITEMS)

        val rails = buildList {
            add(HomeRail(RailKind.CONTINUE_WATCHING, emptyList(), continueWatching.distinctBy { it.title.stableKey }))
            listOf(RailKind.NEW_MOVIES, RailKind.TOP_IMDB_MOVIES, RailKind.NEW_SERIES, RailKind.TOP_IMDB_SERIES)
                .forEach { add(HomeRail(it, items(it))) }
            add(HomeRail(RailKind.MY_LIST, myList.distinctBy { it.stableKey }.take(MAX_RAIL_ITEMS)))
        }.filterNot { it.isEmpty }

        return HomeContent(hero = pickHero(items(RailKind.NEW_MOVIES) + items(RailKind.NEW_SERIES)), rails = rails, failedRails = failed)
    }

    fun pickHero(candidates: List<CatalogTitle>): CatalogTitle? =
        candidates.firstOrNull { it.backdropUrl != null && it.synopsis.isNotBlank() && it.availability.isBrowsable() }
            ?: candidates.firstOrNull { it.backdropUrl != null && it.availability.isBrowsable() }

    private fun Availability.isBrowsable() = isPlayable || this == Availability.EpisodesOnDemand
}
