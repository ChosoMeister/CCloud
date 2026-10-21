package com.pira.ccloud.ui.search

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pira.ccloud.core.catalog.CatalogLookup
import com.pira.ccloud.core.model.ContentType
import com.pira.ccloud.core.tmdb.TmdbGenre
import com.pira.ccloud.core.tmdb.TmdbProvider
import com.pira.ccloud.data.model.Poster
import com.pira.ccloud.data.repository.SearchRepository
import com.pira.ccloud.utils.LanguageUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

class SearchViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SearchRepository()
    private val tmdb = TmdbProvider.repository(application)
    var searchResults by mutableStateOf<List<Poster>>(emptyList()); private set
    var isLoading by mutableStateOf(false); private set
    var isLoadingMore by mutableStateOf(false); private set
    var canLoadMore by mutableStateOf(false); private set
    var errorMessage by mutableStateOf<String?>(null); private set
    var searchQuery by mutableStateOf(""); private set
    var hasSearched by mutableStateOf(false); private set
    var mediaType by mutableStateOf<ContentType?>(null); private set
    var genres by mutableStateOf<List<TmdbGenre>>(emptyList()); private set
    var selectedGenre by mutableStateOf<TmdbGenre?>(null); private set
    var selectedPerson by mutableStateOf<TmdbPersonFilter?>(null); private set
    var isGenresLoading by mutableStateOf(false); private set
    var genreError by mutableStateOf<String?>(null); private set
    private var searchJob: Job? = null
    private var genreJob: Job? = null
    private var requestGeneration = 0
    private var genreGeneration = 0
    private var nextBrowsePage = 1
    private var browseTotalPages: Int? = null
    private var activeType: ContentType? = null
    private var activeGenre: TmdbGenre? = null
    private var activePerson: TmdbPersonFilter? = null
    private var activeQuery = ""

    fun updateSearchQuery(query: String) {
        searchQuery = query
        requestGeneration++; searchJob?.cancel()
        isLoading = false; isLoadingMore = false; canLoadMore = false; hasSearched = false; errorMessage = null
        searchResults = emptyList()
    }

    fun selectMedia(type: ContentType?) {
        if (type == mediaType && genreError == null) return
        mediaType = type; selectedGenre = null; selectedPerson = null; genres = emptyList(); genreError = null
        val genreRequest = ++genreGeneration
        genreJob?.cancel()
        isGenresLoading = type != null
        if (type != null) genreJob = viewModelScope.launch {
            try {
                if (!TmdbProvider.isEnabled(getApplication())) error("Configure TMDB in Settings to browse genres.")
                genres = tmdb.genres(type)
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) { genreError = "TMDB genres could not be loaded. Retry by selecting the type again." }
            finally { if (genreRequest == genreGeneration) isGenresLoading = false }
        }
        if (searchQuery.isNotBlank() || type != null) triggerSearch() else clearSearch()
    }

    fun selectGenre(genre: TmdbGenre?) {
        selectedGenre = genre
        triggerSearch()
    }

    fun selectPerson(person: TmdbPersonFilter?) {
        selectedPerson = person
        triggerSearch()
    }

    fun applyMetadataFilter(type: ContentType, genre: TmdbGenre? = null, person: TmdbPersonFilter? = null) {
        mediaType = if (type == ContentType.MOVIE) ContentType.MOVIE else ContentType.SERIES
        selectedGenre = genre
        selectedPerson = person
        searchQuery = ""
        errorMessage = null
        val genreRequest = ++genreGeneration
        genreJob?.cancel()
        genres = emptyList()
        genreError = null
        isGenresLoading = true
        val selectedType = mediaType!!
        genreJob = viewModelScope.launch {
            try {
                if (!TmdbProvider.isEnabled(getApplication())) error("Configure TMDB in Settings to browse genres.")
                genres = tmdb.genres(selectedType)
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) { genreError = "TMDB genres could not be loaded. Retry by selecting the type again." }
            finally { if (genreRequest == genreGeneration) isGenresLoading = false }
        }
        triggerSearch()
    }

    fun triggerSearch() {
        val query = searchQuery.trim()
        val type = mediaType
        val genre = selectedGenre
        val person = selectedPerson
        if (query.isBlank() && type == null) return
        activeType = type
        activeGenre = genre
        activePerson = person
        activeQuery = query
        nextBrowsePage = 1
        browseTotalPages = null
        val generation = ++requestGeneration
        searchJob?.cancel()
        hasSearched = true; isLoading = true; isLoadingMore = false; canLoadMore = false
        errorMessage = null; searchResults = emptyList()
        searchJob = viewModelScope.launch {
            try {
                val results = if (query.isNotBlank() && genre == null && person == null) {
                    repository.search(query).posters.filter { p ->
                        type == null || if (type == ContentType.MOVIE) p.isMovie() else p.isSeries()
                    }
                } else {
                    if (!TmdbProvider.isEnabled(getApplication())) error("Configure TMDB in Settings to browse genres.")
                    loadMetadataPageResults(generation, append = false)
                    emptyList()
                }
                if (generation == requestGeneration && results.isNotEmpty()) searchResults = visible(results)
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                if (generation == requestGeneration) { errorMessage = e.message ?: "Search failed"; searchResults = emptyList() }
            } finally { if (generation == requestGeneration) { isLoading = false; isLoadingMore = false } }
        }
    }

    fun loadMore() {
        if (isLoading || isLoadingMore || !canLoadMore) return
        val generation = requestGeneration
        isLoadingMore = true
        searchJob = viewModelScope.launch {
            try {
                loadMetadataPageResults(generation, append = true)
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                if (generation == requestGeneration) errorMessage = e.message ?: "Could not load more titles"
            } finally { if (generation == requestGeneration) isLoadingMore = false }
        }
    }

    private suspend fun loadMetadataPageResults(generation: Int, append: Boolean) {
        val type = activeType ?: ContentType.MOVIE
        val genre = activeGenre
        val person = activePerson
        val query = activeQuery
        val result = if (append) searchResults.toMutableList() else mutableListOf()
        var foundThisBatch = false
        while (person != null || nextBrowsePage <= (browseTotalPages ?: Int.MAX_VALUE)) {
            val batchStart = result.size
            val batchLastPage = if (person != null) nextBrowsePage else
                minOf(browseTotalPages ?: (nextBrowsePage + TMDB_PAGES_PER_BATCH - 1),
                    nextBrowsePage + TMDB_PAGES_PER_BATCH - 1)
            while (nextBrowsePage <= batchLastPage &&
                nextBrowsePage <= (browseTotalPages ?: Int.MAX_VALUE)) {
                val page = nextBrowsePage
                val response = tmdb.browsePage(type, genre?.id, query, person?.id, page)
                browseTotalPages = response.totalPages.coerceAtLeast(1)
                nextBrowsePage = page + 1
                val candidates = if (person != null) response.titles.take(40) else response.titles
                for (batch in candidates.chunked(4)) {
                    val found = coroutineScope {
                        batch.map { title -> async { CatalogLookup.find(title.title, title.originalTitle, title.year, title.type) } }
                            .mapNotNull { it.await() }
                    }
                    result += found
                    val visibleResults = visible(result).distinctBy { "${it.isSeries()}:${it.id}" }
                    result.clear()
                    result.addAll(visibleResults)
                    if (generation == requestGeneration) searchResults = visibleResults
                }
                if (person != null) break
            }
            foundThisBatch = result.size > batchStart
            // If a batch has no catalog matches, keep scanning so the empty state can
            // eventually reveal available titles instead of trapping the user at page 2.
            if (person != null || foundThisBatch) break
        }
        if (generation == requestGeneration) {
            canLoadMore = person == null && nextBrowsePage <= (browseTotalPages ?: 0)
        }
    }

    private fun visible(posters: List<Poster>) = posters.filter { LanguageUtils.shouldDisplayTitle(it.title) }
        .distinctBy { "${it.isSeries()}:${it.id}" }

    private companion object {
        const val TMDB_PAGES_PER_BATCH = 2
    }

    fun clearSearch() {
        searchQuery = ""; requestGeneration++; searchJob?.cancel()
        searchResults = emptyList(); errorMessage = null; hasSearched = false; isLoading = false
        if (mediaType != null) triggerSearch()
    }
}
