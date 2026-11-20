package com.lutfan.photogallery.ui.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

object ImageOps {

    /** Decodes [uri] (EXIF orientation applied) so the longest side is at most [maxSide]. */
    fun decode(context: Context, uri: Uri, maxSide: Int): Bitmap {
        val src = ImageDecoder.createSource(context.contentResolver, uri)
        return ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = false
            val w = info.size.width
            val h = info.size.height
            val longest = max(w, h)
            if (longest > maxSide) {
                val k = maxSide.toFloat() / longest
                decoder.setTargetSize((w * k).roundToInt().coerceAtLeast(1), (h * k).roundToInt().coerceAtLeast(1))
            }
        }
    }

    /** Applies the 90° rotation, then a horizontal flip in the rotated frame. */
    fun rotateFlip(src: Bitmap, rotation: Int, flipH: Boolean): Bitmap {
        if (rotation == 0 && !flipH) return src
        val m = Matrix().apply {
            postRotate(rotation.toFloat())
            if (flipH) postScale(-1f, 1f)
        }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
    }

    /**
     * Scale needed so an image of size w×h, rotated by [deg], still covers its
     * original w×h frame (no empty corners).
     */
    fun straightenScale(deg: Float, w: Float, h: Float): Float {
        if (deg == 0f || w <= 0f || h <= 0f) return 1f
        val a = Math.toRadians(abs(deg).toDouble())
        val c = cos(a).toFloat()
        val s = sin(a).toFloat()
        return max(c + (h / w) * s, c + (w / h) * s)
    }

    /** Straighten + crop on an already rotated/flipped bitmap (colour is done on the GPU). */
    fun render(base: Bitmap, state: EditState): Bitmap {
        val bw = base.width.toFloat()
        val bh = base.height.toFloat()
        val crop = state.crop
        val outW = (crop.w * bw).roundToInt().coerceAtLeast(1)
        val outH = (crop.h * bh).roundToInt().coerceAtLeast(1)
        val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val scale = straightenScale(state.straighten, bw, bh)
        canvas.translate(-crop.l * bw, -crop.t * bh)
        canvas.translate(bw / 2f, bh / 2f)
        canvas.rotate(state.straighten)
        canvas.scale(scale, scale)
        canvas.translate(-bw / 2f, -bh / 2f)
        canvas.drawBitmap(base, 0f, 0f, paint)
        return out
    }

    /** Full-resolution export: geometry → GPU colour pipeline → markup. */
    fun export(full: Bitmap, state: EditState): Bitmap {
        val rotated = rotateFlip(full, state.rotation, state.flipH)
        val geo = render(rotated, state)
        val colored = if (!state.hasColor) geo else runCatching { GpuEffects.applyForExport(geo, state) }
            .getOrElse { cpuColorFallback(geo, state) }
        if (state.strokes.isNotEmpty()) {
            val mutable = if (colored.isMutable) colored else colored.copy(Bitmap.Config.ARGB_8888, true)
            MarkupRenderer.draw(Canvas(mutable), state.strokes, mutable.width.toFloat(), mutable.height.toFloat())
            return mutable
        }
        return colored
    }

    /** Matrix-only colour (no curves/grain) if the GPU path is unavailable. */
    private fun cpuColorFallback(src: Bitmap, state: EditState): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = android.graphics.ColorMatrixColorFilter(state.colorMatrix())
        }
        Canvas(out).drawBitmap(src, 0f, 0f, paint)
        return out
    }

    /** Largest centred crop with the given pixel ratio (w/h) inside a frame of [frameRatio]. */
    fun centeredCrop(targetRatio: Float, frameRatio: Float): NormRect {
        val kn = targetRatio / frameRatio // normalised width / normalised height
        return if (kn >= 1f) {
            val h = 1f / kn
            NormRect(0f, (1f - h) / 2f, 1f, (1f + h) / 2f)
        } else {
            NormRect((1f - kn) / 2f, 0f, (1f + kn) / 2f, 1f)
        }
    }
}
