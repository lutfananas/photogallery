package com.lutfan.photogallery.ui.editor

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.ColorSpace
import android.graphics.HardwareRenderer
import android.graphics.PixelFormat
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.hardware.HardwareBuffer
import android.media.ImageReader
import kotlin.math.max

/**
 * The whole colour pipeline as one AGSL shader, so every slider tick is just a
 * uniform update on the GPU (no bitmap work) and stays at the display's refresh rate.
 *
 * Order: sharpen → colour matrix (filter + adjust) → highlights/shadows →
 * tone-curve LUT → vignette → grain.
 */
object GpuEffects {

    private const val SRC = """
        uniform shader image;
        uniform shader lut;
        uniform float2 size;
        uniform float4x4 cm;
        uniform float4 cmOffset;
        uniform float4 tone;   // x highlights, y shadows, z sharpen, w vignette
        uniform float2 grain;  // x amount, y cell size in px

        float hash(float2 c) {
            c = mod(c, 289.0);
            return fract(sin(dot(c, float2(12.9898, 78.233))) * 43758.5453);
        }

        half4 main(float2 p) {
            half4 src = image.eval(p);
            float a = float(src.a);
            float3 rgb = a > 0.0 ? float3(src.rgb) / a : float3(0.0);

            if (tone.z > 0.0) {
                float3 n = (float3(image.eval(p + float2(1.0, 0.0)).rgb) +
                            float3(image.eval(p - float2(1.0, 0.0)).rgb) +
                            float3(image.eval(p + float2(0.0, 1.0)).rgb) +
                            float3(image.eval(p - float2(0.0, 1.0)).rgb)) * 0.25;
                rgb = rgb + (rgb - n) * tone.z * 2.5;
            }

            rgb = clamp((cm * float4(rgb, 1.0)).rgb + cmOffset.rgb, 0.0, 1.0);

            float l = dot(rgb, float3(0.2126, 0.7152, 0.0722));
            rgb += tone.y * 0.45 * (1.0 - smoothstep(0.0, 0.55, l));
            rgb += tone.x * 0.45 * smoothstep(0.45, 1.0, l);
            rgb = clamp(rgb, 0.0, 1.0);

            rgb = float3(
                float(lut.eval(float2(rgb.r * 255.0 + 0.5, 0.5)).r),
                float(lut.eval(float2(rgb.g * 255.0 + 0.5, 0.5)).g),
                float(lut.eval(float2(rgb.b * 255.0 + 0.5, 0.5)).b));

            if (tone.w > 0.0) {
                float d = length(p - size * 0.5) / (length(size) * 0.5);
                rgb *= 1.0 - smoothstep(0.45, 1.0, d) * tone.w * 0.85;
            }
            if (grain.x > 0.0) {
                float n = hash(floor(p / grain.y)) - 0.5;
                rgb += n * grain.x * 0.22;
            }
            rgb = clamp(rgb, 0.0, 1.0);
            return half4(half3(rgb * a), half(a));
        }
    """

    fun newShader() = RuntimeShader(SRC)

    fun lutShader(lut: Bitmap) = BitmapShader(lut, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)

    /** Writes [state] into [shader] for a target of [w]×[h] pixels and returns the effect. */
    fun effect(shader: RuntimeShader, state: EditState, lut: BitmapShader, w: Float, h: Float): RenderEffect {
        val m = state.colorMatrix().array
        // android ColorMatrix is row-major 4x5; AGSL float4x4 uniforms are column-major.
        val colMajor = FloatArray(16) { i -> m[(i % 4) * 5 + i / 4] }
        shader.setFloatUniform("size", w, h)
        shader.setFloatUniform("cm", colMajor)
        shader.setFloatUniform("cmOffset", m[4] / 255f, m[9] / 255f, m[14] / 255f, m[19] / 255f)
        shader.setFloatUniform("tone", state.highlights, state.shadows, state.sharpen, state.vignette)
        shader.setFloatUniform("grain", state.grain, max(1f, w / 1400f))
        shader.setInputShader("lut", lut)
        return RenderEffect.createRuntimeShaderEffect(shader, "image")
    }

    /** Runs the same shader off-screen on the GPU at full export resolution. */
    fun applyForExport(src: Bitmap, state: EditState): Bitmap {
        val w = src.width
        val h = src.height
        val lut = state.curves.lutBitmap()
        val effect = effect(newShader(), state, lutShader(lut), w.toFloat(), h.toFloat())
        val reader = ImageReader.newInstance(
            w, h, PixelFormat.RGBA_8888, 1,
            HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE or HardwareBuffer.USAGE_GPU_COLOR_OUTPUT,
        )
        val node = RenderNode("export").apply {
            setPosition(0, 0, w, h)
            setRenderEffect(effect)
        }
        val canvas = node.beginRecording()
        canvas.drawBitmap(src, 0f, 0f, null)
        node.endRecording()
        val renderer = HardwareRenderer().apply {
            setSurface(reader.surface)
            setContentRoot(node)
        }
        try {
            renderer.createRenderRequest().setWaitForPresent(true).syncAndDraw()
            val image = reader.acquireNextImage() ?: error("GPU render produced no image")
            image.use {
                val buffer = it.hardwareBuffer ?: error("No hardware buffer")
                buffer.use { hb ->
                    val hw = Bitmap.wrapHardwareBuffer(hb, ColorSpace.get(ColorSpace.Named.SRGB))
                        ?: error("wrapHardwareBuffer failed")
                    return hw.copy(Bitmap.Config.ARGB_8888, true)
                }
            }
        } finally {
            renderer.destroy()
            node.discardDisplayList()
            reader.close()
        }
    }
}
