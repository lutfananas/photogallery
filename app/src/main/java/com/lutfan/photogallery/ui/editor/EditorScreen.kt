package com.lutfan.photogallery.ui.editor

import android.content.ContentUris
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Compare
import androidx.compose.material.icons.outlined.Crop169
import androidx.compose.material.icons.outlined.CropFree
import androidx.compose.material.icons.outlined.CropLandscape
import androidx.compose.material.icons.outlined.CropPortrait
import androidx.compose.material.icons.outlined.CropSquare
import androidx.compose.material.icons.outlined.Flip
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Rotate90DegreesCcw
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.ui.graphics.asComposeRenderEffect
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lutfan.photogallery.GalleryViewModel
import com.lutfan.photogallery.ui.theme.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val PREVIEW_MAX = 1600
private const val EXPORT_MAX = 4096

@Composable
fun EditorScreen(
    vm: GalleryViewModel,
    photoId: Long,
    onClose: () -> Unit,
    onSaved: (Long?) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val photo = remember(photoId) { vm.photo(photoId) }

    var source by remember { mutableStateOf<Bitmap?>(null) }
    var loadError by remember { mutableStateOf(false) }
    LaunchedEffect(photo?.uri) {
        val uri = photo?.uri ?: run { loadError = true; return@LaunchedEffect }
        source = runCatching {
            withContext(Dispatchers.IO) { ImageOps.decode(context, uri, PREVIEW_MAX) }
        }.onFailure { loadError = true }.getOrNull()
    }

    var state by remember { mutableStateOf(EditState()) }
    var tab by remember { mutableStateOf(EditTab.Crop) }
    var tool by remember { mutableStateOf(AdjustTool.Brightness) }
    var comparing by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }

    // Markup tool settings.
    var brush by remember { mutableStateOf(BrushMode.Pen) }
    var brushColor by remember { mutableStateOf(Palette.Orange) }
    var brushWidth by remember { mutableStateOf(0.012f) }
    val strokeRedo = remember { mutableStateListOf<MarkStroke>() }

    // Global undo/redo: a snapshot is committed once edits settle for a moment.
    val undoStack = remember { mutableStateListOf<EditState>() }
    val redoStack = remember { mutableStateListOf<EditState>() }
    var committed by remember { mutableStateOf(EditState()) }
    LaunchedEffect(Unit) {
        @OptIn(FlowPreview::class)
        snapshotFlow { state }.debounce(300).collect { s ->
            if (s != committed) {
                undoStack.add(committed)
                if (undoStack.size > 50) undoStack.removeAt(0)
                redoStack.clear()
                committed = s
            }
        }
    }
    fun undo() {
        val prev = undoStack.removeLastOrNull() ?: return
        redoStack.add(state); committed = prev; state = prev
    }
    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.add(state); committed = next; state = next
    }

    val requestClose = { if (state.isEdited) confirmDiscard = true else onClose() }
    BackHandler(enabled = !saving) { requestClose() }

    // Rotated/flipped preview base.
    val base = remember(source, state.rotation, state.flipH) {
        source?.let { ImageOps.rotateFlip(it, state.rotation, state.flipH) }
    }
    // Straightened + cropped preview for Adjust / Filters (colour applied live via ColorFilter).
    val geometry by produceState<Bitmap?>(null, base, state.straighten, state.crop, tab) {
        val b = base ?: return@produceState
        if (tab == EditTab.Crop && value != null) return@produceState
        val geomOnly = state
        value = withContext(Dispatchers.Default) { ImageOps.render(b, geomOnly) }
    }

    fun save() {
        val p = photo ?: return
        saving = true
        scope.launch {
            val newId = runCatching {
                val snapshot = state
                val bitmap = withContext(Dispatchers.Default) {
                    ImageOps.export(ImageOps.decode(context, p.uri, EXPORT_MAX), snapshot)
                }
                val uri = vm.repo.saveEdited(bitmap, p.name) ?: error("save failed")
                vm.reloadNow()
                ContentUris.parseId(uri)
            }.getOrNull()
            saving = false
            if (newId == null) {
                Toast.makeText(context, "Couldn't save the photo", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Saved a copy to Pictures/PhotoGallery", Toast.LENGTH_SHORT).show()
                onSaved(newId)
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Palette.Charcoal)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // ---- top bar ----
        Row(
            Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = requestClose) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = Palette.OnDark)
            }
            Text(
                tab.title,
                color = Palette.OnDark,
                fontSize = 18.sp,
                maxLines = 1,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            IconButton(onClick = ::undo, enabled = undoStack.isNotEmpty()) {
                Icon(
                    Icons.AutoMirrored.Outlined.Undo, "Undo",
                    tint = if (undoStack.isNotEmpty()) Palette.OnDark else Palette.OnDarkMuted.copy(alpha = .35f),
                )
            }
            IconButton(onClick = ::redo, enabled = redoStack.isNotEmpty()) {
                Icon(
                    Icons.AutoMirrored.Outlined.Redo, "Redo",
                    tint = if (redoStack.isNotEmpty()) Palette.OnDark else Palette.OnDarkMuted.copy(alpha = .35f),
                )
            }
            Box(
                Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .pointerInput(Unit) {
                        detectTapGestures(onPress = {
                            comparing = true
                            tryAwaitRelease()
                            comparing = false
                        })
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Compare, "Hold to compare", tint = if (comparing) Palette.Orange else Palette.OnDark)
            }
        }

        // ---- canvas ----
        Box(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 28.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            when {
                loadError -> Text("Couldn't open this photo", color = Palette.OnDarkMuted)
                source == null -> CircularProgressIndicator(color = Palette.Orange)
                comparing -> FittedImage(source!!)
                tab == EditTab.Crop && base != null -> CropCanvas(base, state) { state = it }
                geometry != null -> EffectImage(
                    bitmap = geometry!!,
                    state = { state },
                    markupEnabled = tab == EditTab.Markup,
                    brush = brush,
                    brushColor = brushColor,
                    brushWidth = brushWidth,
                    onStroke = {
                        strokeRedo.clear()
                        state = state.copy(strokes = state.strokes + it)
                    },
                )
                else -> CircularProgressIndicator(color = Palette.Orange)
            }
            if (saving) {
                Box(
                    Modifier.fillMaxSize().background(Palette.Charcoal.copy(alpha = .6f)),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator(color = Palette.Orange) }
            }
        }

        // ---- tool panel ----
        val panelHeight by animateDpAsState(
            when (tab) {
                EditTab.Curves -> 268.dp
                EditTab.Markup -> 228.dp
                else -> 196.dp
            },
            label = "panelHeight",
        )
        AnimatedContent(
            targetState = tab,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "panel",
            modifier = Modifier.fillMaxWidth().height(panelHeight),
        ) { t ->
            when (t) {
                EditTab.Crop -> CropPanel(state, base) { state = it }
                EditTab.Adjust -> AdjustPanel(state, tool, onTool = { tool = it }) { state = it }
                EditTab.Filters -> FilterPanel(state, geometry) { state = it }
                EditTab.Curves -> CurvesPanel(state.curves) { state = state.copy(curves = it) }
                EditTab.Markup -> MarkupPanel(
                    brush = brush, onBrush = { brush = it },
                    color = brushColor, onColor = { brushColor = it },
                    width = brushWidth, onWidth = { brushWidth = it },
                    canUndo = state.strokes.isNotEmpty(),
                    canRedo = strokeRedo.isNotEmpty(),
                    onUndo = {
                        state.strokes.lastOrNull()?.let { strokeRedo.add(it) }
                        state = state.copy(strokes = state.strokes.dropLast(1))
                    },
                    onRedo = {
                        strokeRedo.removeLastOrNull()?.let { state = state.copy(strokes = state.strokes + it) }
                    },
                )
            }
        }

        HorizontalDivider(color = Color.White.copy(alpha = .08f))

        // ---- bottom tabs ----
        Row(
            Modifier.fillMaxWidth().height(76.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = requestClose) { Icon(Icons.Outlined.Close, "Cancel", tint = Palette.OnDark) }
            Row(
                Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.Center,
            ) {
                EditTab.entries.forEach { t ->
                    Text(
                        t.label,
                        color = if (t == tab) Palette.Orange else Palette.OnDark,
                        fontSize = 16.sp,
                        fontWeight = if (t == tab) FontWeight.Medium else FontWeight.Normal,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { tab = t }
                            .padding(horizontal = 9.dp, vertical = 8.dp),
                    )
                }
            }
            IconButton(onClick = { if (!saving) save() }, enabled = photo != null && source != null) {
                Icon(Icons.Outlined.Check, "Save copy", tint = Palette.OnDark)
            }
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard edits?") },
            text = { Text("Your changes to this photo won't be saved.") },
            confirmButton = {
                TextButton(onClick = { confirmDiscard = false; onClose() }) { Text("Discard", color = Palette.Orange) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) { Text("Keep editing", color = Palette.Ink) }
            },
            containerColor = Color.White,
        )
    }
}

