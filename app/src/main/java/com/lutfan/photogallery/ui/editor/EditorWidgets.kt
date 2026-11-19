package com.lutfan.photogallery.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lutfan.photogallery.ui.theme.Palette
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Circular tool button with label, as in the reference design. */
@Composable
fun ToolChip(
    label: String,
    icon: ImageVector?,
    selected: Boolean,
    modifier: Modifier = Modifier,
    dot: Boolean = false,
    size: Dp = 56.dp,
    content: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    Column(
        modifier.width(size + 18.dp).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(size)
                .clip(CircleShape)
                .background(if (selected) Palette.Orange else Palette.CharcoalChip),
            contentAlignment = Alignment.Center,
        ) {
            if (content != null) content()
            else if (icon != null) Icon(icon, label, tint = Color.White, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            label,
            color = if (selected) Palette.Orange else Palette.OnDark,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        Box(
            Modifier
                .padding(top = 4.dp)
                .size(4.dp)
                .clip(CircleShape)
                .background(if (dot) Palette.Orange else Color.Transparent)
        )
    }
}

/**
 * Horizontal degree ruler. Drag to change the value; double-tap resets.
 * Ticks every degree, tall ticks every 5°, triangle marker in the centre.
 */
@Composable
fun AngleRuler(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    range: ClosedFloatingPointRange<Float> = -45f..45f,
) {
    val density = LocalDensity.current
    val pxPerDeg = with(density) { 9.dp.toPx() }
    val current by rememberUpdatedState(value)
    val haptics = LocalHapticFeedback.current
    var accum by remember { mutableStateOf(0f) }

    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("${value.roundToInt()}°", color = Palette.OnDark, style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(6.dp))
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = { onValueChange(0f) })
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { accum = current },
                        onHorizontalDrag = { change, dx ->
                            change.consume()
                            val before = accum.roundToInt()
                            accum = (accum - dx / pxPerDeg).coerceIn(range.start, range.endInclusive)
                            val snapped = if (abs(accum) < 0.6f) 0f else accum
                            if (snapped.roundToInt() != before && snapped.roundToInt() % 5 == 0) {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            onValueChange(snapped)
                        },
                    )
                },
        ) {
            val cx = size.width / 2f
            val tickTop = 4.dp.toPx()
            val baseY = 26.dp.toPx()
            val first = range.start.toInt()
            val last = range.endInclusive.toInt()
            for (d in first..last) {
                val x = cx + (d - value) * pxPerDeg
                if (x < -10f || x > size.width + 10f) continue
                val major = d % 5 == 0
                val len = if (major) 20.dp.toPx() else 12.dp.toPx()
                val edgeFade = 1f - (abs(x - cx) / (size.width / 2f)).coerceIn(0f, 1f) * 0.75f
                val alpha = (if (major) 0.9f else 0.55f) * edgeFade
                drawLine(
                    Color.White.copy(alpha = alpha),
                    Offset(x, baseY - len + tickTop),
                    Offset(x, baseY),
                    strokeWidth = (if (major) 1.6f else 1f).dp.toPx(),
                )
            }
            // zero marker
            val zx = cx + (0 - value) * pxPerDeg
            drawCircle(Palette.Orange, 2.5.dp.toPx(), Offset(zx, tickTop - 1.dp.toPx()))
            // centre triangle
            val tri = Path().apply {
                val y = baseY + 8.dp.toPx()
                moveTo(cx, y)
                lineTo(cx - 6.dp.toPx(), y + 9.dp.toPx())
                lineTo(cx + 6.dp.toPx(), y + 9.dp.toPx())
                close()
            }
            drawPath(tri, Color.White)
        }
    }
}

private enum class Handle { TL, TR, BL, BR, Move, None }

/**
 * Crop rectangle with dimmed outside, rule-of-thirds grid and corner dots.
 * [lockedRatio] is normalised width/height (null = free).
 */
