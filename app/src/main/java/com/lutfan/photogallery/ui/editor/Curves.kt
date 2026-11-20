package com.lutfan.photogallery.ui.editor

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.lutfan.photogallery.ui.theme.Palette
import kotlin.math.roundToInt

enum class CurveChannel(val label: String, val color: Color) {
    Master("RGB", Color.White),
    Red("Red", Color(0xFFFF6B5E)),
    Green("Green", Color(0xFF6BD68A)),
    Blue("Blue", Color(0xFF5EA8FF)),
}

private val Identity = listOf(Offset(0f, 0f), Offset(1f, 1f))

/** Control points per channel, in 0..1 (y up). */
data class Curves(
    val master: List<Offset> = Identity,
    val red: List<Offset> = Identity,
    val green: List<Offset> = Identity,
    val blue: List<Offset> = Identity,
) {
    val isIdentity get() = listOf(master, red, green, blue).all { it == Identity }

    fun points(c: CurveChannel) = when (c) {
        CurveChannel.Master -> master
        CurveChannel.Red -> red
        CurveChannel.Green -> green
        CurveChannel.Blue -> blue
    }

    fun with(c: CurveChannel, pts: List<Offset>) = when (c) {
        CurveChannel.Master -> copy(master = pts)
        CurveChannel.Red -> copy(red = pts)
        CurveChannel.Green -> copy(green = pts)
        CurveChannel.Blue -> copy(blue = pts)
    }

    /** 256×1 lookup texture: per channel, the channel curve then the master curve. */
    fun lutBitmap(): Bitmap {
        val m = MonotoneSpline(master)
        val r = MonotoneSpline(red)
        val g = MonotoneSpline(green)
        val b = MonotoneSpline(blue)
        val px = IntArray(256) { i ->
            val x = i / 255f
            fun v(s: MonotoneSpline) = (m(s(x)).coerceIn(0f, 1f) * 255f).roundToInt()
            (0xFF shl 24) or (v(r) shl 16) or (v(g) shl 8) or v(b)
        }
        return Bitmap.createBitmap(px, 256, 1, Bitmap.Config.ARGB_8888)
    }
}

/** Fritsch–Carlson monotone cubic interpolation (no overshoot between points). */
class MonotoneSpline(points: List<Offset>) {
    private val xs = points.map { it.x }.toFloatArray()
    private val ys = points.map { it.y }.toFloatArray()
    private val ms: FloatArray

    init {
        val n = xs.size
        val d = FloatArray(n - 1) { (ys[it + 1] - ys[it]) / (xs[it + 1] - xs[it]).coerceAtLeast(1e-4f) }
        ms = FloatArray(n)
        ms[0] = d[0]
        ms[n - 1] = d[n - 2]
        for (i in 1 until n - 1) ms[i] = if (d[i - 1] * d[i] <= 0f) 0f else (d[i - 1] + d[i]) / 2f
        for (i in 0 until n - 1) {
            if (d[i] == 0f) { ms[i] = 0f; ms[i + 1] = 0f; continue }
            val a = ms[i] / d[i]
            val b = ms[i + 1] / d[i]
            val s = a * a + b * b
            if (s > 9f) {
                val t = 3f / kotlin.math.sqrt(s)
                ms[i] = t * a * d[i]
                ms[i + 1] = t * b * d[i]
            }
        }
    }

    operator fun invoke(x: Float): Float {
        if (x <= xs.first()) return ys.first()
        if (x >= xs.last()) return ys.last()
        var i = 0
        while (i < xs.size - 2 && x > xs[i + 1]) i++
        val h = xs[i + 1] - xs[i]
        val t = (x - xs[i]) / h
        val t2 = t * t
        val t3 = t2 * t
        return (2 * t3 - 3 * t2 + 1) * ys[i] + (t3 - 2 * t2 + t) * h * ms[i] +
            (-2 * t3 + 3 * t2) * ys[i + 1] + (t3 - t2) * h * ms[i + 1]
    }
}

@Composable
fun CurvesPanel(curves: Curves, onChange: (Curves) -> Unit) {
    var channel by remember { mutableStateOf(CurveChannel.Master) }
    Column(
        Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            CurveChannel.entries.forEach { c ->
                val sel = c == channel
                Row(
                    Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (sel) Palette.Orange else Palette.CharcoalChip)
                        .clickable { channel = c }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(c.color))
                    Spacer(Modifier.width(6.dp))
                    Text(c.label, color = Color.White, style = MaterialTheme.typography.labelMedium)
                }
            }
            Spacer(Modifier.weight(1f))
            Text(
                "Reset",
                color = if (curves.points(channel) != Identity) Palette.Orange else Palette.OnDarkMuted,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.clickable { onChange(curves.with(channel, Identity)) }.padding(6.dp),
            )
        }
        CurveEditor(
            curves = curves,
            channel = channel,
            onChange = onChange,
            modifier = Modifier.fillMaxWidth().weight(1f),
        )
    }
}

