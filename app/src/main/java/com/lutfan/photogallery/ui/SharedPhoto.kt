package com.lutfan.photogallery.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier

@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedScope = compositionLocalOf<SharedTransitionScope?> { null }
val LocalNavScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/** Spring used for the grid ↔ viewer morph: quick, no wobble. */
@OptIn(ExperimentalSharedTransitionApi::class)
val PhotoBoundsTransform = BoundsTransform { _, _ ->
    spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow)
}

fun thumbCacheKey(id: Long) = "thumb:$id"

/** Marks this element as the shared "photo-<id>" between grid and viewer. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedPhoto(id: Long, enabled: Boolean = true): Modifier {
    if (!enabled) return this
    val shared = LocalSharedScope.current ?: return this
    val nav = LocalNavScope.current ?: return this
    return with(shared) {
        this@sharedPhoto.sharedElement(
            rememberSharedContentState("photo-$id"),
            animatedVisibilityScope = nav,
            boundsTransform = PhotoBoundsTransform,
        )
    }
}
