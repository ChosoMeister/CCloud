@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.blur
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.key
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.pira.ccloud.R
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
    val spotlight by viewModel.spotlight.collectAsState()
    val tmdbRails by viewModel.tmdbRails.collectAsState()
    val context = LocalContext.current
    val isTv = remember { DeviceUtils.isTv(context) }
    val isTablet = remember { DeviceUtils.isTablet(context) }
    val density = LocalDensity.current
    val homeDensity = Density(density.density * if (isTv) 0.92f else 1f, density.fontScale)
    val gutter = when { isTv -> CinematicSpacing.gutterTv; isTablet -> CinematicSpacing.gutterTablet; else -> CinematicSpacing.gutterPhone }
    val posterWidth = when { isTv -> 108.dp; isTablet -> 140.dp; else -> 116.dp }

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
                com.pira.ccloud.core.catalog.CatalogNavigation.openMovie(context, navController, it)
            }
            else -> viewModel.series(t.ref.catalogId)?.let {
                com.pira.ccloud.core.catalog.CatalogNavigation.openSeries(context, navController, it)
            }
        }
    }

    CompositionLocalProvider(LocalDensity provides homeDensity) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            when (val s = state) {
                LoadState.Loading -> HomeSkeleton(gutter, posterWidth)
                LoadState.Empty -> EmptyState(Modifier.align(Alignment.Center))
                is LoadState.Failed -> ErrorState(s.failure, viewModel::refresh, Modifier.align(Alignment.Center))
                is LoadState.Ready -> HomeBody(s.data, spotlight, tmdbRails, false, gutter, posterWidth, isTv, openDetails)
                is LoadState.Partial -> HomeBody(s.data, spotlight, tmdbRails, true, gutter, posterWidth, isTv, openDetails)
            }
        }
    }
}

@Composable
private fun HomeBody(
    content: HomeContent, spotlight: List<SpotlightItem>, tmdbRails: List<HomeRail>, partial: Boolean, gutter: Dp, posterWidth: Dp, isTv: Boolean,
    onDetails: (CatalogTitle) -> Unit,
) {
    val heroes: List<Pair<CatalogTitle, com.pira.ccloud.core.tmdb.TrendingTitle?>> = remember(content, spotlight) {
        if (spotlight.isNotEmpty()) spotlight.map { it.title to it.tmdb }
        else (listOfNotNull(content.hero) + content.rails.filter { it.kind != RailKind.CONTINUE_WATCHING && it.kind != RailKind.MY_LIST }
            .flatMap { it.items }.filter { it.backdropUrl != null && it.availability.isPlayable })
            .distinctBy { it.stableKey }.take(8).map { it to null }
    }
    val rails = remember(content, tmdbRails) {
        if (tmdbRails.isEmpty()) content.rails
        else {
            val byKind = content.rails.associateBy { it.kind }
            listOfNotNull(byKind[RailKind.CONTINUE_WATCHING]) + tmdbRails +
                listOfNotNull(byKind[RailKind.NEW_MOVIES], byKind[RailKind.NEW_SERIES], byKind[RailKind.MY_LIST])
        }
    }
    val heroFocusRequester = remember { FocusRequester() }
    val firstRailFocusRequester = remember { FocusRequester() }
    val firstRailKey = rails.firstOrNull { it.items.isNotEmpty() || it.continueItems.isNotEmpty() }?.key
    var lastFocusedTitle by rememberSaveable { androidx.compose.runtime.mutableStateOf<String?>(null) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = if (isTv) 32.dp else 112.dp)) {
        // Always emit the hero slot first so late-arriving heroes never leave the list scrolled past it.
        item(key = "hero") { if (heroes.isNotEmpty()) HeroCarousel(heroes, gutter, isTv, heroFocusRequester, firstRailFocusRequester.takeIf { firstRailKey != null }, lastFocusedTitle == null, { lastFocusedTitle = null }, onDetails) }
        if (partial) item(key = "partial") {
            Text(stringResource(R.string.cine_partial_notice), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = gutter, vertical = CinematicSpacing.sm))
        }
        items(rails, key = { it.key }) { rail ->
            ContentRail(rail, gutter, posterWidth, lastFocusedTitle, { lastFocusedTitle = it },
                firstRailFocusRequester.takeIf { rail.key == firstRailKey },
                heroFocusRequester.takeIf { rail.key == firstRailKey && heroes.isNotEmpty() }, onDetails)
        }
    }
}

