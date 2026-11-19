package com.lutfan.photogallery.ui.editor

import android.graphics.ColorMatrix
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrightnessMedium
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.InvertColors
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.Vignette
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.Gradient
import androidx.compose.ui.graphics.vector.ImageVector

enum class EditTab(val title: String, val label: String) {
    Crop("Crop and rotate", "Crop"),
    Adjust("Adjust", "Adjust"),
    Filters("Filters", "Filters"),
}

/** [ratio] is width / height; null = free, -1 = the image's own ratio. */
enum class Aspect(val label: String, val ratio: Float?) {
    Free("Free", null),
    Original("Original", -1f),
    Square("Square", 1f),
    R2x3("2:3", 2f / 3f),
    R3x2("3:2", 3f / 2f),
    R4x5("4:5", 4f / 5f),
    R16x9("16:9", 16f / 9f),
}

enum class AdjustTool(val label: String, val icon: ImageVector, val min: Float = -100f) {
    Brightness("Brightness", Icons.Outlined.WbSunny),
    Exposure("Exposure", Icons.Outlined.BrightnessMedium),
    Contrast("Contrast", Icons.Outlined.Contrast),
    Saturation("Saturation", Icons.Outlined.InvertColors),
    Warmth("Warmth", Icons.Outlined.Thermostat),
    Tint("Tint", Icons.Outlined.Palette),
    Fade("Fade", Icons.Outlined.Gradient, min = 0f),
    Vignette("Vignette", Icons.Outlined.Vignette, min = 0f),
}

data class NormRect(val l: Float, val t: Float, val r: Float, val b: Float) {
    val w get() = r - l
    val h get() = b - t
    companion object { val Full = NormRect(0f, 0f, 1f, 1f) }
}

data class EditState(
    val rotation: Int = 0,          // 0, 90, 180, 270 (clockwise)
    val flipH: Boolean = false,
    val straighten: Float = 0f,     // -45..45 degrees
    val crop: NormRect = NormRect.Full,
    val aspect: Aspect = Aspect.Free,
    val adjust: Map<AdjustTool, Float> = emptyMap(),
    val filter: Filter = Filter.Original,
    val filterStrength: Float = 1f,
) {
    val hasGeometry get() = rotation != 0 || flipH || straighten != 0f || crop != NormRect.Full
    val hasColor get() = filter != Filter.Original || adjust.values.any { it != 0f }
    val isEdited get() = hasGeometry || hasColor

    fun value(tool: AdjustTool) = adjust[tool] ?: 0f
    val vignette get() = value(AdjustTool.Vignette) / 100f

    /** Full colour pipeline: filter (blended by strength) then the adjust sliders. */
    fun colorMatrix(): ColorMatrix {
        val m = ColorMatrix()
        if (filter != Filter.Original) m.postConcat(lerpMatrix(filter.matrix(), filterStrength))
        m.postConcat(adjustMatrix(adjust))
        return m
    }

    fun colorMatrixValues(): FloatArray = colorMatrix().array.copyOf()
}

/* ---------- colour math (android.graphics.ColorMatrix, offsets in 0..255) ---------- */

private fun cm(vararg v: Float) = ColorMatrix(v)

private fun scaleOffset(r: Float, g: Float, b: Float, ro: Float = 0f, go: Float = 0f, bo: Float = 0f) =
    cm(r, 0f, 0f, 0f, ro, 0f, g, 0f, 0f, go, 0f, 0f, b, 0f, bo, 0f, 0f, 0f, 1f, 0f)

private fun contrast(s: Float): ColorMatrix {
    val t = 128f * (1f - s)
    return scaleOffset(s, s, s, t, t, t)
}

private fun saturation(s: Float) = ColorMatrix().apply { setSaturation(s) }

private fun chain(vararg ms: ColorMatrix) = ColorMatrix().apply { ms.forEach { postConcat(it) } }

private fun lerpMatrix(m: ColorMatrix, k: Float): ColorMatrix {
    val id = ColorMatrix().array
    val a = m.array
    return ColorMatrix(FloatArray(20) { id[it] + (a[it] - id[it]) * k })
}

private fun adjustMatrix(adjust: Map<AdjustTool, Float>): ColorMatrix {
    fun v(t: AdjustTool) = (adjust[t] ?: 0f) / 100f
    val out = ColorMatrix()
    val exposure = v(AdjustTool.Exposure)
    if (exposure != 0f) {
        val s = Math.pow(2.0, exposure.toDouble()).toFloat()
        out.postConcat(scaleOffset(s, s, s))
    }
    val brightness = v(AdjustTool.Brightness)
    if (brightness != 0f) {
        val o = brightness * 60f
        out.postConcat(scaleOffset(1f, 1f, 1f, o, o, o))
    }
    val c = v(AdjustTool.Contrast)
    if (c != 0f) out.postConcat(contrast(1f + c * 0.5f))
    val sat = v(AdjustTool.Saturation)
    if (sat != 0f) out.postConcat(saturation(1f + sat))
    val warm = v(AdjustTool.Warmth)
    if (warm != 0f) out.postConcat(scaleOffset(1f + 0.10f * warm, 1f, 1f - 0.10f * warm, 14f * warm, 0f, -14f * warm))
    val tint = v(AdjustTool.Tint)
    if (tint != 0f) out.postConcat(scaleOffset(1f, 1f, 1f, 6f * tint, -18f * tint, 6f * tint))
    val fade = v(AdjustTool.Fade)
    if (fade != 0f) {
        val s = 1f - 0.35f * fade
        val o = 255f * 0.35f * fade * 0.55f
        out.postConcat(scaleOffset(s, s, s, o, o, o))
    }
    return out
}

enum class Filter(val label: String) {
    Original("Original"),
    Vivid("Vivid"),
    Warm("Warm"),
    Cool("Cool"),
    Golden("Golden"),
    Soft("Soft"),
    Vintage("Vintage"),
    Mono("Mono"),
    Noir("Noir");

    fun matrix(): ColorMatrix = when (this) {
        Original -> ColorMatrix()
        Vivid -> chain(saturation(1.35f), contrast(1.12f))
        Warm -> chain(scaleOffset(1.08f, 1.0f, 0.88f, 10f, 4f, -6f), saturation(1.08f))
        Cool -> chain(scaleOffset(0.92f, 1.0f, 1.08f, -6f, 0f, 12f), saturation(0.95f))
        Golden -> chain(scaleOffset(1.10f, 1.04f, 0.85f, 14f, 8f, -4f), contrast(1.06f), saturation(1.12f))
        Soft -> chain(contrast(0.82f), scaleOffset(1f, 1f, 1f, 14f, 14f, 16f), saturation(0.9f))
        Vintage -> chain(
            cm(
                0.62f, 0.32f, 0.08f, 0f, 18f,
                0.22f, 0.68f, 0.10f, 0f, 10f,
                0.18f, 0.28f, 0.52f, 0f, 6f,
                0f, 0f, 0f, 1f, 0f,
            ),
            contrast(0.9f),
        )
        Mono -> chain(saturation(0f), contrast(1.05f))
        Noir -> chain(saturation(0f), contrast(1.4f), scaleOffset(1f, 1f, 1f, -10f, -10f, -10f))
    }
}
