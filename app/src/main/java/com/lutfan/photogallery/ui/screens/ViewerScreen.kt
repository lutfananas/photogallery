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
    var chrome by remember { mutableStateOf(true) }
    var zoomed by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }
    val current = photos.getOrNull(pager.currentPage.coerceIn(0, photos.lastIndex)) ?: photos.first()

    val trashLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { vm.refresh() }

    Box(Modifier.fillMaxSize().background(Color(0xFF1E1E1E))) {
        HorizontalPager(
            state = pager,
            userScrollEnabled = !zoomed,
            beyondViewportPageCount = 1,
            key = { photos[it].id },
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            ZoomableImage(
                photo = photos[page],
                onTap = { chrome = !chrome },
                onZoomChange = { if (page == pager.currentPage) zoomed = it },
            )
        }

        AnimatedVisibility(chrome, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopCenter)) {
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

        AnimatedVisibility(chrome, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
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

@Composable
private fun ZoomableImage(photo: Photo, onTap: () -> Unit, onZoomChange: (Boolean) -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    fun clamp(o: Offset, s: Float, w: Int, h: Int): Offset {
        val maxX = w * (s - 1f) / 2f
        val maxY = h * (s - 1f) / 2f
        return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
    }

    AsyncImage(
        model = photo.uri,
        contentDescription = photo.name,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(photo.id) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = { tap ->
                        if (scale > 1f) {
                            scale = 1f; offset = Offset.Zero
                        } else {
                            scale = 2.5f
                            val center = Offset(size.width / 2f, size.height / 2f)
                            offset = clamp((center - tap) * 1.5f, scale, size.width, size.height)
                        }
                        onZoomChange(scale > 1f)
                    },
                )
            }
            .pointerInput(photo.id) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val multi = event.changes.count { it.pressed } > 1
                        if (multi || scale > 1f) {
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            offset = if (scale == 1f) Offset.Zero else clamp(offset + pan, scale, size.width, size.height)
                            onZoomChange(scale > 1f)
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .graphicsLayer {
                scaleX = scale; scaleY = scale
                translationX = offset.x; translationY = offset.y
            },
    )
}
