package com.pira.ccloud.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.pira.ccloud.R
import com.pira.ccloud.VideoPlayerActivity
import com.pira.ccloud.core.designsystem.CinematicColors
import com.pira.ccloud.core.designsystem.CinematicScrims
import com.pira.ccloud.core.designsystem.CinematicShapes
import com.pira.ccloud.core.designsystem.CinematicSpacing
import com.pira.ccloud.core.designsystem.EmptyState
import com.pira.ccloud.core.designsystem.ErrorState
import com.pira.ccloud.core.designsystem.LoadingSkeleton
import com.pira.ccloud.core.designsystem.PosterCard
import com.pira.ccloud.core.designsystem.RatingPill
import com.pira.ccloud.core.home.ContinueItem
import com.pira.ccloud.core.home.HomeContent
import com.pira.ccloud.core.home.HomeRail
import com.pira.ccloud.core.home.RailKind
import com.pira.ccloud.core.model.Availability
import com.pira.ccloud.core.model.CatalogTitle
import com.pira.ccloud.core.model.ContentType
import com.pira.ccloud.core.model.LoadState
import com.pira.ccloud.utils.DeviceUtils
import com.pira.ccloud.utils.StorageUtils

@Composable
fun HomeScreen(viewModel: HomeViewModel, navController: NavController) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val isTv = remember { DeviceUtils.isTv(context) }
    val isTablet = remember { DeviceUtils.isTablet(context) }
    val gutter = when { isTv -> CinematicSpacing.gutterTv; isTablet -> CinematicSpacing.gutterTablet; else -> CinematicSpacing.gutterPhone }
    val posterWidth = when { isTv -> 150.dp; isTablet -> 140.dp; else -> 116.dp }

    // Progress/favorites may change in Details/Player; refresh local rails on return.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) viewModel.refreshLocal() }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    val openDetails: (CatalogTitle) -> Unit = { t ->
        // Legacy details screens read the title from cached files; write it first (same as MoviesScreen).
        when (t.ref.type) {
            ContentType.MOVIE -> viewModel.movie(t.ref.catalogId)?.let {
                StorageUtils.saveMovieToFile(context, it); navController.navigate("single_movie/${it.id}")
            }
            else -> viewModel.series(t.ref.catalogId)?.let {
                StorageUtils.saveSeriesToFile(context, it); navController.navigate("single_series/${it.id}")
            }
        }
    }
    val play: (CatalogTitle) -> Unit = { t ->
        val movie = if (t.ref.type == ContentType.MOVIE) viewModel.movie(t.ref.catalogId) else null
        val source = movie?.sources?.firstOrNull { it.url.isNotBlank() }
        // Only real catalog sources reach the player; anything else opens Details.
        if (movie != null && source != null && t.availability.isPlayable) {
            StorageUtils.saveMovieToFile(context, movie)
            VideoPlayerActivity.startWithMovieInfo(context, source.url, movie.id)
        } else openDetails(t)
    }

    Box(Modifier.fillMaxSize().background(CinematicColors.Stage)) {
        when (val s = state) {
            LoadState.Loading -> HomeSkeleton(gutter, posterWidth)
            LoadState.Empty -> EmptyState(Modifier.align(Alignment.Center))
            is LoadState.Failed -> ErrorState(s.failure, viewModel::refresh, Modifier.align(Alignment.Center))
            is LoadState.Ready -> HomeBody(s.data, false, gutter, posterWidth, isTv, play, openDetails)
            is LoadState.Partial -> HomeBody(s.data, true, gutter, posterWidth, isTv, play, openDetails)
        }
    }
}

@Composable
private fun HomeBody(
    content: HomeContent, partial: Boolean, gutter: Dp, posterWidth: Dp, isTv: Boolean,
    onPlay: (CatalogTitle) -> Unit, onDetails: (CatalogTitle) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
        content.hero?.let { hero ->
            item(key = "hero") { CinematicHero(hero, gutter, isTv, onPlay, onDetails) }
        }
        if (partial) item(key = "partial") {
            Text(stringResource(R.string.cine_partial_notice), color = CinematicColors.TextTertiary, fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = gutter, vertical = CinematicSpacing.sm))
        }
        items(content.rails, key = { it.kind.name }) { rail ->
            ContentRail(rail, gutter, posterWidth, onPlay, onDetails)
        }
    }
}