@Composable
fun CropOverlay(
    crop: NormRect,
    lockedRatio: Float?,
    onCropChange: (NormRect) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cropState by rememberUpdatedState(crop)
    val ratioState by rememberUpdatedState(lockedRatio)
    var handle by remember { mutableStateOf(Handle.None) }
    val density = LocalDensity.current
    val touch = with(density) { 36.dp.toPx() }

    Canvas(
        modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { p ->
                        val c = cropState
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        val corners = listOf(
                            Handle.TL to Offset(c.l * w, c.t * h),
                            Handle.TR to Offset(c.r * w, c.t * h),
                            Handle.BL to Offset(c.l * w, c.b * h),
                            Handle.BR to Offset(c.r * w, c.b * h),
                        )
                        val nearest = corners.minBy { (it.second - p).getDistance() }
                        handle = when {
                            (nearest.second - p).getDistance() < touch -> nearest.first
                            Rect(c.l * w, c.t * h, c.r * w, c.b * h).contains(p) -> Handle.Move
                            else -> Handle.None
                        }
                    },
                    onDragEnd = { handle = Handle.None },
                    onDragCancel = { handle = Handle.None },
                    onDrag = { change, drag ->
                        if (handle == Handle.None) return@detectDragGestures
                        change.consume()
                        val dx = drag.x / size.width
                        val dy = drag.y / size.height
                        onCropChange(dragCrop(cropState, handle, dx, dy, ratioState))
                    },
                )
            }
    ) {
        val w = size.width
        val h = size.height
        val r = Rect(crop.l * w, crop.t * h, crop.r * w, crop.b * h)
        val dim = Color.Black.copy(alpha = 0.55f)
        // outside dim (4 rects)
        drawRect(dim, Offset.Zero, Size(w, r.top))
        drawRect(dim, Offset(0f, r.bottom), Size(w, h - r.bottom))
        drawRect(dim, Offset(0f, r.top), Size(r.left, r.height))
        drawRect(dim, Offset(r.right, r.top), Size(w - r.right, r.height))
        // thirds grid
        val gridColor = Color.White.copy(alpha = if (handle != Handle.None) 0.9f else 0.7f)
        for (i in 1..2) {
            val x = r.left + r.width * i / 3f
            val y = r.top + r.height * i / 3f
            drawLine(gridColor, Offset(x, r.top), Offset(x, r.bottom), 1.dp.toPx())
            drawLine(gridColor, Offset(r.left, y), Offset(r.right, y), 1.dp.toPx())
        }
        drawRect(Color.White, r.topLeft, r.size, style = Stroke(1.5.dp.toPx()))
        val dot = 6.dp.toPx()
        listOf(r.topLeft, r.topRight, r.bottomLeft, r.bottomRight).forEach {
            drawCircle(Color.White, dot, it)
        }
    }
}

private const val MIN_SIZE = 0.12f

private fun dragCrop(c: NormRect, handle: Handle, dx: Float, dy: Float, ratio: Float?): NormRect {
    if (handle == Handle.Move) {
        val ndx = dx.coerceIn(-c.l, 1f - c.r)
        val ndy = dy.coerceIn(-c.t, 1f - c.b)
        return NormRect(c.l + ndx, c.t + ndy, c.r + ndx, c.b + ndy)
    }
    // anchor = opposite corner, dir = direction the moving corner extends in
    val (ax, ay, dirX, dirY) = when (handle) {
        Handle.TL -> Quad(c.r, c.b, -1f, -1f)
        Handle.TR -> Quad(c.l, c.b, 1f, -1f)
        Handle.BL -> Quad(c.r, c.t, -1f, 1f)
        else -> Quad(c.l, c.t, 1f, 1f)
    }
    val maxW = if (dirX > 0) 1f - ax else ax
    val maxH = if (dirY > 0) 1f - ay else ay
    var w = c.w + dx * dirX
    var h = c.h + dy * dirY
    if (ratio == null) {
        w = w.coerceIn(MIN_SIZE, maxW)
        h = h.coerceIn(MIN_SIZE, maxH)
    } else {
        w = (w + h * ratio) / 2f
        h = w / ratio
        val minK = max(MIN_SIZE / w, MIN_SIZE / h)
        if (minK > 1f) { w *= minK; h *= minK }
        val k = min(1f, min(maxW / w, maxH / h))
        w *= k; h *= k
    }
    val l = if (dirX > 0) ax else ax - w
    val t = if (dirY > 0) ay else ay - h
    return NormRect(l, t, l + w, t + h)
}

private data class Quad(val a: Float, val b: Float, val c: Float, val d: Float)
