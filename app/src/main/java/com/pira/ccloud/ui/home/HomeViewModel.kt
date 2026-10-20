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

/** A spotlight slide: a playable catalog title with TMDB art/overview. */
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
    /** Top-10 TMDB box-office / trending titles that are actually available in the catalog. */
    val spotlight: StateFlow<List<SpotlightItem>> = _spotlight.asStateFlow()
    private var spotlightJob: Job? = null

    init { refresh(); loadSpotlight() }

    fun loadSpotlight() {
        spotlightJob?.cancel()
        spotlightJob = viewModelScope.launch {
            val trending = try { TmdbProvider.repository(getApplication()).trending() }
                catch (e: CancellationException) { throw e } catch (_: Exception) { emptyList() }
            val result = mutableListOf<SpotlightItem>()
            // Resolve in small ordered batches so the top of the chart wins and we stop at 10.
            for (batch in trending.take(60).chunked(8)) {
                val found = supervisorScope {
                    batch.map { t -> async { t to CatalogLookup.find(t.title, t.originalTitle, t.year, t.type) } }.map { it.await() }
                }
                for ((t, poster) in found) {
                    if (poster == null || result.size >= 10) continue
                    val title = if (t.type == ContentType.MOVIE) {
                        val m = poster.toMovie(); synchronized(movies) { movies[m.id] = m }; m.toCatalogTitle()
                    } else {
                        val s = poster.toSeries(); synchronized(series) { series[s.id] = s }; s.toCatalogTitle()
                    }
                    if (result.none { it.title.stableKey == title.stableKey }) result += SpotlightItem(title, t)
                }
                if (result.size >= 10) break
            }
            _spotlight.value = result.toList()
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
