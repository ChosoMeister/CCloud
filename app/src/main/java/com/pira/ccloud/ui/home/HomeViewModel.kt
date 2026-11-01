package com.pira.ccloud.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pira.ccloud.core.home.ContinueItem
import com.pira.ccloud.core.home.HomeAssembler
import com.pira.ccloud.core.home.HomeContent
import com.pira.ccloud.core.home.RailKind
import com.pira.ccloud.core.home.RailResult
import com.pira.ccloud.core.model.LoadState
import com.pira.ccloud.core.model.toCatalogTitle
import com.pira.ccloud.core.network.FailureClassifier
import com.pira.ccloud.core.playback.ProgressStore
import com.pira.ccloud.core.playback.SharedPrefsKeyValueStore
import com.pira.ccloud.data.model.FilterType
import com.pira.ccloud.data.model.Movie
import com.pira.ccloud.data.model.Series
import com.pira.ccloud.data.repository.MovieRepository
import com.pira.ccloud.data.repository.SeriesRepository
import com.pira.ccloud.utils.StorageUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import com.pira.ccloud.core.catalog.CatalogLookup
import com.pira.ccloud.core.model.CatalogTitle
import com.pira.ccloud.core.model.ContentType
import com.pira.ccloud.core.tmdb.TmdbProvider
import com.pira.ccloud.core.tmdb.TrendingTitle

/** A discovery slide: a playable catalog title with TMDB art/overview. */
data class SpotlightItem(val title: CatalogTitle, val tmdb: TrendingTitle)

/**
 * Cinematic Home: built only from the existing catalog API (4 parallel first-page requests)
 * plus local data (progress, favorites). No TMDB dependency.
 */
