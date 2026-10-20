package com.pira.ccloud.ui.details

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import kotlinx.coroutines.async
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.pira.ccloud.core.model.ContentType
import com.pira.ccloud.core.tmdb.Enrichment
import com.pira.ccloud.core.tmdb.EnrichmentResult
import com.pira.ccloud.core.tmdb.MetadataTitle
import com.pira.ccloud.core.tmdb.Person
import com.pira.ccloud.core.tmdb.Review
import com.pira.ccloud.core.tmdb.TmdbProvider

private val TextHi: Color @Composable get() = MaterialTheme.colorScheme.onSurface
private val TextMid: Color @Composable get() = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
private val TextLo: Color @Composable get() = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
private val Chip: Color @Composable get() = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)

/**
 * Loads TMDB enrichment once per details screen (never per card). Returns null while loading
 * or when TMDB is disabled / unmatched / failing, so callers fall back to catalog data.
 */
@Composable
fun rememberEnrichment(catalogKey: String, titles: List<String>, year: Int?, type: ContentType): Enrichment? {
    val context = LocalContext.current
    val state by produceState<Enrichment?>(null, catalogKey) {
        value = (TmdbProvider.repository(context).enrich(catalogKey, titles, year, type) as? EnrichmentResult.Ready)?.enrichment
    }
    return state
}

/** Cast, crew, trailer, reviews and recommendations from TMDB, with attribution. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EnrichmentSection(e: Enrichment, modifier: Modifier = Modifier,
                      onOpenCatalog: ((com.pira.ccloud.data.model.Poster) -> Unit)? = null) {
    val context = LocalContext.current
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        if (e.genres.isNotEmpty()) {
            FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                e.genres.forEach { g ->
                    Text(g, color = TextHi, fontSize = 13.sp, modifier = Modifier.clip(CircleShape).background(Chip)
                        .padding(horizontal = 14.dp, vertical = 7.dp))
                }
            }
        }
        e.trailer?.let { t ->
            Row(
                Modifier.padding(horizontal = 16.dp).clip(CircleShape).background(Chip)
                    .clickable { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(t.url))) }
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.PlayArrow, null, tint = TextHi)
                Spacer(Modifier.width(6.dp))
                Text("Trailer", color = TextHi, fontWeight = FontWeight.SemiBold)
            }
        }
        if (e.directors.isNotEmpty()) {
            Text("Director: " + e.directors.joinToString(", ") { it.name }, color = TextMid, fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 16.dp))
        }
        if (e.cast.isNotEmpty()) {
            SectionTitle("Cast")
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                items(e.cast, key = { it.id }) { CastItem(it) }
            }
        }
        if (e.reviews.isNotEmpty()) {
            SectionTitle("Reviews (${e.totalReviews})")
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                e.reviews.take(3).forEach { ReviewCard(it) }
            }
        }
        if (e.recommendations.isNotEmpty() && onOpenCatalog != null) {
            // Only titles that are actually in the catalog (playable) are shown.
            val available by produceState<List<Pair<MetadataTitle, com.pira.ccloud.data.model.Poster>>>(emptyList(), e.tmdbId) {
                value = kotlinx.coroutines.coroutineScope {
                    e.recommendations.take(20).map { m ->
                        async { com.pira.ccloud.core.catalog.CatalogLookup.find(m.title, null, m.year, m.type)?.let { m to it } }
                    }.mapNotNull { it.await() }
                }
            }
            if (available.isNotEmpty()) {
                SectionTitle("More like this")
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(available, key = { it.first.tmdbId }) { (m, p) -> RecommendationCard(m) { onOpenCatalog(p) } }
                }
            }
        }
        Text("Metadata and images provided by TMDB. This product uses the TMDB API but is not endorsed or certified by TMDB.",
            color = TextLo, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 16.dp))
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, color = TextHi, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp))
}

@Composable
private fun CastItem(p: Person) {
    Column(Modifier.width(84.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(76.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            val initials = p.name.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1) }.uppercase()
            Text(initials, color = TextMid, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            if (p.imageUrl != null) AsyncImage(p.imageUrl, p.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Spacer(Modifier.height(6.dp))
        Text(p.name, color = TextHi, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
            overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        p.role?.let { Text(it, color = TextLo, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center) }
    }
}

@Composable
private fun ReviewCard(r: Review) {
    var expanded by rememberSaveable(r.id) { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Chip).clickable { expanded = !expanded }.padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(r.author, color = TextHi, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.weight(1f))
            r.authorRating?.let { Text("★ ${"%.0f".format(it)}/10", color = TextMid, fontSize = 12.sp) }
        }
        Text(r.content, color = TextMid, fontSize = 13.sp, maxLines = if (expanded) Int.MAX_VALUE else 4, overflow = TextOverflow.Ellipsis)
        Text(if (expanded) "Show less" else "Read more (may contain spoilers)", color = TextLo, fontSize = 11.sp)
    }
}

@Composable
private fun RecommendationCard(m: MetadataTitle, onClick: () -> Unit) {
    Column(Modifier.width(110.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick)) {
        AsyncImage(m.posterUrl, m.title, contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant))
        Text(m.title, color = TextHi, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
        Text(listOfNotNull(m.year?.toString(), m.rating?.let { "TMDB ${it.formatted()}" }).joinToString(" · "),
            color = TextLo, fontSize = 11.sp, maxLines = 1)
    }
}
