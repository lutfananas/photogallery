package com.lutfan.photogallery.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lutfan.photogallery.GalleryViewModel
import com.lutfan.photogallery.data.Photo
import com.lutfan.photogallery.ui.LocalSharedScope
import com.lutfan.photogallery.ui.components.CircleThumb
import com.lutfan.photogallery.ui.components.EmptyState
import com.lutfan.photogallery.ui.components.PhotoTile
import com.lutfan.photogallery.ui.theme.Palette
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/* ------------------------------------------------------------------ layout model */

/** Zoom levels: 2 = big squares, 3 = monthly mosaic (default), 4..6 = dense grid. */
private const val MIN_LEVEL = 2
private const val MOSAIC = 3
private const val MAX_LEVEL = 6

private sealed interface GridRow {
    val key: Any
}

private data class MonthHeader(val label: String, val count: Int, override val key: Any) : GridRow

/** One big square + up to two small ones stacked beside it. */
private data class MosaicBlock(val big: Photo, val small: List<Photo>, val bigLeft: Boolean, override val key: Any) : GridRow

/** A plain row of equal squares. */
private data class Strip(val photos: List<Photo>, override val key: Any) : GridRow

private data class Tile(val photo: Photo, val x: Dp, val y: Dp, val size: Dp)

/** Pure geometry for one zoom level, so drawing and hit-testing agree exactly. */
private class Geometry(val level: Int, width: Dp) {
    val side: Dp = if (level <= MOSAIC) 20.dp else 2.dp
    val gap: Dp = when (level) { 2 -> 10.dp; MOSAIC -> 8.dp; else -> 2.dp }
    val corner: Dp = when (level) { 2 -> 18.dp; MOSAIC -> 14.dp; 4 -> 6.dp; else -> 3.dp }
    private val inner = width - side * 2
    val columns = level
    val cell: Dp = (inner - gap * (columns - 1)) / columns
    val big: Dp = cell * 2 + gap
    val headerHeight = 58.dp

    fun height(row: GridRow): Dp = when (row) {
        is MonthHeader -> headerHeight
        is MosaicBlock -> big + gap
        is Strip -> cell + gap
    }

    fun tiles(row: GridRow): List<Tile> = when (row) {
        is MonthHeader -> emptyList()
        is Strip -> row.photos.mapIndexed { i, p -> Tile(p, (cell + gap) * i, 0.dp, cell) }
        is MosaicBlock -> {
            val bigX = if (row.bigLeft) 0.dp else cell + gap
            val smallX = if (row.bigLeft) big + gap else 0.dp
            listOf(Tile(row.big, bigX, 0.dp, big)) +
                row.small.mapIndexed { i, p -> Tile(p, smallX, (cell + gap) * i, cell) }
        }
    }
}

private val monthFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())

private fun buildRows(photos: List<Photo>, level: Int): List<GridRow> {
    val rows = ArrayList<GridRow>()
    val cal = Calendar.getInstance()
    photos.groupBy {
        cal.timeInMillis = it.dateTaken
        cal.get(Calendar.YEAR) * 12 + cal.get(Calendar.MONTH)
    }.forEach { (month, items) ->
        rows += MonthHeader(monthFormat.format(Date(items.first().dateTaken)), items.size, "h-$month")
        if (level == MOSAIC) {
            // Repeating pattern from the reference: big + two small, then a row of three.
            var i = 0
            var block = 0
            while (i < items.size) {
                val left = items.size - i
                if (block % 2 == 0 && left >= 3) {
                    rows += MosaicBlock(items[i], items.subList(i + 1, i + 3), bigLeft = (block / 2) % 2 == 0, key = "m-${items[i].id}")
                    i += 3
                } else {
                    val n = minOf(3, left)
                    rows += Strip(items.subList(i, i + n), "s-${items[i].id}")
                    i += n
                }
                block++
            }
        } else {
            items.chunked(level).forEach { rows += Strip(it, "s-${it.first().id}") }
        }
    }
    return rows
}

/* ------------------------------------------------------------------ screen */