class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val movieRepo = MovieRepository()
    private val seriesRepo = SeriesRepository()
    private val progress = ProgressStore(SharedPrefsKeyValueStore(app))

    private val _state = MutableStateFlow<LoadState<HomeContent>>(LoadState.Loading)
    val state: StateFlow<LoadState<HomeContent>> = _state.asStateFlow()

    /** Raw catalog objects so details screens (which read cached files) can be opened. */
    private val movies = mutableMapOf<Int, Movie>()
    private val series = mutableMapOf<Int, Series>()
    private var job: Job? = null
    private var catalogCache: Map<RailKind, RailResult> = emptyMap()

    private val _spotlight = MutableStateFlow<List<SpotlightItem>>(emptyList())
    /** Diverse TMDB discovery titles that are actually available in the catalog. */
    val spotlight: StateFlow<List<SpotlightItem>> = _spotlight.asStateFlow()
    private var spotlightJob: Job? = null

    private val _tmdbRails = MutableStateFlow<List<com.pira.ccloud.core.home.HomeRail>>(emptyList())
    /** Netflix-style rails curated from TMDB lists, containing only titles available in the catalog. */
    val tmdbRails: StateFlow<List<com.pira.ccloud.core.home.HomeRail>> = _tmdbRails.asStateFlow()

    private data class RailSpec(val title: String, val path: String, val type: ContentType, val pages: Int = 3)
    private val railSpecs = listOf(
        RailSpec("Trending Movies", "/trending/movie/week", ContentType.MOVIE),
        RailSpec("Trending Series", "/trending/tv/week", ContentType.SERIES),
        RailSpec("New Releases", "/movie/now_playing", ContentType.MOVIE),
        RailSpec("Popular Movies", "/movie/popular", ContentType.MOVIE),
        RailSpec("Popular Series", "/tv/popular", ContentType.SERIES),
        RailSpec("New Episodes This Week", "/tv/on_the_air", ContentType.SERIES, 2),
        RailSpec("Top Rated Movies", "/movie/top_rated", ContentType.MOVIE),
        RailSpec("Top Rated Series", "/tv/top_rated", ContentType.SERIES),
    )

    init { refresh(); loadSpotlight() }

    /** Resolves a wider candidate pool, then balances content type and catalog genres. */
    private suspend fun resolve(list: List<TrendingTitle>, limit: Int, maxCandidates: Int = 60): List<SpotlightItem> {
        val matched = mutableListOf<SpotlightItem>()
        for (batch in list.take(maxCandidates).chunked(8)) {
            val found = supervisorScope {
                batch.map { t -> async { t to CatalogLookup.find(t.title, t.originalTitle, t.year, t.type) } }.map { it.await() }
            }
            for ((t, poster) in found) {
                if (poster == null) continue
                val base = if (t.type == ContentType.MOVIE) {
                    val m = poster.toMovie(); synchronized(movies) { movies[m.id] = m }; m.toCatalogTitle()
                } else {
                    val s = poster.toSeries(); synchronized(series) { series[s.id] = s }; s.toCatalogTitle()
                }
                // Sharper TMDB card art; playback/details still come from the catalog entry.
                val title = base.copy(posterUrl = t.posterUrl?.replace("/w780/", "/w342/") ?: base.posterUrl,
                    backdropUrl = t.backdropUrl ?: base.backdropUrl)
                if (matched.none { it.title.stableKey == title.stableKey }) matched += SpotlightItem(title, t)
            }
            if (matched.size >= limit * 3) break
        }
        return diversify(matched, limit)
    }

    private fun diversify(items: List<SpotlightItem>, limit: Int): List<SpotlightItem> {
        val remaining = items.distinctBy { it.title.stableKey }.toMutableList()
        val selected = mutableListOf<SpotlightItem>()
        val genreCounts = mutableMapOf<String, Int>()
        val typeCounts = mutableMapOf<ContentType, Int>()
        while (selected.size < limit && remaining.isNotEmpty()) {
            val lastType = selected.lastOrNull()?.title?.ref?.type
            val next = remaining.minByOrNull { item ->
                val type = item.title.ref.type
                val repeatedType = if (type == lastType) 1.0 else 0.0
                val repeatedGenres = item.title.genres.sumOf { (genreCounts[it] ?: 0).toDouble() }
                val typeImbalance = (typeCounts[type] ?: 0).toDouble()
                val rating = item.tmdb.rating?.value
                val votes = item.tmdb.voteCount.toDouble()
                val confidenceAdjustedRating = rating?.let { (it * votes + 6.0 * 120.0) / (votes + 120.0) } ?: 6.0
                val lowRatingPenalty = (7.0 - confidenceAdjustedRating).coerceAtLeast(0.0)
                val sourceOrder = items.indexOf(item).toDouble() * 0.03
                repeatedType * 4.0 + repeatedGenres * 0.8 + typeImbalance * 0.5 + lowRatingPenalty * 0.35 + sourceOrder
            } ?: break
            remaining.remove(next)
            selected += next
            typeCounts[next.title.ref.type] = (typeCounts[next.title.ref.type] ?: 0) + 1
            next.title.genres.forEach { genreCounts[it] = (genreCounts[it] ?: 0) + 1 }
        }
        return selected
    }

    fun loadSpotlight() {
        spotlightJob?.cancel()
        spotlightJob = viewModelScope.launch {
            val repo = TmdbProvider.repository(getApplication())
            val trending = try { repo.trending() }
                catch (e: CancellationException) { throw e } catch (_: Exception) { emptyList() }
            _spotlight.value = resolve(trending, limit = 20, maxCandidates = 120)
            val rails = mutableListOf<com.pira.ccloud.core.home.HomeRail>()
            for (spec in railSpecs) {
                val list = try { repo.list(spec.path, spec.type, spec.pages) }
                    catch (e: CancellationException) { throw e } catch (_: Exception) { emptyList() }
                val items = resolve(list, 20).map { it.title }
                if (items.size >= 3) rails += com.pira.ccloud.core.home.HomeRail(RailKind.TMDB, items, customTitle = spec.title)
                // Publish rarely so the screen doesn't keep re-laying out (visible flicker on TV).
                if (rails.size == 2) _tmdbRails.value = rails.toList()
            }
            _tmdbRails.value = rails.toList()
        }
    }

    fun refresh() {
        job?.cancel()
        job = viewModelScope.launch {
            if (_state.value !is LoadState.Ready && _state.value !is LoadState.Partial) _state.value = LoadState.Loading
            catalogCache = supervisorScope {
                val newMovies = async { loadMovies(FilterType.DEFAULT) }
                val topMovies = async { loadMovies(FilterType.BY_IMDB) }
                val newSeries = async { loadSeries(FilterType.DEFAULT) }
                val topSeries = async { loadSeries(FilterType.BY_IMDB) }
                mapOf(
                    RailKind.NEW_MOVIES to newMovies.await(), RailKind.TOP_IMDB_MOVIES to topMovies.await(),
                    RailKind.NEW_SERIES to newSeries.await(), RailKind.TOP_IMDB_SERIES to topSeries.await(),
                )
            }
            publish()
        }
    }

    /** Re-reads local rails (after returning from the player/details) without network. */
    fun refreshLocal() {
        if (catalogCache.isEmpty()) return
        viewModelScope.launch { publish() }
    }

    private suspend fun publish() {
        val (cont, myList) = withContext(Dispatchers.IO) { loadContinueWatching() to loadMyList() }
        val content = HomeAssembler.assemble(catalogCache, cont, myList)
        _state.value = when {
            content.rails.isEmpty() && content.failedRails.isNotEmpty() -> LoadState.Failed(content.failedRails.values.first())
            content.rails.isEmpty() -> LoadState.Empty
            content.failedRails.isNotEmpty() -> LoadState.Partial(content, content.failedRails.values.first())
            else -> LoadState.Ready(content)
        }
    }

    private suspend fun loadMovies(filter: FilterType): RailResult = try {
        val list = movieRepo.getMovies(page = 0, filterType = filter)
        synchronized(movies) { list.forEach { movies[it.id] = it } }
        RailResult.Ok(list.map { it.toCatalogTitle() })
    } catch (e: CancellationException) { throw e } catch (e: Exception) { RailResult.Err(FailureClassifier.fromThrowable(e)) }

    private suspend fun loadSeries(filter: FilterType): RailResult = try {
        val list = seriesRepo.getSeries(page = 0, filterType = filter)
        synchronized(series) { list.forEach { series[it.id] = it } }
        RailResult.Ok(list.map { it.toCatalogTitle() })
    } catch (e: CancellationException) { throw e } catch (e: Exception) { RailResult.Err(FailureClassifier.fromThrowable(e)) }

    private fun loadContinueWatching(): List<ContinueItem> {
        val ctx = getApplication<Application>()
        return progress.recentByTitle(20).mapNotNull { entry ->
            val title = if (entry.episodeId != null) {
                (series[entry.catalogId] ?: StorageUtils.loadSeriesFromFile(ctx, entry.catalogId))?.toCatalogTitle()
            } else {
                (movies[entry.catalogId] ?: StorageUtils.loadMovieFromFile(ctx, entry.catalogId))?.toCatalogTitle()
            }
            title?.let { ContinueItem(it, entry) }
        }
    }

    private fun loadMyList() = try {
        StorageUtils.loadAllFavorites(getApplication()).map { it.toCatalogTitle() }
    } catch (_: Exception) { emptyList() }

    fun movie(id: Int): Movie? = movies[id] ?: StorageUtils.loadMovieFromFile(getApplication(), id)
    fun series(id: Int): Series? = series[id] ?: StorageUtils.loadSeriesFromFile(getApplication(), id)
}
