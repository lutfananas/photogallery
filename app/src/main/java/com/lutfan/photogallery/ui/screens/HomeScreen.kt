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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.util.lerp
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.Color
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.lutfan.photogallery.ui.thumbCacheKey
import com.lutfan.photogallery.GalleryViewModel
import com.lutfan.photogallery.data.Photo
import com.lutfan.photogallery.ui.LocalSharedScope
import com.lutfan.photogallery.ui.components.CircleThumb
import com.lutfan.photogallery.ui.components.EmptyState
import com.lutfan.photogallery.ui.components.PhotoTile
import com.lutfan.photogallery.ui.theme.Palette
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.ln
import kotlin.math.roundToInt
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
    val headerInset: Dp = if (level > MOSAIC) 12.dp else 0.dp

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

/* ------------------------------------------------------------------ pixel layout per level */

/** Every photo/header position for one level, in px, relative to the start of the photo section. */
private class LevelLayout(val level: Int, val geo: Geometry, val rows: List<GridRow>, density: androidx.compose.ui.unit.Density) {
    val rowTop = FloatArray(rows.size + 1)
    val tiles = HashMap<Long, Rect>()
    val headers = HashMap<Any, Pair<Float, MonthHeader>>()
    val cellPx: Float
    val cornerPx: Float
    val sidePx: Float
    val headerInsetPx: Float

    init {
        with(density) {
            cellPx = geo.cell.toPx()
            cornerPx = geo.corner.toPx()
            sidePx = geo.side.toPx()
            headerInsetPx = (geo.side + geo.headerInset).toPx()
            var y = 0f
            rows.forEachIndexed { i, row ->
                rowTop[i] = y
                when (row) {
                    is MonthHeader -> headers[row.key] = y to row
                    else -> geo.tiles(row).forEach { t ->
                        val l = sidePx + t.x.toPx()
                        val top = y + t.y.toPx()
                        val sz = t.size.toPx()
                        tiles[t.photo.id] = Rect(l, top, l + sz, top + sz)
                    }
                }
                y += geo.height(row).toPx()
            }
            rowTop[rows.size] = y
        }
    }

    val total get() = rowTop[rows.size]

    /** Index of the row containing section-relative y. */
    fun rowAt(y: Float): Int {
        var lo = 0
        var hi = rows.size - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (rowTop[mid] <= y) lo = mid else hi = mid - 1
        }
        return lo.coerceAtLeast(0)
    }
}

/** A live transition between two levels (A = from, B = to). */
private class Morph(val a: LevelLayout, val b: LevelLayout, val photos: List<Photo>, val headers: List<Any>)

private fun <T> settleSpring() = spring<T>(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow)

