package com.pira.ccloud.ui.details

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

/** A catalog genre chip that can filter the catalog when tapped. */
data class GenreChip(val id: Int, val title: String)

/** Full-resolution TMDB art for banners (any sized TMDB URL → /original/). Banners only, never cards. */
fun sharpBackdrop(url: String?): String? =
    url?.replace(Regex("/t/p/w\\d+/"), "/t/p/original/")

/**
 * Netflix / Apple TV style details header: full-bleed sharp banner (no wash-out), centered title,
 * meta line, white pill Play, glass back/favorite buttons and tappable genre chips.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CinematicDetailsHeader(
    title: String,
    bannerUrl: String?,
    meta: List<String>,
    genres: List<GenreChip>,
    playLabel: String?,
    isFavorite: Boolean,
    onBack: () -> Unit,
    onToggleFavorite: () -> Unit,
    onPlay: (() -> Unit)?,
    onGenreClick: (GenreChip) -> Unit,
    tagline: String? = null,
) {
    val cfg = LocalConfiguration.current
    val landscape = cfg.screenWidthDp > cfg.screenHeightDp
    val height = if (landscape) (cfg.screenHeightDp * 0.92f).dp else (cfg.screenHeightDp * 0.62f).dp
    val bg = MaterialTheme.colorScheme.background
    Box(Modifier.fillMaxWidth().height(height)) {
        AsyncImage(model = sharpBackdrop(bannerUrl), contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize())
        // Keep the image crisp: only a light top scrim and a bottom fade for legibility.
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
            0f to Color.Black.copy(alpha = 0.35f), 0.18f to Color.Transparent, 0.55f to Color.Transparent,
            0.85f to bg.copy(alpha = 0.85f), 1f to bg)))
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            GlassButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack)
            GlassButton(if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Favorite", onToggleFavorite,
                tint = if (isFavorite) Color(0xFFFF4D67) else Color.White)
        }
        Column(
            Modifier.align(Alignment.BottomCenter).padding(horizontal = 24.dp, vertical = 12.dp).widthIn(max = 760.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, color = MaterialTheme.colorScheme.onBackground, fontSize = 32.sp, lineHeight = 36.sp,
                fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis)
            tagline?.takeIf { it.isNotBlank() }?.let {
                Text(it, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f), fontSize = 14.sp, textAlign = TextAlign.Center)
            }
            if (meta.isNotEmpty()) {
                Text(meta.joinToString("  ·  "), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                    fontSize = 13.sp, textAlign = TextAlign.Center)
            }
            if (onPlay != null && playLabel != null) {
                Button(onClick = onPlay, shape = CircleShape, modifier = Modifier.padding(top = 4.dp).height(54.dp).widthIn(min = 220.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF4F4F6), contentColor = Color(0xFF111114))) {
                    Icon(Icons.Default.PlayArrow, null)
                    Spacer(Modifier.width(8.dp))
                    Text(playLabel, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                }
            }
            if (genres.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    genres.forEach { g ->
                        Text(g.title, color = MaterialTheme.colorScheme.onBackground, fontSize = 13.sp,
                            modifier = Modifier.clip(CircleShape)
                                .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.10f))
                                .border(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f), CircleShape)
                                .clickable { onGenreClick(g) }.padding(horizontal = 14.dp, vertical = 7.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun GlassButton(icon: ImageVector, cd: String, onClick: () -> Unit, tint: Color = Color.White) {
    Box(
        Modifier.size(46.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.35f))
            .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, cd, tint = tint) }
}

/** Cross-screen request to open the catalog filtered by a genre (consumed by Movies/Series screens). */
object GenreNavigation {
    var pendingMovieGenre by mutableStateOf<Int?>(null)
    var pendingSeriesGenre by mutableStateOf<Int?>(null)
}

/** Opens a catalog search result in the existing details screens (they read cached files). */
fun openCatalogPoster(context: android.content.Context, nav: androidx.navigation.NavController, p: com.pira.ccloud.data.model.Poster) {
    if (p.isSeries()) {
        com.pira.ccloud.utils.StorageUtils.saveSeriesToFile(context, p.toSeries()); nav.navigate("single_series/${p.id}")
    } else {
        com.pira.ccloud.utils.StorageUtils.saveMovieToFile(context, p.toMovie()); nav.navigate("single_movie/${p.id}")
    }
}
