package com.lutfan.photogallery.ui.editor

import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BorderColor
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.HorizontalRule
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.lutfan.photogallery.ui.theme.Palette
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

enum class BrushMode(val label: String, val icon: ImageVector, val isShape: Boolean = false) {
    Pen("Pen", Icons.Outlined.Draw),
    Highlighter("Highlight", Icons.Outlined.BorderColor),
    Neon("Neon", Icons.Outlined.AutoAwesome),
    Line("Line", Icons.Outlined.HorizontalRule, isShape = true),
    Arrow("Arrow", Icons.AutoMirrored.Outlined.ArrowForward, isShape = true),
    Rect("Rect", Icons.Outlined.CheckBoxOutlineBlank, isShape = true),
    Oval("Oval", Icons.Outlined.Circle, isShape = true),
    Eraser("Eraser", Icons.Outlined.CleaningServices),
}

/** Points are normalised to the (cropped) image; [width] is a fraction of image width. */
data class MarkStroke(
    val mode: BrushMode,
    val color: Int,
    val width: Float,
    val points: List<Offset>,
)

val markupColors = listOf(
    Color.White, Color(0xFF1E1E1E), Palette.Orange, Color(0xFFFF4D4D), Color(0xFFFFD43B),
    Color(0xFF51CF66), Color(0xFF339AF0), Color(0xFF845EF7), Color(0xFFFF6FB5),
)

/** Draws strokes with android.graphics so preview and export share one code path. */
object MarkupRenderer {
    fun draw(canvas: android.graphics.Canvas, strokes: List<MarkStroke>, w: Float, h: Float) {
        if (strokes.isEmpty()) return
        val layer = canvas.saveLayer(0f, 0f, w, h, null)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
        }
        for (s in strokes) {
            if (s.points.isEmpty()) continue
            val sw = max(1f, s.width * w)
            paint.reset(); paint.isAntiAlias = true
            paint.style = Paint.Style.STROKE
            paint.strokeJoin = Paint.Join.ROUND
            paint.strokeCap = Paint.Cap.ROUND
            paint.color = s.color
            paint.strokeWidth = sw
            val path = pathOf(s, w, h)
            when (s.mode) {
                BrushMode.Highlighter -> {
                    paint.alpha = 110
                    paint.strokeCap = Paint.Cap.SQUARE
                    paint.strokeWidth = sw * 2.4f
                    canvas.drawPath(path, paint)
                }
                BrushMode.Neon -> {
                    paint.strokeWidth = sw * 1.8f
                    paint.maskFilter = BlurMaskFilter(sw * 1.4f, BlurMaskFilter.Blur.NORMAL)
                    canvas.drawPath(path, paint)
                    paint.maskFilter = null
                    paint.color = android.graphics.Color.WHITE
                    paint.strokeWidth = sw * 0.45f
                    canvas.drawPath(path, paint)
                }
                BrushMode.Eraser -> {
                    paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
                    paint.strokeWidth = sw * 2.5f
                    canvas.drawPath(path, paint)
                }
                else -> canvas.drawPath(path, paint)
            }
        }
        canvas.restoreToCount(layer)
    }

    private fun pathOf(s: MarkStroke, w: Float, h: Float): android.graphics.Path {
        val p = android.graphics.Path()
        val pts = s.points.map { Offset(it.x * w, it.y * h) }
        val a = pts.first()
        val b = pts.last()
        when (s.mode) {
            BrushMode.Line -> { p.moveTo(a.x, a.y); p.lineTo(b.x, b.y) }
            BrushMode.Arrow -> {
                p.moveTo(a.x, a.y); p.lineTo(b.x, b.y)
                val ang = atan2(b.y - a.y, b.x - a.x)
                val head = max(s.width * w * 4f, 24f)
                for (side in listOf(-1, 1)) {
                    val t = ang + Math.PI.toFloat() + side * 0.5f
                    p.moveTo(b.x, b.y)
                    p.lineTo(b.x + cos(t) * head, b.y + sin(t) * head)
                }
            }
            BrushMode.Rect -> p.addRect(RectF(minOf(a.x, b.x), minOf(a.y, b.y), maxOf(a.x, b.x), maxOf(a.y, b.y)), android.graphics.Path.Direction.CW)
            BrushMode.Oval -> p.addOval(RectF(minOf(a.x, b.x), minOf(a.y, b.y), maxOf(a.x, b.x), maxOf(a.y, b.y)), android.graphics.Path.Direction.CW)
            else -> {
                p.moveTo(a.x, a.y)
                if (pts.size == 1) p.lineTo(a.x + 0.1f, a.y)
                // Smooth freehand strokes through midpoints.
                for (i in 1 until pts.size) {
                    val prev = pts[i - 1]
                    val cur = pts[i]
                    p.quadTo(prev.x, prev.y, (prev.x + cur.x) / 2f, (prev.y + cur.y) / 2f)
                }
                if (pts.size > 1) p.lineTo(b.x, b.y)
            }
        }
        return p
    }
}

