package com.pira.ccloud.core.designsystem

import androidx.annotation.StringRes
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import com.pira.ccloud.R
import com.pira.ccloud.core.model.Availability
import com.pira.ccloud.core.model.CatalogTitle
import com.pira.ccloud.core.model.Rating
import com.pira.ccloud.core.network.FailureKind

@StringRes
fun Availability.labelRes(): Int = when (this) {
    is Availability.Playable -> R.string.cine_avail_playable
    Availability.EpisodesOnDemand -> R.string.cine_avail_episodes
    Availability.NoStreams -> R.string.cine_avail_no_streams
    Availability.MetadataOnly -> R.string.cine_avail_metadata_only
    Availability.SubscriptionRequired -> R.string.cine_avail_subscription
    Availability.TemporarilyUnavailable -> R.string.cine_avail_temporarily
}

@StringRes
fun FailureKind.messageRes(): Int = when (this) {
    FailureKind.OFFLINE -> R.string.cine_err_offline
    FailureKind.TIMEOUT -> R.string.cine_err_timeout
    FailureKind.UNAUTHORIZED -> R.string.cine_err_auth
    FailureKind.FORBIDDEN -> R.string.cine_err_forbidden
    FailureKind.NOT_FOUND -> R.string.cine_err_not_found
    FailureKind.RATE_LIMITED -> R.string.cine_err_rate_limited
    FailureKind.SERVER -> R.string.cine_err_server
    FailureKind.INVALID_DATA -> R.string.cine_err_invalid
    FailureKind.UNKNOWN -> R.string.cine_err_unknown
}

/** Rating with explicit source label (e.g. "IMDb 7.8"); never color-only. */
@Composable
fun RatingPill(rating: Rating, modifier: Modifier = Modifier) {
    val cd = stringResource(R.string.cine_rating_cd, rating.kind.label, rating.formatted())
    Text(
        text = "${rating.kind.label} ${rating.formatted()}",
        color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
        modifier = modifier.semantics { contentDescription = cd }
            .clip(CinematicShapes.chip).background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/** Shown only for non-playable states so "Available" doesn't clutter every card. */
@Composable
fun AvailabilityBadge(availability: Availability, modifier: Modifier = Modifier, showWhenPlayable: Boolean = false) {
    if (availability.isPlayable && !showWhenPlayable) return
    if (availability == Availability.EpisodesOnDemand && !showWhenPlayable) return
    val color = when (availability) {
        is Availability.Playable -> CinematicColors.Success
        Availability.EpisodesOnDemand -> CinematicColors.Info
        Availability.SubscriptionRequired, Availability.TemporarilyUnavailable -> CinematicColors.Warning
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        text = stringResource(availability.labelRes()), color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold,
        modifier = modifier.clip(CinematicShapes.chip).background(color).padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@Composable
fun EmptyState(modifier: Modifier = Modifier, message: String = stringResource(R.string.cine_empty_title)) {
    Box(modifier.fillMaxWidth().padding(CinematicSpacing.xxl), contentAlignment = Alignment.Center) {
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
fun ErrorState(failure: FailureKind, onRetry: (() -> Unit)?, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(CinematicSpacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CinematicSpacing.lg),
    ) {
        Text(stringResource(failure.messageRes()), color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
        if (onRetry != null) {
            Button(onClick = onRetry, shape = CinematicShapes.button,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)) {
                Text(stringResource(R.string.cine_action_retry))
            }
        }
    }
}

/** Pulsing placeholder block; static when reduced motion is on. */
@Composable
fun LoadingSkeleton(modifier: Modifier = Modifier) {
    val reduced = LocalReducedMotion.current
    val alpha = if (reduced) 0.6f else {
        val t = rememberInfiniteTransition(label = "skeleton")
        t.animateFloat(0.35f, 0.75f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "a").value
    }
    val cd = stringResource(R.string.cine_loading)
    Box(modifier.semantics { contentDescription = cd }.alpha(alpha).clip(CinematicShapes.card).background(MaterialTheme.colorScheme.surfaceVariant))
}

/**
 * Focus-aware poster card for touch and D-pad. Focus = white ring + highlighted title (distinct from any
 * selected/playing state, which uses the accent). Coil loads a size-constrained image.
 */
@Composable
fun PosterCard(
    item: CatalogTitle,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 120.dp,
    focusModifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Column(modifier.width(width)) {
        Box(
            Modifier.then(focusModifier).fillMaxWidth().aspectRatio(2f / 3f)
                .clip(CinematicShapes.card)
                .then(if (focused) Modifier.border(4.dp, MaterialTheme.colorScheme.primary, CinematicShapes.card) else Modifier)
                .background(MaterialTheme.colorScheme.surface)
                .clickable(interactionSource = interaction, indication = null, onClick = onClick)
                .semantics { contentDescription = item.title + (item.year?.let { ", $it" } ?: "") },
        ) {
            if (item.posterUrl != null) {
                SubcomposeAsyncImage(
                    model = item.posterUrl, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    loading = { LoadingSkeleton(Modifier.fillMaxSize()) },
                    error = { PosterFallback(item.title) },
                )
            } else PosterFallback(item.title)
            AvailabilityBadge(item.availability, Modifier.align(Alignment.TopStart).padding(6.dp))
        }
        Text(item.title, color = if (focused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            fontSize = 13.sp, fontWeight = if (focused) FontWeight.Bold else FontWeight.Normal, maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp).fillMaxWidth().clip(RoundedCornerShape(4.dp))
                .background(if (focused) MaterialTheme.colorScheme.primary else Color.Transparent).padding(horizontal = 4.dp, vertical = 4.dp))
    }
}

/** Honest fallback artwork: the title on a neutral surface (no fake images). */
@Composable
private fun PosterFallback(title: String) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant).padding(8.dp), contentAlignment = Alignment.Center) {
        Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, textAlign = TextAlign.Center, maxLines = 4,
            overflow = TextOverflow.Ellipsis)
    }
}