/* ------------------------------------------------------------------ screen */

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
    val listState = rememberLazyListState()
    val topItems = 2 + (if (partialAccess) 1 else 0) // title, albums, (banner)

    // ---- live morph state (read only in layout/draw lambdas → no per-frame recomposition)
    var morph by remember { mutableStateOf<Morph?>(null) }
    var t by remember { mutableFloatStateOf(0f) }
    var sA by remember { mutableFloatStateOf(0f) }   // screen y of photo section in level A
    var sB by remember { mutableFloatStateOf(0f) }   // ... in level B
    var sList by remember { mutableFloatStateOf(0f) } // section y of the (frozen) list when the pinch began
    var rubber by remember { mutableFloatStateOf(1f) }
    var pivot by remember { mutableStateOf(Offset.Zero) }
    var settle by remember { mutableStateOf<Job?>(null) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var topH by remember { mutableFloatStateOf(with(density) { 300.dp.toPx() }) }

    BoxWithConstraints(Modifier.fillMaxSize().background(Palette.Paper)) {
        val width = maxWidth
        val layouts = remember(vm.photos, width, density) { HashMap<Int, LevelLayout>() }
        fun layoutOf(l: Int) = layouts.getOrPut(l) { LevelLayout(l, Geometry(l, width), buildRows(vm.photos, l), density) }
        val bottomPad = with(density) { 130.dp.toPx() }

        // ---- gesture bookkeeping (plain vars, only touched from the gesture coroutine)
        val g = remember { object {
            var base: LevelLayout? = null
            var anchor: Long = -1
            var frac = 0f
            var acc = 1f
        } }

        fun clampS(s: Float, lay: LevelLayout): Float {
            val min = viewport.height - bottomPad - lay.total
            return if (min > topH) topH else s.coerceIn(min, topH)
        }

        fun sectionYOf(lay: LevelLayout): Float? {
            val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index >= topItems && it.index - topItems < lay.rows.size }
                ?: return null
            return info.offset - lay.rowTop[info.index - topItems]
        }

        fun sFor(lay: LevelLayout): Float {
            val r = lay.tiles[g.anchor] ?: return sA
            return clampS(pivot.y - (r.top + g.frac * r.height), lay)
        }

        fun buildMorph(a: LevelLayout, b: LevelLayout): Morph {
            val h = viewport.height.toFloat()
            val margin = h * 0.35f
            val estB = sFor(b)
            fun visible(r: Rect, s: Float) = r.bottom + s > -margin && r.top + s < h + margin
            val ids = LinkedHashSet<Long>()
            a.tiles.forEach { (id, r) -> if (visible(r, sA)) ids += id }
            b.tiles.forEach { (id, r) -> if (visible(r, estB)) ids += id }
            val byId = vm.photos.associateBy { it.id }
            val heads = a.headers.keys.filter { k ->
                val ya = a.headers[k]!!.first + sA
                val yb = (b.headers[k]?.first ?: 0f) + estB
                (ya > -margin && ya < h + margin) || (yb > -margin && yb < h + margin)
            }
            return Morph(a, b, ids.mapNotNull { byId[it] }, heads)
        }

        /** Hands the result back to the real list, positioned exactly where the morph ended. */
        fun finish(toB: Boolean) {
            val m = morph
            val final = if (m != null && toB) m.b else g.base ?: layoutOf(level)
            val s = if (m != null && toB) sB else sA
            if (final.level != level) level = final.level
            if (s > 0f) {
                listState.requestScrollToItem(0, (topH - s).roundToInt().coerceAtLeast(0))
            } else {
                val r = final.rowAt(-s)
                listState.requestScrollToItem(topItems + r, (-s - final.rowTop[r]).roundToInt())
            }
            morph = null
            t = 0f
            g.base = null
        }

        fun begin(): Boolean {
            settle?.let { if (it.isActive) { it.cancel(); finish(t > 0.5f) } }
            val lay = layoutOf(level)
            val s0 = sectionYOf(lay) ?: return false
            // Height of the content above the photos, used to clamp at the top.
            val vis = listState.layoutInfo.visibleItemsInfo
            if (vis.firstOrNull()?.index == 0) {
                vis.firstOrNull { it.index == topItems }?.let { topH = it.offset.toFloat() + listState.firstVisibleItemScrollOffset }
            }
            sA = s0; sList = s0
            g.base = lay
            g.acc = 1f
            // Photo under the fingers (or the nearest one in that row).
            val y = pivot.y - s0
            var r = lay.rowAt(y)
            if (lay.rows[r] is MonthHeader && r + 1 < lay.rows.size) r++
            val inRow = lay.geo.tiles(lay.rows[r]).map { it.photo.id }
            val id = inRow.firstOrNull { lay.tiles[it]!!.contains(Offset(pivot.x, y)) }
                ?: inRow.minByOrNull { kotlin.math.abs(lay.tiles[it]!!.center.x - pivot.x) }
                ?: return false
            val rect = lay.tiles[id]!!
            g.anchor = id
            g.frac = ((y - rect.top) / rect.height).coerceIn(0f, 1f)
            return true
        }

        fun update(zoom: Float) {
            val base = g.base ?: return
            g.acc *= zoom
            val tgt = when {
                g.acc > 1f && base.level > MIN_LEVEL -> base.level - 1
                g.acc < 1f && base.level < MAX_LEVEL -> base.level + 1
                else -> null
            }
            if (tgt == null) {
                // At the ends: a little rubber-band instead.
                rubber = (1f + (g.acc - 1f) * 0.25f).coerceIn(0.95f, 1.05f)
                t = 0f
                morph = if (base.level != level) buildMorph(base, base) else null
                return
            }
            rubber = 1f
            val b = layoutOf(tgt)
            if (morph?.b !== b || morph?.a !== base) morph = buildMorph(base, b)
            val ratio = b.cellPx / base.cellPx
            var tt = ln(g.acc) / ln(ratio)
            sB = sFor(b)
            if (tt >= 1f) {
                // Passed the next level: continue seamlessly from there.
                g.base = b
                sA = sB
                g.acc /= ratio
                tt = 0f
                morph = buildMorph(b, b) // keep drawing the overlay; no flash of the old list
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
            t = tt.coerceIn(0f, 1f)
        }

        fun release() {
            val start = t
            val toB = morph != null && start > 0.35f
            if (morph == null) {
                // Ended exactly on a level (or at an end).
                if (g.base != null && g.base!!.level != level) finish(false) else { g.base = null }
            }
            if (rubber != 1f) {
                val r0 = rubber
                scope.launch { animate(r0, 1f, animationSpec = settleSpring()) { v, _ -> rubber = v } }
            }
            if (morph == null) return
            settle = scope.launch {
                animate(start, if (toB) 1f else 0f, animationSpec = settleSpring()) { v, _ -> t = v }
                if (toB) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                finish(toB)
            }
        }

        val beginState by rememberUpdatedState(::begin)
        val updateState by rememberUpdatedState(::update)
        val releaseState by rememberUpdatedState(::release)

        Box(
            Modifier
                .fillMaxSize()
                .onSizeChanged { viewport = it }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        var pinching = false
                        do {
                            // Initial pass: see two-finger gestures before the list scrolls.
                            val ev = awaitPointerEvent(PointerEventPass.Initial)
                            val pts = ev.changes.filter { it.pressed && it.previousPressed }
                            if (pts.size >= 2) {
                                val c = ev.calculateCentroid(useCurrent = true)
                                if (c.isSpecified) pivot = c
                                if (!pinching) {
                                    pinching = beginState()
                                    if (!pinching) { ev.changes.forEach { it.consume() }; continue }
                                }
                                val zoom = ev.calculateZoom()
                                if (zoom.isFinite() && zoom > 0f) updateState(zoom)
                                ev.changes.forEach { it.consume() }
                            } else if (pinching) {
                                ev.changes.forEach { it.consume() }
                            }
                        } while (ev.changes.any { it.pressed })
                        if (pinching) releaseState()
                    }
                }
                .graphicsLayer {
                    scaleX = rubber; scaleY = rubber
                    if (size.width > 0f) {
                        transformOrigin = TransformOrigin(
                            (pivot.x / size.width).coerceIn(0f, 1f),
                            (pivot.y / size.height).coerceIn(0f, 1f),
                        )
                    }
                }
        ) {
            PhotoList(
                vm = vm,
                layout = layoutOf(level),
                state = listState,
                hideRows = { morph != null },
                shift = { if (morph == null) 0f else lerp(sA, sB, t) - sList },
                partialAccess = partialAccess,
                onManageAccess = onManageAccess,
                onSearch = onSearch,
                onAlbum = onAlbum,
                onSeeMore = onSeeMore,
                onPhoto = onPhoto,
            )
            morph?.let { m -> MorphLayer(m, t = { t }, sA = { sA }, sB = { sB }) }
        }
    }
}

