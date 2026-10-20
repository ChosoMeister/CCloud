package com.pira.ccloud.core.designsystem

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Cinematic dark-first tokens. Inspired by the "One Stage" principles in
 * msharifi99/30nama-tizenos design/DESIGN.md (near-black chrome, one restrained accent);
 * values are our own. No third-party brand colors are used.
 */
@Immutable
object CinematicColors {
    val Stage = Color(0xFF0B0B0D)          // app background
    val Surface = Color(0xFF141417)        // cards, sheets
    val SurfaceRaised = Color(0xFF1C1C21)
    val Outline = Color(0x33FFFFFF)
    val TextPrimary = Color(0xFFF4F4F5)
    val TextSecondary = Color(0xFFB4B4BB)  // ≥ 7:1 on Stage
    val TextTertiary = Color(0xFF8A8A93)   // ≥ 4.5:1 on Stage
    val Accent = Color(0xFFE5484D)         // single accent: primary action, progress
    val OnAccent = Color.White
    val Focus = Color(0xFFFFFFFF)          // TV focus ring: white is distinct from accent "selected/playing"
    val Success = Color(0xFF3DD68C)
    val Warning = Color(0xFFF5A524)
    val Info = Color(0xFF6CB6FF)
}

@Immutable
object CinematicSpacing {
    val xxs = 2.dp; val xs = 4.dp; val sm = 8.dp; val md = 12.dp; val lg = 16.dp
    val xl = 24.dp; val xxl = 32.dp; val xxxl = 48.dp
    /** Horizontal screen gutters by form factor. */
    val gutterPhone = 16.dp; val gutterTablet = 32.dp; val gutterTv = 48.dp
}

@Immutable
object CinematicShapes {
    val card = RoundedCornerShape(10.dp)
    val chip = RoundedCornerShape(50)
    val button = RoundedCornerShape(8.dp)
    val sheet = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
}

@Immutable
object CinematicElevation { val none = 0.dp; val card = 2.dp; val focused = 8.dp }

object CinematicMotion {
    const val SHORT_MS = 150
    const val MEDIUM_MS = 250
    const val FOCUS_SCALE = 1.06f
    fun <T> standard(reduced: Boolean) = tween<T>(if (reduced) 0 else MEDIUM_MS, easing = FastOutSlowInEasing)
}

/** Scrims that keep text legible regardless of the image behind it. */
object CinematicScrims {
    val bottom = Brush.verticalGradient(0f to Color.Transparent, 0.55f to Color(0x99000000), 1f to CinematicColors.Stage)
    val start = Brush.horizontalGradient(0f to Color(0xE60B0B0D), 0.6f to Color(0x660B0B0D), 1f to Color.Transparent)
    val card = Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color(0xCC000000))
}

/** User/system reduced-motion preference, provided at the app root. */
val LocalReducedMotion = staticCompositionLocalOf { false }
