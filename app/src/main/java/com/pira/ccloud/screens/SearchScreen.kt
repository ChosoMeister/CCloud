package com.pira.ccloud.screens

import androidx.compose.runtime.saveable.rememberSaveable
import com.pira.ccloud.core.designsystem.rememberGridReturnFocus
import com.pira.ccloud.core.designsystem.tvSafeMenuHeight
import com.pira.ccloud.core.designsystem.tvFocusIndicator
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.focusProperties
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import com.pira.ccloud.core.model.ContentType
import kotlinx.coroutines.launch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.pira.ccloud.R
import com.pira.ccloud.data.model.Country
import com.pira.ccloud.data.model.Poster
import com.pira.ccloud.ui.search.SearchViewModel
import com.pira.ccloud.ui.search.SearchNavigation
import com.pira.ccloud.utils.DeviceUtils
import com.pira.ccloud.utils.StorageUtils

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(viewModel: SearchViewModel = viewModel(), navController: NavController? = null) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val fieldFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val isTv = DeviceUtils.isTv(context)
    var genresExpanded by remember { mutableStateOf(false) }
    val pendingFilter = SearchNavigation.pendingFilter
    LaunchedEffect(pendingFilter) {
        pendingFilter?.let {
            viewModel.applyMetadataFilter(it.type, it.genre, it.person)
            SearchNavigation.pendingFilter = null
        }
    }
    val openKeyboard: () -> Unit = {
        scope.launch {
            fieldFocus.requestFocus()
            androidx.compose.runtime.withFrameNanos { }
            keyboard?.show()
        }
    }
    val submit: () -> Unit = {
        keyboard?.hide()
        if (isTv) focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Down) else focusManager.clearFocus()
        viewModel.triggerSearch()
    }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
        .focusProperties { enter = { fieldFocus } }.focusGroup().padding(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            TextField(
                value = viewModel.searchQuery,
                onValueChange = viewModel::updateSearchQuery,
                modifier = Modifier.weight(1f).focusRequester(fieldFocus).tvFocusIndicator()
                    .onPreviewKeyEvent { event ->
                        if (isTv && (event.key == Key.DirectionCenter || event.key == Key.Enter)) {
                            if (event.type == KeyEventType.KeyUp) openKeyboard()
                            true
                        } else false
                    },
                placeholder = { Text("Search movies and series...", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = { Icon(Icons.Default.Search, "Search") },
                trailingIcon = {
                    if (viewModel.searchQuery.isNotEmpty()) IconButton(
                        onClick = viewModel::clearSearch, modifier = Modifier.tvFocusIndicator()
                    ) { Icon(Icons.Default.Clear, "Clear") }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, showKeyboardOnFocus = !isTv),
                keyboardActions = KeyboardActions(onSearch = { submit() }),
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            )
            Button(onClick = submit, modifier = Modifier.tvFocusIndicator()) { Text("Search") }
        }
        FlowRow(Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("All" to null, "Movies" to ContentType.MOVIE, "Series" to ContentType.SERIES).forEach { (label, type) ->
                FilterChip(selected = viewModel.mediaType == type,
                    onClick = { keyboard?.hide(); viewModel.selectMedia(type) },
                    label = { Text(label) }, modifier = Modifier.tvFocusIndicator())
            }
            Box {
                OutlinedButton(onClick = { genresExpanded = true },
                    enabled = viewModel.mediaType != null && !viewModel.isGenresLoading,
                    modifier = Modifier.tvFocusIndicator()) {
                    Text(if (viewModel.isGenresLoading) "Loading genres..." else viewModel.selectedGenre?.name ?: "TMDB genres", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                DropdownMenu(modifier = Modifier.tvSafeMenuHeight(), expanded = genresExpanded, onDismissRequest = { genresExpanded = false }) {
                    DropdownMenuItem(text = { Text("All genres") }, onClick = {
                        genresExpanded = false; viewModel.selectGenre(null)
                    })
                    viewModel.genres.forEach { genre ->
                        DropdownMenuItem(text = { Text(genre.name) }, onClick = {
                            genresExpanded = false; viewModel.selectGenre(genre)
                        })
                    }
                }
            }
            viewModel.selectedPerson?.let { person ->
                FilterChip(selected = true, onClick = { viewModel.selectPerson(null) },
                    label = { Text("Actor: ${person.name}  ×", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    modifier = Modifier.tvFocusIndicator())
            }
        }
        viewModel.genreError?.let { Text(it, color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 6.dp)) }
        if (viewModel.mediaType != null) Text("TMDB genres · Titles available in CCloud",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        if ((viewModel.isLoading || viewModel.isLoadingMore) && viewModel.searchResults.isNotEmpty()) LinearProgressIndicator(Modifier.fillMaxWidth())
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                viewModel.searchResults.isNotEmpty() -> SearchResultsGrid(
                    viewModel.searchResults, navController, context,
                    isLoading = viewModel.isLoading,
                    isLoadingMore = viewModel.isLoadingMore,
                    canLoadMore = viewModel.canLoadMore,
                    onLoadMore = viewModel::loadMore,
                )
                viewModel.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center).size(32.dp))
                viewModel.errorMessage != null -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(viewModel.errorMessage.orEmpty(), color = MaterialTheme.colorScheme.error)
                    Button(onClick = viewModel::triggerSearch, modifier = Modifier.tvFocusIndicator()) { Text("Retry") }
                }
                else -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Search, null, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(if (viewModel.hasSearched) "No matching titles in CCloud" else "Search movies and series",
                        style = MaterialTheme.typography.titleLarge)
                    Text(if (viewModel.hasSearched) "Try another genre or search term" else if (isTv) "Press OK on the search field to type, or choose Movies / Series for TMDB genres"
                        else "Tap the search field to type, or choose Movies / Series for TMDB genres",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
fun SearchResultsGrid(
    posters: List<Poster>,
    navController: NavController?,
    context: Context,
    isLoading: Boolean = false,
    isLoadingMore: Boolean = false,
    canLoadMore: Boolean = false,
    onLoadMore: () -> Unit = {},
) {
    var restoreFocusKey by rememberSaveable { androidx.compose.runtime.mutableStateOf<String?>(null) }
    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    LaunchedEffect(gridState, posters.size, isLoading, isLoadingMore, canLoadMore) {
        androidx.compose.runtime.snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .collect { lastVisibleIndex ->
                if (canLoadMore && !isLoading && !isLoadingMore && lastVisibleIndex >= posters.size - 3) onLoadMore()
            }
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 112.dp),
        state = gridState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(0.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(posters, key = { (if (it.isSeries()) "series:" else "movie:") + it.id }) { poster ->
            PosterItem(
                poster = poster,
                focusModifier = rememberGridReturnFocus((if (poster.isSeries()) "series:" else "movie:") + poster.id, restoreFocusKey) { restoreFocusKey = null },
                onClick = {
                    restoreFocusKey = (if (poster.isSeries()) "series:" else "movie:") + poster.id

                    navController?.let { com.pira.ccloud.core.catalog.CatalogNavigation.openPoster(context, it, poster) }
                }
            )
        }
        if (isLoadingMore) item {
            Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(28.dp))
            }
        }
    }
}

@Composable
fun PosterItem(
    poster: Poster,
    onClick: () -> Unit,
    focusModifier: Modifier = Modifier,
) {
    PosterGridCard(
        title = poster.title,
        imageUrl = poster.image,
        rating = poster.imdb,
        subtitle = listOfNotNull(
            if (poster.isSeries()) "Series" else "Movie",
            poster.year.takeIf { it > 0 }?.toString(),
            poster.genres.firstOrNull()?.title,
        ).joinToString(" · "),
        onClick = onClick,
        focusModifier = focusModifier,
    )
}