/** Overlay that shows strokes and, when [enabled], records new ones. */
@Composable
fun MarkupLayer(
    strokes: List<MarkStroke>,
    enabled: Boolean,
    brush: BrushMode,
    color: Color,
    width: Float,
    onStroke: (MarkStroke) -> Unit,
    modifier: Modifier = Modifier,
) {
    val current = remember { mutableStateListOf<Offset>() }
    val brushState by rememberUpdatedState(brush)
    val colorState by rememberUpdatedState(color)
    val widthState by rememberUpdatedState(width)
    val onStrokeState by rememberUpdatedState(onStroke)

    val input = if (!enabled) Modifier else Modifier.pointerInput(Unit) {
        detectDragGestures(
            onDragStart = { p ->
                current.clear()
                current.add(Offset(p.x / size.width, p.y / size.height))
            },
            onDrag = { change, _ ->
                change.consume()
                val n = Offset(change.position.x / size.width, change.position.y / size.height)
                if (brushState.isShape && current.size >= 2) current[current.lastIndex] = n else current.add(n)
            },
            onDragEnd = {
                if (current.isNotEmpty()) {
                    onStrokeState(MarkStroke(brushState, colorState.toArgb(), widthState, current.toList()))
                }
                current.clear()
            },
            onDragCancel = { current.clear() },
        )
    }

    Canvas(modifier.fillMaxSize().then(input)) {
        val live = if (current.isEmpty()) null
        else MarkStroke(brush, color.toArgb(), width, current.toList())
        drawIntoCanvas {
            MarkupRenderer.draw(it.nativeCanvas, if (live == null) strokes else strokes + live, size.width, size.height)
        }
    }
}

@Composable
fun MarkupPanel(
    brush: BrushMode,
    onBrush: (BrushMode) -> Unit,
    color: Color,
    onColor: (Color) -> Unit,
    width: Float,
    onWidth: (Float) -> Unit,
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(BrushMode.entries) { m ->
                ToolChip(m.label, m.icon, selected = m == brush, size = 48.dp) { onBrush(m) }
            }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items(markupColors) { c ->
                val sel = c == color
                Box(
                    Modifier
                        .size(if (sel) 34.dp else 28.dp)
                        .clip(CircleShape)
                        .background(c)
                        .border(if (sel) 3.dp else 1.dp, if (sel) Palette.Orange else Color.White.copy(.3f), CircleShape)
                        .clickable { onColor(c) }
                )
            }
        }
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Size", color = Palette.OnDark, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 8.dp, end = 8.dp))
            Slider(
                value = width,
                onValueChange = onWidth,
                valueRange = 0.004f..0.05f,
                colors = SliderDefaults.colors(
                    thumbColor = Palette.Orange,
                    activeTrackColor = Palette.Orange,
                    inactiveTrackColor = Color.White.copy(alpha = .18f),
                ),
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onUndo, enabled = canUndo) {
                Icon(Icons.AutoMirrored.Outlined.Undo, "Undo stroke", tint = if (canUndo) Palette.OnDark else Palette.OnDarkMuted.copy(.4f))
            }
            IconButton(onClick = onRedo, enabled = canRedo) {
                Icon(Icons.AutoMirrored.Outlined.Redo, "Redo stroke", tint = if (canRedo) Palette.OnDark else Palette.OnDarkMuted.copy(.4f))
            }
        }
    }
}