@Composable
private fun CinematicHero(
    hero: CatalogTitle, gutter: Dp, isTv: Boolean,
    onPlay: (CatalogTitle) -> Unit, onDetails: (CatalogTitle) -> Unit,
) {
    // Hero never takes the whole viewport: 16:9 of width, capped.
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val height = (maxWidth * 9 / 16).coerceAtMost(if (isTv) 420.dp else 360.dp).coerceAtLeast(220.dp)
        Box(Modifier.fillMaxWidth().height(height)) {
            AsyncImage(model = hero.backdropUrl, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(CinematicScrims.bottom))
            Box(Modifier.fillMaxSize().background(CinematicScrims.start))
            Column(
                Modifier.align(Alignment.BottomStart).padding(horizontal = gutter, vertical = CinematicSpacing.lg)
                    .fillMaxWidth(if (isTv) 0.5f else 1f),
                verticalArrangement = Arrangement.spacedBy(CinematicSpacing.sm),
            ) {
                Text(hero.title, color = CinematicColors.TextPrimary, fontSize = if (isTv) 34.sp else 26.sp,
                    fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() })
                Row(horizontalArrangement = Arrangement.spacedBy(CinematicSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                    hero.year?.let { Text(it.toString(), color = CinematicColors.TextSecondary, fontSize = 13.sp) }
                    hero.genres.take(2).takeIf { it.isNotEmpty() }?.let {
                        Text(it.joinToString(" · "), color = CinematicColors.TextSecondary, fontSize = 13.sp, maxLines = 1)
                    }
                    hero.ratings.firstOrNull()?.let { RatingPill(it) }
                }
                if (hero.synopsis.isNotBlank()) {
                    Text(hero.synopsis, color = CinematicColors.TextSecondary, fontSize = 13.sp, maxLines = 2,
                        overflow = TextOverflow.Ellipsis)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(CinematicSpacing.md)) {
                    if (hero.availability.isPlayable) {
                        Button(onClick = { onPlay(hero) }, shape = CinematicShapes.button,
                            colors = ButtonDefaults.buttonColors(containerColor = CinematicColors.Accent, contentColor = CinematicColors.OnAccent)) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.cine_action_play))
                        }
                    }
                    OutlinedButton(onClick = { onDetails(hero) }, shape = CinematicShapes.button) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = CinematicColors.TextPrimary)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.cine_action_details), color = CinematicColors.TextPrimary)
                    }
                }
            }
        }
    }
}

private fun RailKind.titleRes(): Int = when (this) {
    RailKind.CONTINUE_WATCHING -> R.string.cine_rail_continue
    RailKind.NEW_MOVIES -> R.string.cine_rail_new_movies
    RailKind.TOP_IMDB_MOVIES -> R.string.cine_rail_top_movies
    RailKind.NEW_SERIES -> R.string.cine_rail_new_series
    RailKind.TOP_IMDB_SERIES -> R.string.cine_rail_top_series
    RailKind.MY_LIST -> R.string.cine_rail_my_list
}

@Composable
private fun ContentRail(
    rail: HomeRail, gutter: Dp, posterWidth: Dp,
    onPlay: (CatalogTitle) -> Unit, onDetails: (CatalogTitle) -> Unit,
) {
    Column(Modifier.padding(top = CinematicSpacing.lg)) {
        Text(stringResource(rail.kind.titleRes()), color = CinematicColors.TextPrimary, fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = gutter, vertical = CinematicSpacing.sm).semantics { heading() })
        LazyRow(contentPadding = PaddingValues(horizontal = gutter), horizontalArrangement = Arrangement.spacedBy(CinematicSpacing.md)) {
            if (rail.kind == RailKind.CONTINUE_WATCHING) {
                items(rail.continueItems, key = { it.progress.key }) { ProgressCard(it, posterWidth * 1.6f) { onPlayOrDetails(it, onPlay, onDetails) } }
            } else {
                items(rail.items, key = { it.stableKey }) { PosterCard(it, onClick = { onDetails(it) }, width = posterWidth) }
            }
        }
    }
}

private fun onPlayOrDetails(item: ContinueItem, onPlay: (CatalogTitle) -> Unit, onDetails: (CatalogTitle) -> Unit) {
    // Movies resume directly (the player restores the saved position); episodes open the series.
    if (item.progress.episodeId == null && item.title.availability is Availability.Playable) onPlay(item.title) else onDetails(item.title)
}

/** Landscape card with real progress from the local store. */
@Composable
private fun ProgressCard(item: ContinueItem, width: Dp, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val pct = (item.progress.fraction * 100).toInt()
    val cd = item.title.title + ", " + stringResource(R.string.cine_progress_cd, pct)
    Column(Modifier.width(width)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(CinematicShapes.card)
                .then(if (focused) Modifier.border(3.dp, CinematicColors.Focus, CinematicShapes.card) else Modifier)
                .background(CinematicColors.Surface)
                .clickable(interactionSource = interaction, indication = null, onClick = onClick)
                .semantics { contentDescription = cd },
        ) {
            AsyncImage(model = item.title.backdropUrl ?: item.title.posterUrl, contentDescription = null,
                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(CinematicScrims.card))
            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = CinematicColors.TextPrimary,
                modifier = Modifier.align(Alignment.Center).size(36.dp))
            LinearProgressIndicator(
                progress = { item.progress.fraction }, color = CinematicColors.Accent, trackColor = CinematicColors.Outline,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp),
            )
        }
        Text(item.title.title, color = CinematicColors.TextPrimary, fontSize = 13.sp, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun HomeSkeleton(gutter: Dp, posterWidth: Dp) {
    Column(Modifier.fillMaxSize()) {
        LoadingSkeleton(Modifier.fillMaxWidth().heightIn(min = 220.dp).aspectRatio(16f / 9f))
        repeat(2) {
            Spacer(Modifier.height(CinematicSpacing.xl))
            Row(Modifier.padding(horizontal = gutter), horizontalArrangement = Arrangement.spacedBy(CinematicSpacing.md)) {
                repeat(6) { LoadingSkeleton(Modifier.width(posterWidth).aspectRatio(2f / 3f)) }
            }
        }
    }
}