@Composable
private fun HeroCarousel(
    heroes: List<Pair<CatalogTitle, com.pira.ccloud.core.tmdb.TrendingTitle?>>, gutter: Dp, isTv: Boolean,
    heroFocusRequester: FocusRequester, firstRailFocusRequester: FocusRequester?,
    restoreFocus: Boolean, onFocused: () -> Unit,
    onDetails: (CatalogTitle) -> Unit,
) {
    var selectedPage by rememberSaveable { androidx.compose.runtime.mutableIntStateOf(0) }
    val config = LocalConfiguration.current
    val landscape = config.screenWidthDp > config.screenHeightDp
    // Give artwork more room, while keeping the first content rail reachable on a TV viewport.
    val height = if (isTv) (config.screenHeightDp * 0.80f).dp.coerceIn(380.dp, 600.dp) else if (landscape) (config.screenHeightDp * 0.92f).dp else (config.screenHeightDp * 0.78f).dp
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val pagerState = rememberPagerState(pageCount = { heroes.size })
    val pagerScope = rememberCoroutineScope()
    val page = if (isTv) selectedPage.coerceIn(0, heroes.lastIndex) else pagerState.currentPage.coerceIn(0, heroes.lastIndex)
    val (title, spotlight) = heroes[page]
    val detailsLabel = stringResource(R.string.cine_action_details)
    val inputMode = androidx.compose.ui.platform.LocalInputModeManager.current
    // `currentPage` changes as soon as a pager animation starts. Keying this timer to it
    // cancels animateScrollToPage mid-flight, leaving the mobile banner between slides.
    // `settledPage` only changes after the swipe/animation has fully landed.
    val settledPage = pagerState.settledPage.coerceIn(0, heroes.lastIndex)
    LaunchedEffect(settledPage, heroes.size, isTv, focused) {
        if (heroes.size > 1 && (!isTv || focused)) {
            delay(8_000L)
            val nextPage = if (page >= heroes.lastIndex) 0 else page + 1
            if (isTv) selectedPage = nextPage
            else pagerState.animateScrollToPage(nextPage)
        }
    }
    LaunchedEffect(isTv) {
        if (isTv && restoreFocus) {
            inputMode.requestInputMode(androidx.compose.ui.input.InputMode.Keyboard)
            heroFocusRequester.requestFocus()
        }
    }
    Box(Modifier.fillMaxWidth().height(height).then(
        if (isTv) Modifier
            .focusRequester(heroFocusRequester)
            .focusProperties { firstRailFocusRequester?.let { down = it } }
            .onFocusChanged { if (it.isFocused) onFocused() }
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) false else if (event.nativeKeyEvent.repeatCount > 0 &&
                    (event.key == Key.DirectionLeft || event.key == Key.DirectionRight)) true else when (event.key) {
                    Key.DirectionLeft -> if (page > 0) { selectedPage = page - 1; true } else false
                    Key.DirectionRight -> { selectedPage = (page + 1).coerceAtMost(heroes.lastIndex); true }
                    Key.DirectionDown -> firstRailFocusRequester?.let { it.requestFocus(); true } ?: false
                    else -> false
                }
            }
            .clickable(interactionSource = interaction, indication = null, onClickLabel = detailsLabel) { onDetails(title) }
            .then(if (focused) Modifier.border(4.dp, Color.White, RoundedCornerShape(8.dp)) else Modifier)
            .semantics { contentDescription = "${spotlight?.title ?: title.title}, $detailsLabel" }
        else Modifier
    )) {
        if (isTv) {
            HeroPage(title, spotlight, gutter, true, landscape, focused, onDetails)
        } else {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()
                .pointerInput(pagerState) {
                    // Android's pager handles touch; also accept a primary-button mouse drag.
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        if (down.type != PointerType.Mouse) return@awaitEachGesture
                        var dx = 0f
                        var dy = 0f
                        var horizontal = false
                        var pressed: Boolean
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            dx += change.position.x - change.previousPosition.x
                            dy += change.position.y - change.previousPosition.y
                            if (abs(dx) > viewConfiguration.touchSlop && abs(dx) > abs(dy)) horizontal = true
                            if (horizontal) change.consume()
                            pressed = change.pressed
                        } while (pressed)
                        if (horizontal && abs(dx) > size.width * 0.12f) {
                            val target = (pagerState.currentPage + if (dx < 0) 1 else -1).coerceIn(0, heroes.lastIndex)
                            pagerScope.launch { pagerState.animateScrollToPage(target) }
                        }
                    }
                },
                key = { heroes[it].first.stableKey }) { index ->
                val (item, art) = heroes[index]
                HeroPage(item, art, gutter, false, landscape, false, onDetails)
            }
        }
    }
}

