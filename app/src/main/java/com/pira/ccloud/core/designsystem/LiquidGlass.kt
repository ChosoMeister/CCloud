package com.pira.ccloud.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Lightweight glass finish for native Compose surfaces: a tinted base, a restrained
 * top reflection, and a fine rim. Compose has no cross-platform backdrop refraction,
 * so this intentionally avoids claiming the web engine's optical distortion.
 */
fun Modifier.liquidGlassSurface(
    shape: Shape = RoundedCornerShape(24.dp),
    tint: Color = CinematicColors.Surface,
): Modifier = this
    .clip(shape)
    .background(tint)
    .background(
        Brush.verticalGradient(
            0f to Color.White.copy(alpha = 0.075f),
            0.42f to Color.White.copy(alpha = 0.025f),
            1f to Color.Transparent,
        ),
    )
    .border(
        width = 1.dp,
        brush = Brush.verticalGradient(
            0f to Color.White.copy(alpha = 0.18f),
            0.55f to Color.White.copy(alpha = 0.08f),
            1f to Color.White.copy(alpha = 0.04f),
        ),
        shape = shape,
    )