/* ---------------------------------------------------------------- canvas */

/** Box sized to the largest rect of [aspect] that fits the incoming constraints. */
@Composable
private fun AspectFit(aspect: Float, content: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val boxRatio = maxWidth / maxHeight
        val (w, h) = if (aspect > boxRatio) maxWidth to maxWidth / aspect else maxHeight * aspect to maxHeight
        Box(Modifier.size(w, h)) { content() }
    }
}

@Composable
private fun FittedImage(bitmap: Bitmap) {
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    AspectFit(bitmap.width.toFloat() / bitmap.height) {
        Image(image, null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
    }
}

/**
 * The live preview: the colour pipeline runs as a RenderEffect on the image's
 * layer, so slider changes only touch GPU uniforms (no recomposition of the image).
 */
@Composable
private fun EffectImage(
    bitmap: Bitmap,
    state: () -> EditState,
    markupEnabled: Boolean,
    brush: BrushMode,
    brushColor: Color,
    brushWidth: Float,
    onStroke: (MarkStroke) -> Unit,
) {
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    val shader = remember { GpuEffects.newShader() }
    val curves = state().curves
    val lut = remember(curves) { GpuEffects.lutShader(curves.lutBitmap()) }
    AspectFit(bitmap.width.toFloat() / bitmap.height) {
        Image(
            image, null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                val s = state()
                renderEffect = if (!s.hasColor) null
                else GpuEffects.effect(shader, s, lut, size.width, size.height).asComposeRenderEffect()
            },
        )
        MarkupLayer(
            strokes = state().strokes,
            enabled = markupEnabled,
            brush = brush,
            color = brushColor,
            width = brushWidth,
            onStroke = onStroke,
        )
    }
}