/** Draws every involved photo/header between its A and B rect; all motion is in the draw phase. */
@Composable
private fun MorphLayer(m: Morph, t: () -> Float, sA: () -> Float, sB: () -> Float) {
    val context = LocalContext.current
    Box(Modifier.fillMaxSize()) {
        m.headers.forEach { k ->
            val (ya, h) = m.a.headers[k] ?: return@forEach
            val yb = m.b.headers[k]?.first ?: ya
            Row(
                Modifier
                    .height(Geometry(m.a.level, 0.dp).headerHeight)
                    .layout { measurable, constraints ->
                        // Few headers: animating their inset in layout is cheap.
                        val inset = lerp(m.a.headerInsetPx, m.b.headerInsetPx, t()).roundToInt()
                        val w = (constraints.maxWidth - inset * 2).coerceAtLeast(0)
                        val pl = measurable.measure(constraints.copy(minWidth = w, maxWidth = w))
                        layout(constraints.maxWidth, pl.height) { pl.place(inset, 0) }
                    }
                    .graphicsLayer { translationY = lerp(ya + sA(), yb + sB(), t()) }
                    .padding(bottom = 10.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(h.label, fontSize = 18.sp, fontWeight = FontWeight.Medium, color = Palette.Ink, modifier = Modifier.weight(1f))
                Text("${h.count} photos", style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
            }
        }
        m.photos.forEach { p ->
            val ra = m.a.tiles[p.id] ?: return@forEach
            val rb = m.b.tiles[p.id] ?: return@forEach
            // Measure once at the larger size; scale down in the layer (cheap per frame).
            val base = maxOf(ra.width, rb.width)
            val request = remember(p.id) {
                ImageRequest.Builder(context).data(p.uri).memoryCacheKey(thumbCacheKey(p.id)).size(720).build()
            }
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .layout { measurable, _ ->
                        val px = base.roundToInt()
                        val pl = measurable.measure(Constraints.fixed(px, px))
                        layout(px, px) { pl.place(0, 0) }
                    }
                    .graphicsLayer {
                        val tt = t()
                        val w = lerp(ra.width, rb.width, tt)
                        val k = w / base
                        transformOrigin = TransformOrigin(0f, 0f)
                        scaleX = k; scaleY = k
                        translationX = lerp(ra.left, rb.left, tt)
                        translationY = lerp(ra.top + sA(), rb.top + sB(), tt)
                        shape = RoundedCornerShape(lerp(m.a.cornerPx, m.b.cornerPx, tt) / k)
                        clip = true
                    }
                    .background(Palette.Line),
            )
        }
    }
}

@Composable
private fun PhotoList(
    vm: GalleryViewModel,
    layout: LevelLayout,
    state: LazyListState,
    hideRows: () -> Boolean,
    shift: () -> Float,
    partialAccess: Boolean,
    onManageAccess: () -> Unit,
    onSearch: () -> Unit,
    onAlbum: (Long) -> Unit,
    onSeeMore: () -> Unit,
    onPhoto: (Long) -> Unit,
) {
    val geo = layout.geo
    val level = layout.level
    val rows = layout.rows

    LazyColumn(
        state = state,
        modifier = Modifier.fillMaxSize().graphicsLayer { translationY = shift() },
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

        items(rows, key = { "$level-${it.key}" }, contentType = { it::class }) { row ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(geo.height(row))
                    .graphicsLayer { alpha = if (hideRows()) 0f else 1f }
                    .padding(horizontal = geo.side)
            ) {
                when (row) {
                    is MonthHeader -> Row(
                        Modifier.fillMaxSize().padding(start = geo.headerInset, end = geo.headerInset, bottom = 10.dp),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        Text(row.label, fontSize = 18.sp, fontWeight = FontWeight.Medium, color = Palette.Ink, modifier = Modifier.weight(1f))
                        Text("${row.count} photos", style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
                    }
                    else -> geo.tiles(row).forEach { tile ->
                        PhotoTile(
                            photo = tile.photo,
                            modifier = Modifier.offset(tile.x, tile.y).size(tile.size),
                            aspect = 1f,
                            corner = geo.corner,
                            favorite = vm.isFavorite(tile.photo.id) && level <= MOSAIC,
                            selectionMode = vm.selecting,
                            selected = tile.photo.id in vm.selection,
                            onLongClick = { vm.toggleSelect(tile.photo.id) },
                            onClick = { if (vm.selecting) vm.toggleSelect(tile.photo.id) else onPhoto(tile.photo.id) },
                        )
                    }
                }
            }
        }
    }
}
