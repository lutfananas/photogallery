package com.lutfan.photogallery.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow

/** The app content captured behind floating glass bars. */
val LocalGlassBackdrop = compositionLocalOf<LayerBackdrop?> { null }

/**
 * Liquid-glass surface: the content behind is blurred, boosted (vibrancy) and
 * refracted at the rounded edges (lens), with a thin specular highlight, like iOS.
 * A light smoky tint keeps white icons readable over any photo.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    corner: Dp = 30.dp,
    tint: Color = Color(0xFF2B2B2B).copy(alpha = 0.34f),
    content: @Composable BoxScope.() -> Unit,
) {
    val backdrop = LocalGlassBackdrop.current
    val shape = RoundedCornerShape(corner)
    val glass = if (backdrop == null) {
        Modifier
    } else {
        Modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                vibrancy()
                blur(10.dp.toPx())
                lens(refractionHeight = 18.dp.toPx(), refractionAmount = 36.dp.toPx(), depthEffect = true)
            },
            highlight = { Highlight.Default.copy(alpha = 0.9f) },
            shadow = { Shadow(radius = 22.dp, color = Color.Black.copy(alpha = 0.18f)) },
            onDrawSurface = { drawRect(tint) },
        )
    }
    Box(modifier.then(glass), content = content)
}