@OptIn(ExperimentalSharedTransitionApi::class)
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
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    var level by rememberSaveable { mutableIntStateOf(MOSAIC) }
    var pinch by remember { mutableFloatStateOf(1f) }
    var pivot by remember { mutableStateOf(Offset.Zero) }
    // Where the next level's list should start so the pinched photo stays under the fingers.
    var pendingStart by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    // The live list state of the level currently shown (for hit-testing).
    var currentState by remember { mutableStateOf<LazyListState?>(null) }

    val topItems = 2 + (if (partialAccess) 1 else 0) // title, albums, (banner)

    BoxWithConstraints(Modifier.fillMaxSize().background(Palette.Paper)) {
        val width = maxWidth
        val rowsFor = remember(vm.photos, width) { HashMap<Int, List<GridRow>>() }
        fun rows(l: Int) = rowsFor.getOrPut(l) { buildRows(vm.photos, l) }

        /** Finds the photo under the pivot in the current level and computes where the new level starts. */
        fun changeLevel(newLevel: Int) {
            val state = currentState
            val oldGeo = Geometry(level, width)
            val newGeo = Geometry(newLevel, width)
            var anchorId: Long? = null
            var frac = 0f
            var anchorScreenY = pivot.y
            if (state != null) {
                val oldRows = rows(level)
                for (info in state.layoutInfo.visibleItemsInfo) {
                    val r = info.index - topItems
                    if (r !in oldRows.indices) continue
                    val tiles = oldGeo.tiles(oldRows[r])
                    with(density) {
                        for (t in tiles) {
                            val left = (oldGeo.side + t.x).toPx()
                            val top = info.offset + t.y.toPx()
                            val sz = t.size.toPx()
                            if (pivot.x in left..(left + sz) && pivot.y in top..(top + sz)) {
                                anchorId = t.photo.id
                                frac = (pivot.y - top) / sz
                            }
                        }
                    }
                    if (anchorId != null) break
                }
                // Not over a photo: keep the first visible photo where it is.
                if (anchorId == null) {
                    state.layoutInfo.visibleItemsInfo.firstOrNull { (it.index - topItems) in oldRows.indices && oldGeo.tiles(oldRows[it.index - topItems]).isNotEmpty() }?.let { info ->
                        anchorId = oldGeo.tiles(oldRows[info.index - topItems]).first().photo.id
                        frac = 0f
                        anchorScreenY = info.offset.toFloat()
                    }
                }
            }
            val newRows = rows(newLevel)
            pendingStart = anchorId?.let { id ->
                val r = newRows.indexOfFirst { row -> newGeo.tiles(row).any { it.photo.id == id } }
                if (r < 0) return@let null
                val tile = newGeo.tiles(newRows[r]).first { it.photo.id == id }
                with(density) {
                    var index = r
                    var offset = (tile.y.toPx() + frac * tile.size.toPx() - anchorScreenY).toInt()
                    while (offset < 0 && index > 0) {
                        index--
                        offset += newGeo.height(newRows[index]).roundToPx()
                    }
                    if (offset < 0) 0 to 0 else (index + topItems) to offset
                }
            }
            level = newLevel
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }

        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        var acc = 1f
                        var pinching = false
                        do {
                            // Initial pass: see two-finger gestures before the list scrolls.
                            val ev = awaitPointerEvent(PointerEventPass.Initial)
                            val pts = ev.changes.filter { it.pressed && it.previousPressed }
                            if (pts.size >= 2) {
                                pinching = true
                                val zoom = ev.calculateZoom()
                                if (zoom.isFinite() && zoom > 0f) acc *= zoom
                                val c = ev.calculateCentroid(useCurrent = true)
                                if (c.isSpecified) pivot = c
                                when {
                                    acc > 1.25f && level > MIN_LEVEL -> { changeLevel(level - 1); acc = 1f }
                                    acc < 0.8f && level < MAX_LEVEL -> { changeLevel(level + 1); acc = 1f }
                                }
                                pinch = (1f + (acc - 1f) * 0.35f).coerceIn(0.93f, 1.08f)
                                ev.changes.forEach { it.consume() }
                            } else if (pinching) {
                                ev.changes.forEach { it.consume() }
                            }
                        } while (ev.changes.any { it.pressed })
                        if (pinching) {
                            val start = pinch
                            scope.launch { animate(start, 1f, animationSpec = spring(0.88f, Spring.StiffnessMediumLow)) { v, _ -> pinch = v } }
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
                }
        ) {
            AnimatedContent(
                targetState = level,
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(220)) using null },
                label = "gridLevel",
            ) { lvl ->
                val state = rememberSaveable(saver = LazyListState.Saver) {
                    val start = pendingStart
                    pendingStart = null
                    LazyListState(start?.first ?: 0, start?.second ?: 0)
                }
                val isTarget = transition.targetState == EnterExitState.Visible
                SideEffect { if (isTarget) currentState = state }
                PhotoLevel(
                    vm = vm,
                    level = lvl,
                    width = width,
                    rows = rows(lvl),
                    state = state,
                    isTarget = isTarget,
                    contentScope = this,
                    partialAccess = partialAccess,
                    onManageAccess = onManageAccess,
                    onSearch = onSearch,
                    onAlbum = onAlbum,
                    onSeeMore = onSeeMore,
                    onPhoto = onPhoto,
                )
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
private val MorphTransform = BoundsTransform { _, _ ->
    spring(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow)
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun PhotoLevel(
    vm: GalleryViewModel,
    level: Int,
    width: Dp,
    rows: List<GridRow>,
    state: LazyListState,
    isTarget: Boolean,
    contentScope: AnimatedContentScope,
    partialAccess: Boolean,
    onManageAccess: () -> Unit,
    onSearch: () -> Unit,
    onAlbum: (Long) -> Unit,
    onSeeMore: () -> Unit,
    onPhoto: (Long) -> Unit,
) {
    val geo = remember(level, width) { Geometry(level, width) }
    val shared = LocalSharedScope.current

    LazyColumn(
        state = state,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 130.dp),
    ) {
        item(key = "title") {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(start = 20.dp, end = 12.dp, top = 28.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Discover", style = MaterialTheme.typography.displaySmall, modifier = Modifier.weight(1f))
                IconButton(onClick = onSearch) {
                    Icon(Icons.Outlined.Search, "Search", modifier = Modifier.size(28.dp))
                }
            }
        }
        item(key = "albums") {
            if (vm.albums.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 14.dp, top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Albums", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    Text(
                        "See all",
                        color = Palette.Orange,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.clickable(onClick = onSeeMore).padding(6.dp),
                    )
                }
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(vm.albums, key = { it.id }) { album ->
                        CircleThumb(album.name, album.cover) { onAlbum(album.id) }
                    }
                }
            } else {
                Spacer(Modifier.height(1.dp))
            }
        }
        if (partialAccess) {
            item(key = "banner") {
                Row(
                    Modifier
                        .padding(horizontal = 20.dp, vertical = 6.dp)
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

        if (rows.isEmpty()) {
            item(key = "empty") {
                if (vm.loading) {
                    Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Palette.Orange)
                    }
                } else {
                    EmptyState(Icons.Outlined.PhotoLibrary, "No photos yet", "Take a photo with the camera button below.")
                }
            }
        }

        items(rows, key = { it.key }, contentType = { it::class }) { row ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(geo.height(row))
                    .padding(horizontal = geo.side)
            ) {
                when (row) {
                    is MonthHeader -> Row(
                        Modifier.fillMaxSize().padding(start = if (level > MOSAIC) 10.dp else 0.dp, end = if (level > MOSAIC) 10.dp else 0.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        Text(row.label, fontSize = 18.sp, fontWeight = FontWeight.Medium, color = Palette.Ink, modifier = Modifier.weight(1f))
                        Text("${row.count} photos", style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
                    }
                    else -> geo.tiles(row).forEach { t ->
                        val morph = if (shared != null) with(shared) {
                            Modifier.sharedElement(
                                rememberSharedContentState("grid-${t.photo.id}"),
                                animatedVisibilityScope = contentScope,
                                boundsTransform = MorphTransform,
                            )
                        } else Modifier
                        PhotoTile(
                            photo = t.photo,
                            modifier = Modifier
                                .offset(t.x, t.y)
                                .size(t.size)
                                .then(morph),
                            aspect = 1f,
                            corner = geo.corner,
                            favorite = vm.isFavorite(t.photo.id) && level <= MOSAIC,
                            selectionMode = vm.selecting,
                            selected = t.photo.id in vm.selection,
                            onLongClick = { vm.toggleSelect(t.photo.id) },
                            sharedEnabled = isTarget,
                            onClick = { if (vm.selecting) vm.toggleSelect(t.photo.id) else onPhoto(t.photo.id) },
                        )
                    }
                }
            }
        }
    }
}
