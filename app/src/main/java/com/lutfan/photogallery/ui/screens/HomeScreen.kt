package com.lutfan.photogallery.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.launch
import com.lutfan.photogallery.GalleryViewModel
import com.lutfan.photogallery.ui.components.CircleThumb
import com.lutfan.photogallery.ui.components.EmptyState
import com.lutfan.photogallery.ui.components.PhotoTile
import com.lutfan.photogallery.ui.theme.Palette

@Composable
fun HomeScreen(
    vm: GalleryViewModel,
    partialAccess: Boolean,
    onManageAccess: () -> Unit,
    onSearch: () -> Unit,
    onAlbum: (Long) -> Unit,
    onSeeMore: () -> Unit,
    onPhoto: (Long) -> Unit,
) {
    val photos = vm.photos
    val full = StaggeredGridItemSpan.FullLine
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    // Pinch to zoom the grid like iOS Photos: 2 = large cards, 3..6 = dense square grid.
    var columns by rememberSaveable { mutableIntStateOf(2) }
    var pinch by remember { mutableFloatStateOf(1f) }
    var pivot by remember { mutableStateOf(Offset.Zero) }
    val dense = columns > 2
    val gap by animateDpAsState(if (dense) 3.dp else 16.dp, gridSpring(), label = "gap")
    val side by animateDpAsState(if (dense) 4.dp else 20.dp, gridSpring(), label = "side")
    val corner = when (columns) { 2 -> 22.dp; 3 -> 10.dp; 4 -> 7.dp; else -> 5.dp }

    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(columns),
        modifier = Modifier
            .fillMaxSize()
            .background(Palette.Paper)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var acc = 1f
                    var pinching = false
                    do {
                        // Initial pass: we see two-finger gestures before the grid scrolls.
                        val ev = awaitPointerEvent(PointerEventPass.Initial)
                        val pts = ev.changes.filter { it.pressed && it.previousPressed }
                        if (pts.size >= 2) {
                            pinching = true
                            val zoom = ev.calculateZoom()
                            if (zoom.isFinite() && zoom > 0f) acc *= zoom
                            val c = ev.calculateCentroid(useCurrent = true)
                            if (c.isSpecified) pivot = c
                            when {
                                acc > 1.28f && columns > 2 -> { columns--; acc = 1f; haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                                acc < 0.78f && columns < 6 -> { columns++; acc = 1f; haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                            }
                            // Live rubber-band feedback while pinching.
                            pinch = (1f + (acc - 1f) * 0.55f).coerceIn(0.88f, 1.14f)
                            ev.changes.forEach { it.consume() }
                        } else if (pinching) {
                            ev.changes.forEach { it.consume() }
                        }
                    } while (ev.changes.any { it.pressed })
                    if (pinching) {
                        val start = pinch
                        scope.launch { animate(start, 1f, animationSpec = gridSpring()) { v, _ -> pinch = v } }
                    }
                }
            }
            .graphicsLayer {
                scaleX = pinch; scaleY = pinch
                if (size.width > 0f && size.height > 0f) {
                    transformOrigin = TransformOrigin(
                        (pivot.x / size.width).coerceIn(0f, 1f),
                        (pivot.y / size.height).coerceIn(0f, 1f),
                    )
                }
            },
        contentPadding = PaddingValues(start = side, end = side, bottom = 130.dp),
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalItemSpacing = gap,
    ) {
        item(span = full) {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(start = 20.dp - side, end = 20.dp - side, top = 28.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Discover", style = MaterialTheme.typography.displaySmall, modifier = Modifier.weight(1f))
                IconButton(onClick = onSearch) {
                    Icon(Icons.Outlined.Search, "Search", modifier = Modifier.size(28.dp))
                }
            }
        }

        if (vm.albums.isNotEmpty()) {
            item(span = full) {
                LazyRow(
                    contentPadding = PaddingValues(start = 20.dp - side, end = 20.dp - side, top = 12.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(horizontal = 0.dp),
                ) {
                    items(vm.albums, key = { it.id }) { album ->
                        CircleThumb(album.name, album.cover) { onAlbum(album.id) }
                    }
                }
            }
        }

        if (partialAccess) {
            item(span = full) {
                Row(
                    Modifier
                        .padding(horizontal = 20.dp - side)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Palette.OrangeSoft)
                        .clickable(onClick = onManageAccess)
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "You've given access to selected photos only.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text("Manage", color = Palette.Orange, style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        item(span = full) {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp - side, end = 20.dp - side, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Recent", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Text(
                    "See more",
                    color = Palette.Muted,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.clickable(onClick = onSeeMore).padding(6.dp),
                )
            }
        }

        when {
            photos.isEmpty() && vm.loading -> item(span = full) {
                Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Palette.Orange)
                }
            }
            photos.isEmpty() -> item(span = full) {
                EmptyState(Icons.Outlined.PhotoLibrary, "No photos yet", "Take a photo with the camera button below.")
            }
            else -> itemsIndexed(photos, key = { _, p -> p.id }) { index, photo ->
                PhotoTile(
                    photo = photo,
                    modifier = Modifier.animateItem(
                        fadeInSpec = null, fadeOutSpec = null,
                        placementSpec = spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow),
                    ),
                    aspect = when {
                        dense -> 1f
                        index == 0 -> 0.74f
                        else -> photo.aspect.coerceIn(0.72f, 1.35f)
                    },
                    corner = corner,
                    elevated = index == 0 && !dense,
                    favorite = vm.isFavorite(photo.id),
                    selectionMode = vm.selecting,
                    selected = photo.id in vm.selection,
                    onLongClick = { vm.toggleSelect(photo.id) },
                    onClick = { if (vm.selecting) vm.toggleSelect(photo.id) else onPhoto(photo.id) },
                )
            }
        }
    }
}

private fun <T> gridSpring() = spring<T>(dampingRatio = 0.88f, stiffness = Spring.StiffnessMediumLow)
