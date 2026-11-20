package com.lutfan.photogallery.ui.screens

import android.content.Intent
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.layout
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil3.memory.MemoryCache
import coil3.request.ImageRequest
import com.lutfan.photogallery.ui.sharedPhoto
import com.lutfan.photogallery.ui.thumbCacheKey
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.lutfan.photogallery.GalleryViewModel
import com.lutfan.photogallery.data.Photo
import com.lutfan.photogallery.data.PhotoSource
import com.lutfan.photogallery.ui.theme.Palette
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerScreen(
    vm: GalleryViewModel,
    source: PhotoSource,
    startId: Long,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
) {
    val context = LocalContext.current
    val photos = vm.photosFor(source)
    if (photos.isEmpty()) {
        LaunchedEffect(Unit) { if (!vm.loading) onBack() }
        Box(Modifier.fillMaxSize().background(Color(0xFF1E1E1E)))
        return
    }
    val startIndex = photos.indexOfFirst { it.id == startId }.coerceAtLeast(0)
    val pager = rememberPagerState(initialPage = startIndex) { photos.size }
    // Bars start hidden; a single tap brings them in.
    var chrome by remember { mutableStateOf(false) }
    var zoomed by remember { mutableStateOf(false) }
    var dismiss by remember { mutableFloatStateOf(0f) } // 0..1 while swiping down to close
    ImmersiveSystemBars(visible = chrome && dismiss == 0f)
    var showInfo by remember { mutableStateOf(false) }
    val current = photos.getOrNull(pager.currentPage.coerceIn(0, photos.lastIndex)) ?: photos.first()

    val trashLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { vm.refresh() }

    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 1f - dismiss))) {
        HorizontalPager(
            state = pager,
            userScrollEnabled = !zoomed,
            beyondViewportPageCount = 1,
            key = { photos[it].id },
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            ZoomablePhoto(
                photo = photos[page],
                isCurrent = page == pager.currentPage,
                onTap = { chrome = !chrome },
                onZoomChange = { if (page == pager.currentPage) zoomed = it },
                onDismissProgress = { dismiss = it },
                onDismiss = onBack,
            )
        }

        AnimatedVisibility(
            chrome && dismiss == 0f,
            enter = fadeIn(tween(200)) + slideInVertically(tween(220)) { -it / 2 },
            exit = fadeOut(tween(160)) + slideOutVertically(tween(200)) { -it / 2 },
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(.55f), Color.Transparent)))
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = Color.White) }
                Column(Modifier.weight(1f)) {
                    Text(
                        DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(current.dateTaken)),
                        color = Color.White, style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(current.dateTaken)),
                        color = Palette.OnDarkMuted, style = MaterialTheme.typography.labelMedium,
                    )
                }
                IconButton(onClick = { showInfo = true }) { Icon(Icons.Outlined.Info, "Info", tint = Color.White) }
            }
        }

        AnimatedVisibility(
            chrome && dismiss == 0f,
            enter = fadeIn(tween(200)) + slideInVertically(tween(220)) { it / 2 },
            exit = fadeOut(tween(160)) + slideOutVertically(tween(200)) { it / 2 },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Row(
                Modifier
                    .navigationBarsPadding()
                    .padding(16.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(30.dp))
                    .background(Palette.Charcoal)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val fav = vm.isFavorite(current.id)
                BarAction(Icons.Outlined.Share, "Share") {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = current.mime
                        putExtra(Intent.EXTRA_STREAM, current.uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(send, null))
                }
                BarAction(Icons.Outlined.Tune, "Edit", highlight = true) { onEdit(current.id) }
                BarAction(
                    if (fav) Icons.Rounded.Favorite else Icons.Outlined.FavoriteBorder,
                    "Favorite",
                    tint = if (fav) Palette.Orange else Color.White,
                ) { vm.toggleFavorite(current.id) }
                BarAction(Icons.Outlined.DeleteOutline, "Trash") {
                    val pi = MediaStore.createTrashRequest(context.contentResolver, listOf(current.uri), true)
                    trashLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
                }
            }
        }
    }

    if (showInfo) {
        ModalBottomSheet(onDismissRequest = { showInfo = false }, containerColor = Color.White) {
            InfoSheet(current)
        }
    }
}

@Composable
private fun BarAction(
    icon: ImageVector,
    label: String,
    highlight: Boolean = false,
    tint: Color = Color.White,
    onClick: () -> Unit,
) {
    Column(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (highlight) Palette.Orange else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, label, tint = tint, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(2.dp))
        Text(label, color = tint, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun InfoSheet(p: Photo) {
    Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 36.dp)) {
        Text("Details", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))
        InfoRow("Name", p.name)
        InfoRow("Date", DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.SHORT).format(Date(p.dateTaken)))
        InfoRow("Album", p.bucketName)
        if (p.width > 0) InfoRow("Resolution", "${p.width} × ${p.height}  ·  %.1f MP".format(p.width * p.height / 1_000_000f))
        InfoRow("Size", formatBytes(p.size))
        InfoRow("Type", p.mime)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(label, color = Palette.Muted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.35f))
        Text(value, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(0.65f))
    }
}