@Composable
private fun HeroPage(
    hero: CatalogTitle, spot: com.pira.ccloud.core.tmdb.TrendingTitle?, gutter: Dp, isTv: Boolean, landscape: Boolean,
    focused: Boolean,
    onDetails: (CatalogTitle) -> Unit,
) {
    val centered = !landscape
    Box(Modifier.fillMaxSize()) {
        // Spotlight items already carry original-size TMDB art; others are enriched on demand.
        val e = if (spot == null) com.pira.ccloud.ui.details.rememberEnrichment(
            if (hero.ref.type == ContentType.MOVIE) "movie:${hero.ref.catalogId}" else "series:${hero.ref.catalogId}",
            listOf(hero.title), hero.year, hero.ref.type) else null
        val raw = com.pira.ccloud.ui.details.sharpBackdrop(
            if (landscape) spot?.backdropUrl ?: e?.backdropUrl else spot?.posterUrl ?: e?.posterUrl)
        val tmdbArt = if (isTv) raw?.replace("/t/p/original/", "/t/p/w1280/") else raw
        if (tmdbArt != null) {
            AsyncImage(model = tmdbArt, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            // Low-res catalog poster: never stretch it full-bleed. Blurred ambient fill + crisp poster at native aspect.
            val art = hero.posterUrl ?: hero.backdropUrl
            AsyncImage(model = art, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(40.dp).background(Color.Black))
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))
            AsyncImage(model = art, contentDescription = null, contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxHeight(if (landscape) 0.78f else 0.62f)
                    .align(if (landscape) Alignment.CenterEnd else Alignment.TopCenter)
                    .padding(end = if (landscape) gutter * 2 else 0.dp, top = if (landscape) 40.dp else 90.dp)
                    .clip(RoundedCornerShape(18.dp)))
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
            0f to Color.Black.copy(alpha = 0.25f), 0.15f to Color.Transparent, 0.65f to Color.Transparent,
            1f to MaterialTheme.colorScheme.background)))
        if (landscape) Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(
            0f to Color.Black.copy(alpha = 0.6f), 0.45f to Color.Transparent)))
        Column(
            Modifier.align(if (centered) Alignment.BottomCenter else Alignment.BottomStart)
                .padding(start = gutter, end = gutter, bottom = if (isTv) 36.dp else 64.dp)
                .fillMaxWidth(if (landscape) 0.55f else 1f),
            horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(spot?.title ?: hero.title, color = Color.White, fontSize = if (isTv) 26.sp else 30.sp, lineHeight = if (isTv) 32.sp else 34.sp,
                fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis,
                textAlign = if (centered) TextAlign.Center else TextAlign.Start,
                modifier = Modifier.semantics { heading() })
            val typeLabel = stringResource(if (hero.ref.type == ContentType.MOVIE) R.string.cine_type_movie else R.string.cine_type_series)
            val meta = buildList {
                add(typeLabel)
                hero.ratings.firstOrNull()?.let { add("${it.kind.label} ${it.formatted()}") }
                hero.year?.let { add(it.toString()) }
                hero.genres.take(2).takeIf { it.isNotEmpty() }?.let { add(it.joinToString(", ")) }
            }.joinToString("  ·  ")
            Text(meta, color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val synopsis = (spot?.overview?.takeIf { it.isNotBlank() } ?: e?.overview?.takeIf { it.isNotBlank() } ?: hero.synopsis)
                .lines().filterNot { it.trim().startsWith("نام اصلی") }.joinToString(" ").trim()
            if (synopsis.isNotBlank()) {
                Text(synopsis, color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp, maxLines = 2,
                    overflow = TextOverflow.Ellipsis, textAlign = if (centered) TextAlign.Center else TextAlign.Start)
            }
            Spacer(Modifier.height(4.dp))
            val detailsLabel = stringResource(R.string.cine_action_details)
            Row(
                Modifier.clip(RoundedCornerShape(8.dp))
                    .background(if (focused) Color.White else Color.White.copy(alpha = 0.15f))
                    .then(if (!isTv) Modifier.clickable { onDetails(hero) } else Modifier)
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Default.Info, null, tint = if (focused) Color.Black else Color.White)
                Text(detailsLabel, color = if (focused) Color.Black else Color.White,
                    fontSize = 14.sp, fontWeight = FontWeight.Bold)
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
    RailKind.TMDB -> R.string.cine_rail_new_movies
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun ContentRail(
    rail: HomeRail, gutter: Dp, posterWidth: Dp,
    lastFocusedTitle: String?, onFocused: (String) -> Unit,
    entryRequester: FocusRequester?, upRequester: FocusRequester?,
    onDetails: (CatalogTitle) -> Unit,
) {
    var entryKey by rememberSaveable(rail.key) { androidx.compose.runtime.mutableStateOf<String?>(null) }
    val keys = if (rail.kind == RailKind.CONTINUE_WATCHING) rail.continueItems.map { "${rail.key}:${it.progress.key}" }
        else rail.items.map { "${rail.key}:${it.stableKey}" }
    val targetKey = entryKey?.takeIf { it in keys } ?: keys.firstOrNull()
    val localEntryRequester = remember { FocusRequester() }
    val rowEntryRequester = entryRequester ?: localEntryRequester
    val rememberFocus: (String) -> Unit = { key -> entryKey = key; onFocused(key) }
    Column(Modifier.padding(top = CinematicSpacing.lg)) {
        Text(rail.customTitle ?: stringResource(rail.kind.titleRes()), color = MaterialTheme.colorScheme.onSurface, fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = gutter, vertical = CinematicSpacing.sm).semantics { heading() })
        LazyRow(modifier = Modifier.focusProperties { enter = { rowEntryRequester } }.focusGroup(), contentPadding = PaddingValues(horizontal = gutter, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(CinematicSpacing.md)) {
            if (rail.kind == RailKind.CONTINUE_WATCHING) {
                items(rail.continueItems, key = { it.progress.key }) { item ->
                    val key = "${rail.key}:${item.progress.key}"
                    val focus = rememberTitleFocus(key, lastFocusedTitle, rememberFocus)
                        .then(if (key == targetKey) Modifier.focusRequester(rowEntryRequester) else Modifier)
                        .focusProperties { upRequester?.let { up = it } }
                    ProgressCard(item, posterWidth * 1.6f, focus) { onDetails(item.title) }
                }
            } else {
                items(rail.items, key = { it.stableKey }) { item ->
                    val key = "${rail.key}:${item.stableKey}"
                    val focus = rememberTitleFocus(key, lastFocusedTitle, rememberFocus)
                        .then(if (key == targetKey) Modifier.focusRequester(rowEntryRequester) else Modifier)
                        .focusProperties { upRequester?.let { up = it } }
                    PosterCard(item, onClick = { onDetails(item) }, width = posterWidth, focusModifier = focus)
                }
            }
        }
    }
}

/** Save the last title in Navigation's saveable state, including across Details and Back. */
@Composable
private fun rememberTitleFocus(key: String, lastFocusedTitle: String?, onFocused: (String) -> Unit): Modifier {
    val requester = remember { FocusRequester() }
    val inputMode = androidx.compose.ui.platform.LocalInputModeManager.current
    val isTv = DeviceUtils.isTv(LocalContext.current)
    LaunchedEffect(Unit) {
        if (isTv && key == lastFocusedTitle) {
            inputMode.requestInputMode(androidx.compose.ui.input.InputMode.Keyboard)
            requester.requestFocus()
        }
    }
    return Modifier.focusRequester(requester).onFocusChanged {
        if (it.isFocused) onFocused(key)
    }
}

/** Landscape card with real progress from the local store. */
@Composable
private fun ProgressCard(item: ContinueItem, width: Dp, focusModifier: Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val pct = (item.progress.fraction * 100).toInt()
    val cd = item.title.title + ", " + stringResource(R.string.cine_progress_cd, pct)
    Column(Modifier.width(width)) {
        Box(
            Modifier.then(focusModifier).fillMaxWidth().aspectRatio(16f / 9f).clip(CinematicShapes.card)
                .then(if (focused) Modifier.border(4.dp, MaterialTheme.colorScheme.primary, CinematicShapes.card) else Modifier)
                .background(MaterialTheme.colorScheme.surface)
                .clickable(interactionSource = interaction, indication = null, onClick = onClick)
                .semantics { contentDescription = cd },
        ) {
            AsyncImage(model = item.title.backdropUrl ?: item.title.posterUrl, contentDescription = null,
                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(CinematicScrims.card))
            Icon(Icons.Default.Info, contentDescription = null, tint = Color.White,
                modifier = Modifier.align(Alignment.Center).size(36.dp))
            LinearProgressIndicator(
                progress = { item.progress.fraction }, color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp),
            )
        }
        Text(item.title.title, color = if (focused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            fontSize = 13.sp, maxLines = 1, fontWeight = if (focused) FontWeight.Bold else FontWeight.Normal,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp).fillMaxWidth()
                .background(if (focused) MaterialTheme.colorScheme.primary else Color.Transparent).padding(4.dp))
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

/** TV: the hero Play button gets initial focus exactly once per process (no focus stealing on carousel advance). */