@Composable
private fun CropCanvas(base: Bitmap, state: EditState, onChange: (EditState) -> Unit) {
    val image = remember(base) { base.asImageBitmap() }
    val frameRatio = base.width.toFloat() / base.height
    val scale = ImageOps.straightenScale(state.straighten, base.width.toFloat(), base.height.toFloat())
    AspectFit(frameRatio) {
        Box(Modifier.fillMaxSize().clipToBounds()) {
            Image(
                image, null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    rotationZ = state.straighten
                    scaleX = scale; scaleY = scale
                },
            )
        }
        CropOverlay(
            crop = state.crop,
            lockedRatio = lockedNormRatio(state.aspect, base),
            onCropChange = { onChange(state.copy(crop = it)) },
        )
    }
}

private fun lockedNormRatio(aspect: Aspect, base: Bitmap?): Float? {
    val r = aspect.ratio ?: return null
    base ?: return null
    val frame = base.width.toFloat() / base.height
    return if (r < 0f) 1f else r / frame
}

/* ---------------------------------------------------------------- panels */

private val aspectIcons = mapOf(
    Aspect.Free to Icons.Outlined.CropFree,
    Aspect.Original to Icons.Outlined.Image,
    Aspect.Square to Icons.Outlined.CropSquare,
    Aspect.R2x3 to Icons.Outlined.CropPortrait,
    Aspect.R3x2 to Icons.Outlined.CropLandscape,
    Aspect.R4x5 to Icons.Outlined.CropPortrait,
    Aspect.R16x9 to Icons.Outlined.Crop169,
)

@Composable
private fun CropPanel(state: EditState, base: Bitmap?, onChange: (EditState) -> Unit) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
        AngleRuler(
            value = state.straighten,
            onValueChange = { onChange(state.copy(straighten = it)) },
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item {
                ToolChip("Rotate", Icons.Outlined.Rotate90DegreesCcw, selected = false) {
                    // Rotating swaps the frame; re-apply the aspect so the crop stays valid.
                    val next = state.copy(rotation = (state.rotation + 270) % 360, crop = NormRect.Full)
                    onChange(withAspect(next, next.aspect, base, rotated = true))
                }
            }
            item {
                ToolChip("Flip", Icons.Outlined.Flip, selected = state.flipH) {
                    val c = state.crop
                    onChange(state.copy(flipH = !state.flipH, straighten = -state.straighten, crop = NormRect(1f - c.r, c.t, 1f - c.l, c.b)))
                }
            }
            items(Aspect.entries) { a ->
                ToolChip(a.label, aspectIcons[a], selected = state.aspect == a) {
                    onChange(withAspect(state, a, base, rotated = false))
                }
            }
        }
    }
}