/** Hides status + navigation bars while the viewer's own bars are hidden. */
@Composable
private fun ImmersiveSystemBars(visible: Boolean) {
    val activity = LocalActivity.current ?: return
    val controller = remember(activity) {
        WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
    LaunchedEffect(visible) {
        if (visible) controller.show(WindowInsetsCompat.Type.systemBars())
        else controller.hide(WindowInsetsCompat.Type.systemBars())
    }
    DisposableEffect(controller) {
        onDispose { controller.show(WindowInsetsCompat.Type.systemBars()) }
    }
}

private const val MAX_ZOOM = 6f
private const val DOUBLE_TAP_ZOOM = 2.5f
private val zoomSpring = spring<Float>(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow)

/**
 * One page of the viewer:
 * - single tap → toggle bars, double tap → animated zoom to the tapped point / back
 * - two fingers → pinch zoom in/out around the fingers (springs back below 1×)
 * - one finger when zoomed → pan; when not zoomed, vertical drag → swipe down to close
 */
@Composable
private fun ZoomablePhoto(
    photo: Photo,
    isCurrent: Boolean,
    onTap: () -> Unit,
    onZoomChange: (Boolean) -> Unit,
    onDismissProgress: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var drag by remember { mutableStateOf(Offset.Zero) } // swipe-to-close offset
    var container by remember { mutableStateOf(IntSize.Zero) }
    var anim by remember { mutableStateOf<Job?>(null) }

    val aspect = if (photo.width > 0 && photo.height > 0) photo.aspect else 0f

    fun fittedSize(): Size {
        val cw = container.width.toFloat()
        val ch = container.height.toFloat()
        if (aspect <= 0f || cw == 0f || ch == 0f) return Size(cw, ch)
        return if (aspect > cw / ch) Size(cw, cw / aspect) else Size(ch * aspect, ch)
    }

    fun clamp(o: Offset, s: Float): Offset {
        val f = fittedSize()
        val maxX = max(0f, (f.width * s - container.width) / 2f)
        val maxY = max(0f, (f.height * s - container.height) / 2f)
        return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
    }

    /** Springs scale/offset to a target together. */
    fun animateTo(targetScale: Float, targetOffset: Offset) {
        anim?.cancel()
        val startScale = scale
        val startOffset = offset
        anim = scope.launch {
            animate(0f, 1f, animationSpec = zoomSpring) { t, _ ->
                scale = startScale + (targetScale - startScale) * t
                offset = startOffset + (targetOffset - startOffset) * t
            }
        }
        onZoomChange(targetScale > 1.01f)
    }

    // Reset zoom when the page is swiped away.
    LaunchedEffect(isCurrent) {
        if (!isCurrent && scale != 1f) {
            anim?.cancel(); scale = 1f; offset = Offset.Zero
        }
    }

    val request = remember(photo.id) {
        ImageRequest.Builder(context)
            .data(photo.uri)
            .placeholderMemoryCacheKey(MemoryCache.Key(thumbCacheKey(photo.id)))
            .memoryCacheKey("full:${photo.id}")
            .size(2560)
            .build()
    }

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { container = it }
            .pointerInput(photo.id) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = { tap ->
                        if (scale > 1.01f) {
                            animateTo(1f, Offset.Zero)
                        } else {
                            val center = Offset(size.width / 2f, size.height / 2f)
                            // Keep the tapped point under the finger.
                            val target = clamp((center - tap) * (DOUBLE_TAP_ZOOM - 1f), DOUBLE_TAP_ZOOM)
                            animateTo(DOUBLE_TAP_ZOOM, target)
                        }
                    },
                )
            }
            .pointerInput(photo.id) {
                val slop = viewConfiguration.touchSlop
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    // 0 = undecided, 1 = zoom/pan, 2 = swipe-to-close, 3 = leave it to the pager
                    var mode = 0
                    var travel = Offset.Zero
                    val velocity = VelocityTracker()
                    val dismissDistance = size.height * 0.5f
                    fun progress(d: Offset) = (d.y / dismissDistance).coerceIn(0f, 1f)
                    do {
                        val event = awaitPointerEvent()
                        val fingers = event.changes.count { it.pressed }
                        if (mode == 0 || mode == 3) {
                            if (fingers >= 2) mode = 1
                            else if (mode == 0 && scale > 1.01f) mode = 1
                        }
                        when (mode) {
                            0 -> {
                                travel += event.calculatePan()
                                if (travel.getDistance() > slop) {
                                    // Only a downward drag starts swipe-to-close; sideways goes to the pager.
                                    mode = if (travel.y > 0f && travel.y > abs(travel.x) * 1.2f) 2 else 3
                                    if (mode == 2) drag = travel
                                }
                            }
                            1 -> {
                                anim?.cancel()
                                // Only fingers that were down in both frames count, so lifting a
                                // finger never makes the photo jump or produce NaN (which blanked it).
                                val t = event.stableTransform() ?: continue
                                val zoom = t.zoom
                                val pan = t.pan
                                val centroid = t.centroid
                                val newScale = (scale * zoom).coerceIn(0.6f, MAX_ZOOM)
                                val z = newScale / scale
                                val center = Offset(size.width / 2f, size.height / 2f)
                                var o = offset * z + (centroid - center) * (1f - z) + pan
                                if (newScale >= 1f) o = clamp(o, newScale)
                                if (!newScale.isFinite() || !o.x.isFinite() || !o.y.isFinite()) continue
                                scale = newScale
                                offset = o
                                onZoomChange(newScale > 1.01f)
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                            2 -> {
                                // The photo follows the finger freely (like iOS Photos).
                                drag += event.calculatePan()
                                event.changes.firstOrNull()?.let { velocity.addPosition(it.uptimeMillis, it.position) }
                                onDismissProgress(progress(drag))
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        }
                    } while (event.changes.any { it.pressed })

                    if (!scale.isFinite() || !offset.x.isFinite() || !offset.y.isFinite()) {
                        scale = 1f; offset = Offset.Zero
                    }
                    when (mode) {
                        1 -> when {
                            scale < 1f -> animateTo(1f, Offset.Zero)
                            else -> {
                                val c = clamp(offset, scale)
                                if (c != offset) animateTo(scale, c)
                            }
                        }
                        2 -> {
                            val vy = velocity.calculateVelocity().y
                            val flick = vy > 1200f && drag.y > 0f
                            if (flick || drag.y > size.height * 0.12f) {
                                // Back to the grid: the shared-element transition carries the photo
                                // from where it is now down into its tile.
                                onDismiss()
                            } else {
                                val start = drag
                                scope.launch {
                                    animate(0f, 1f, animationSpec = zoomSpring) { t, _ ->
                                        drag = start * (1f - t)
                                        onDismissProgress(progress(drag))
                                    }
                                }
                            }
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val sized = if (aspect > 0f) {
            val containerAspect = if (container.height > 0) container.width / container.height.toFloat() else 1f
            Modifier.aspectRatio(aspect, matchHeightConstraintsFirst = aspect < containerAspect)
        } else Modifier.fillMaxSize()
        AsyncImage(
            model = request,
            contentDescription = photo.name,
            contentScale = if (aspect > 0f) ContentScale.Crop else ContentScale.Fit,
            modifier = sized
                // Done in layout (not a graphics layer) so the shared-element return
                // starts from exactly where, and how small, the dragged photo is.
                .layout { measurable, constraints ->
                    val w = constraints.maxWidth
                    val h = constraints.maxHeight
                    val k = 1f - 0.4f * (drag.y / (container.height * 0.5f).coerceAtLeast(1f)).coerceIn(0f, 1f)
                    val cw = (w * k).roundToInt().coerceAtLeast(1)
                    val ch = (h * k).roundToInt().coerceAtLeast(1)
                    val placeable = measurable.measure(Constraints.fixed(cw, ch))
                    layout(w, h) {
                        placeable.place(
                            ((w - cw) / 2f + drag.x).roundToInt(),
                            ((h - ch) / 2f + drag.y).roundToInt(),
                        )
                    }
                }
                .sharedPhoto(photo.id, enabled = isCurrent)
                .graphicsLayer {
                    scaleX = scale; scaleY = scale
                    translationX = offset.x; translationY = offset.y
                },
        )
    }
}

private class Transform(val zoom: Float, val pan: Offset, val centroid: Offset)

/**
 * Pinch/pan from the pointers pressed in both the previous and current frame.
 * Returns null when fewer than one such pointer exists (e.g. all fingers lifted).
 */
private fun PointerEvent.stableTransform(): Transform? {
    val pts = changes.filter { it.pressed && it.previousPressed }
    if (pts.isEmpty()) return null
    var cur = Offset.Zero
    var prev = Offset.Zero
    pts.forEach { cur += it.position; prev += it.previousPosition }
    cur /= pts.size.toFloat()
    prev /= pts.size.toFloat()
    var zoom = 1f
    if (pts.size >= 2) {
        val curSpread = pts.sumOf { (it.position - cur).getDistance().toDouble() }
        val prevSpread = pts.sumOf { (it.previousPosition - prev).getDistance().toDouble() }
        if (curSpread > 0.0 && prevSpread > 0.0) zoom = (curSpread / prevSpread).toFloat()
    }
    return Transform(zoom, cur - prev, cur)
}
