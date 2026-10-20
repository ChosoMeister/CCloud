package com.pira.ccloud.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.pira.ccloud.VideoPlayerActivity
import com.pira.ccloud.components.DownloadOptionsDialog
import com.pira.ccloud.components.ExpandableText
import com.pira.ccloud.data.model.FavoriteItem
import com.pira.ccloud.data.model.Movie
import com.pira.ccloud.data.model.Source
import com.pira.ccloud.utils.DownloadUtils
import com.pira.ccloud.utils.StorageUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SingleMovieScreen(
    movieId: Int,
    navController: NavController
) {
    var movie by remember { mutableStateOf<Movie?>(null) }
    val context = LocalContext.current
    
    LaunchedEffect(movieId) {
        movie = StorageUtils.loadMovieFromFile(context, movieId)
    }
    
    // Directly render content without Scaffold since it's already in MainScreen's Scaffold
    if (movie != null) {
        MovieDetailsContent(
            movie = movie!!,
            onBackClick = { navController.popBackStack() },
            onPlayClick = { source ->
                // Launch video player activity
                VideoPlayerActivity.startWithMovieInfo(context, source.url, movie!!.id)
            },
            // Remove any padding from parent Scaffold to use full screen
            modifier = Modifier.fillMaxSize(),
            onGenreClick = { genreId ->
                com.pira.ccloud.ui.details.GenreNavigation.pendingMovieGenre = genreId
                navController.navigate("movies") { launchSingleTop = true }
            },
            onOpenCatalog = { p -> com.pira.ccloud.ui.details.openCatalogPoster(context, navController, p) },
        )
    } else {
        // Show loading or error state
        Box(
            modifier = Modifier
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back"
                    )
                }
                Text(
                    text = "Movie not found",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
        }
    }
}

@Composable
fun MovieDetailsContent(
    movie: Movie,
    onBackClick: () -> Unit,
    onPlayClick: (Source) -> Unit,
    modifier: Modifier = Modifier,
    onGenreClick: (Int) -> Unit = {},
    onOpenCatalog: ((com.pira.ccloud.data.model.Poster) -> Unit)? = null,
) {
    val context = LocalContext.current
    val layoutDirection = LocalLayoutDirection.current
    var selectedSource by remember { mutableStateOf<Source?>(null) }
    var showSourceDialog by remember { mutableStateOf(false) }
    
    // Source selection dialog
    if (showSourceDialog && selectedSource != null) {
        SourceOptionsDialog(
            source = selectedSource!!,
            onDismiss = { showSourceDialog = false },
            onDownload = { source ->
                showSourceDialog = false
                DownloadUtils.openUrl(context, source.url)
            },
            onPlay = { source ->
                showSourceDialog = false
                onPlayClick(source)
            }
        )
    }
    
    val enrichment = com.pira.ccloud.ui.details.rememberEnrichment(
        "movie:${movie.id}", listOf(movie.title), movie.year.takeIf { it > 0 },
        com.pira.ccloud.core.model.ContentType.MOVIE,
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Cinematic header: sharp TMDB/catalog banner, centered title, Play pill, tappable genres
        var isFavorite by remember { mutableStateOf(false) }
        LaunchedEffect(movie.id) { isFavorite = StorageUtils.isFavorite(context, movie.id, "movie") }
        val firstSource = movie.sources.firstOrNull { it.url.isNotBlank() }
        com.pira.ccloud.ui.details.CinematicDetailsHeader(
            title = movie.title,
            bannerUrl = enrichment?.backdropUrl ?: movie.cover.ifBlank { movie.image },
            tagline = enrichment?.tagline,
            meta = buildList {
                add("Movie")
                if (movie.imdb > 0) add("IMDb " + String.format(java.util.Locale.US, "%.1f", movie.imdb))
                enrichment?.rating?.let { add("TMDB " + it.formatted()) }
                if (movie.year > 0) add(movie.year.toString())
                (enrichment?.runtimeMinutes?.let { "${it / 60}h ${it % 60}m" } ?: movie.duration)?.takeIf { it.isNotBlank() }?.let { add(it) }
                movie.country.firstOrNull()?.let { add(it.title) }
            },
            genres = movie.genres.map { com.pira.ccloud.ui.details.GenreChip(it.id, it.title) },
            playLabel = if (firstSource != null) "Play" else null,
            isFavorite = isFavorite,
            onBack = onBackClick,
            onToggleFavorite = {
                if (isFavorite) {
                    StorageUtils.removeFavorite(context, movie.id, "movie"); isFavorite = false
                    android.widget.Toast.makeText(context, "Removed from favorites", android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    StorageUtils.saveFavorite(context, FavoriteItem(
                        id = movie.id, type = "movie", title = movie.title, description = movie.description,
                        year = movie.year, imdb = movie.imdb, rating = movie.rating, duration = movie.duration,
                        image = movie.image, cover = movie.cover, genres = movie.genres, country = movie.country,
                        sources = movie.sources,
                    ))
                    isFavorite = true
                    android.widget.Toast.makeText(context, "Added to favorites", android.widget.Toast.LENGTH_SHORT).show()
                }
            },
            onPlay = firstSource?.let { src -> { onPlayClick(src) } },
            onGenreClick = { onGenreClick(it.id) },
        )

        // Description
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 16.dp, end = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Description",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        
        enrichment?.overview?.takeIf { it.isNotBlank() }?.let { overview ->
            Text(
                text = overview,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        // Set layout direction to RTL for the description text
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            ExpandableText(
                text = movie.description,
                modifier = Modifier
                    .padding(start = 16.dp, end = 16.dp)
                    .fillMaxWidth()
            )
        }

        enrichment?.let { com.pira.ccloud.ui.details.EnrichmentSection(it, onOpenCatalog = onOpenCatalog) }
        
        // Sources/Quality options
        if (movie.sources.isNotEmpty()) {
            Text(
                text = "Available Qualities",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp)
            )
            
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(start = 16.dp, end = 16.dp)
            ) {
                movie.sources.forEach { source ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedSource = source
                                showSourceDialog = true
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = source.quality,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Play",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun SourceOptionsDialog(
    source: Source,
    onDismiss: () -> Unit,
    onDownload: (Source) -> Unit,
    onPlay: (Source) -> Unit
) {
    val context = LocalContext.current
    var showDownloadOptions by remember { mutableStateOf(false) }
    
    if (showDownloadOptions) {
        DownloadOptionsDialog(
            source = source,
            onDismiss = { showDownloadOptions = false },
            onCopyLink = { DownloadUtils.copyToClipboard(context, source.url) },
            onDownloadWithBrowser = { DownloadUtils.openUrl(context, source.url) },
            onDownloadWithADM = { DownloadUtils.openWithADM(context, source.url) },
            onOpenInVLC = { DownloadUtils.openWithVLC(context, source.url) },
            onOpenInMXPlayer = { DownloadUtils.openWithMXPlayer(context, source.url) },
            onOpenInKMPlayer = { DownloadUtils.openWithKMPlayer(context, source.url) }
        )
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = source.quality,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = "Choose an action for this video quality",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { showDownloadOptions = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    elevation = androidx.compose.material3.ButtonDefaults.elevatedButtonElevation()
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "Download",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Download Options")
                }
                
                Button(
                    onClick = { onPlay(source) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    elevation = androidx.compose.material3.ButtonDefaults.elevatedButtonElevation()
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Play in CCloud")
                }
                
                // Cancel button moved to the bottom of the dialog
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Cancel")
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 6.dp
    )
}


fun openUrl(context: Context, url: String) {
    DownloadUtils.openUrl(context, url)
}