/** Applies an aspect preset: the largest centred crop with that ratio. */
private fun withAspect(state: EditState, aspect: Aspect, base: Bitmap?, rotated: Boolean): EditState {
    val r = aspect.ratio ?: return state.copy(aspect = aspect)
    if (base == null) return state.copy(aspect = aspect)
    // When we just rotated, `base` is still the pre-rotation bitmap: swap its ratio.
    val frame = if (rotated) base.height.toFloat() / base.width else base.width.toFloat() / base.height
    val target = if (r < 0f) frame else r
    return state.copy(aspect = aspect, crop = ImageOps.centeredCrop(target, frame))
}

@Composable
private fun AdjustPanel(
    state: EditState,
    tool: AdjustTool,
    onTool: (AdjustTool) -> Unit,
    onChange: (EditState) -> Unit,
) {
    val value = state.value(tool)
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
        Column(Modifier.padding(horizontal = 28.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tool.label, color = Palette.OnDark, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                Text(
                    (if (value > 0 && tool.min < 0) "+" else "") + value.roundToInt(),
                    color = if (value != 0f) Palette.Orange else Palette.OnDarkMuted,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.clickable { onChange(state.copy(adjust = state.adjust - tool)) },
                )
            }
            EditorSlider(value, tool.min..100f) { v ->
                onChange(state.copy(adjust = state.adjust + (tool to v)))
            }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item {
                val auto = state.adjust == AUTO
                ToolChip("Auto", Icons.Outlined.AutoFixHigh, selected = auto) {
                    onChange(state.copy(adjust = if (auto) emptyMap() else AUTO))
                }
            }
            items(AdjustTool.entries) { t ->
                ToolChip(t.label, t.icon, selected = t == tool, dot = state.value(t) != 0f) { onTool(t) }
            }
        }
    }
}

private val AUTO = mapOf(
    AdjustTool.Brightness to 6f,
    AdjustTool.Contrast to 14f,
    AdjustTool.Saturation to 12f,
    AdjustTool.Warmth to 4f,
)

@Composable
private fun FilterPanel(state: EditState, geometry: Bitmap?, onChange: (EditState) -> Unit) {
    val thumb = remember(geometry) {
        geometry?.let {
            val k = 180f / max(it.width, it.height)
            Bitmap.createScaledBitmap(it, max(1, (it.width * k).roundToInt()), max(1, (it.height * k).roundToInt()), true)
                .asImageBitmap()
        }
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
        Box(Modifier.padding(horizontal = 28.dp).height(48.dp), contentAlignment = Alignment.Center) {
            if (state.filter != Filter.Original) {
                Column {
                    Row {
                        Text("Intensity", color = Palette.OnDark, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                        Text("${(state.filterStrength * 100).roundToInt()}", color = Palette.Orange, style = MaterialTheme.typography.labelLarge)
                    }
                    EditorSlider(state.filterStrength * 100f, 0f..100f) { onChange(state.copy(filterStrength = it / 100f)) }
                }
            }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(Filter.entries) { f ->
                val selected = state.filter == f
                Column(
                    Modifier.width(76.dp).clickable { onChange(state.copy(filter = f, filterStrength = 1f)) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Palette.CharcoalChip)
                            .then(if (selected) Modifier.border(2.dp, Palette.Orange, RoundedCornerShape(16.dp)) else Modifier)
                    ) {
                        if (thumb != null) {
                            val shader = remember { GpuEffects.newShader() }
                            val lut = remember { GpuEffects.lutShader(Curves().lutBitmap()) }
                            val preview = remember(f) { EditState(filter = f) }
                            Image(
                                thumb, f.label,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize().graphicsLayer {
                                    renderEffect = if (f == Filter.Original) null
                                    else GpuEffects.effect(shader, preview, lut, size.width, size.height).asComposeRenderEffect()
                                },
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        f.label,
                        color = if (selected) Palette.Orange else Palette.OnDark,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun EditorSlider(value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Slider(
        value = value.coerceIn(range.start, range.endInclusive),
        onValueChange = { v -> onChange(if (range.start < 0f && kotlin.math.abs(v) < 2f) 0f else v) },
        valueRange = range,
        colors = SliderDefaults.colors(
            thumbColor = Palette.Orange,
            activeTrackColor = Palette.Orange,
            inactiveTrackColor = Color.White.copy(alpha = .18f),
        ),
    )
}
