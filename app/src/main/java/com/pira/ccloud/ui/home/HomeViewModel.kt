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

    init { refresh() }

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