@Composable
private fun CurveEditor(
    curves: Curves,
    channel: CurveChannel,
    onChange: (Curves) -> Unit,
    modifier: Modifier = Modifier,
) {
    val curvesState by rememberUpdatedState(curves)
    val channelState by rememberUpdatedState(channel)
    val touch = with(LocalDensity.current) { 28.dp.toPx() }
    var active by remember { mutableStateOf(-1) }

    Canvas(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black.copy(alpha = .22f))
            .pointerInput(Unit) {
                var lastTapTime = 0L
                var lastTapIndex = -1
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val w = size.width.toFloat()
                    val h = size.height.toFloat()
                    fun toNorm(o: Offset) = Offset((o.x / w).coerceIn(0f, 1f), (1f - o.y / h).coerceIn(0f, 1f))
                    var pts = curvesState.points(channelState).toMutableList()
                    val p = down.position
                    var index = pts.indices.minByOrNull { (Offset(pts[it].x * w, (1 - pts[it].y) * h) - p).getDistance() } ?: -1
                    val near = index >= 0 && (Offset(pts[index].x * w, (1 - pts[index].y) * h) - p).getDistance() < touch
                    var added = false
                    if (!near) {
                        val n = toNorm(p)
                        if (pts.size >= 12) return@awaitEachGesture
                        val y = MonotoneSpline(pts)(n.x)
                        index = pts.indexOfFirst { it.x > n.x }.let { if (it < 0) pts.size - 1 else it }
                        if (index == 0) index = 1
                        pts.add(index, Offset(n.x, y))
                        added = true
                        onChange(curvesState.with(channelState, pts.toList()))
                    }
                    active = index
                    var moved = false
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                        if (!ch.pressed) break
                        val delta = ch.positionChange()
                        if (delta != Offset.Zero) {
                            moved = true
                            ch.consume()
                            val n = toNorm(ch.position)
                            val i = index
                            val x = when (i) {
                                0 -> 0f
                                pts.lastIndex -> 1f
                                else -> n.x.coerceIn(pts[i - 1].x + 0.02f, pts[i + 1].x - 0.02f)
                            }
                            pts[i] = Offset(x, n.y)
                            onChange(curvesState.with(channelState, pts.toList()))
                        }
                    }
                    // Double-tap an inner point to remove it.
                    if (!moved && !added) {
                        val now = System.currentTimeMillis()
                        if (lastTapIndex == index && now - lastTapTime < 350 && index in 1 until pts.lastIndex) {
                            pts.removeAt(index)
                            onChange(curvesState.with(channelState, pts.toList()))
                            lastTapIndex = -1
                        } else {
                            lastTapIndex = index
                            lastTapTime = now
                        }
                    }
                    active = -1
                }
            }
    ) {
        val w = size.width
        val h = size.height
        val grid = Color.White.copy(alpha = .10f)
        for (i in 1..3) {
            drawLine(grid, Offset(w * i / 4f, 0f), Offset(w * i / 4f, h), 1f)
            drawLine(grid, Offset(0f, h * i / 4f), Offset(w, h * i / 4f), 1f)
        }
        drawLine(Color.White.copy(alpha = .18f), Offset(0f, h), Offset(w, 0f), 1.dp.toPx())

        fun curvePath(points: List<Offset>): Path {
            val s = MonotoneSpline(points)
            return Path().apply {
                val steps = 96
                for (k in 0..steps) {
                    val x = k / steps.toFloat()
                    val px = x * w
                    val py = (1f - s(x).coerceIn(0f, 1f)) * h
                    if (k == 0) moveTo(px, py) else lineTo(px, py)
                }
            }
        }
        CurveChannel.entries.filter { it != channel && curves.points(it) != Identity }.forEach { c ->
            drawPath(curvePath(curves.points(c)), c.color.copy(alpha = .35f), style = Stroke(1.5.dp.toPx()))
        }
        val pts = curves.points(channel)
        drawPath(curvePath(pts), channel.color, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
        pts.forEachIndexed { i, p ->
            val c = Offset(p.x * w, (1f - p.y) * h)
            drawCircle(if (i == active) Palette.Orange else Color.White, (if (i == active) 9 else 6).dp.toPx(), c)
            drawCircle(Palette.Charcoal, 2.dp.toPx(), c)
        }
    }
}
