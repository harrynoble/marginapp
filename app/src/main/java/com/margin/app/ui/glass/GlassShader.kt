package com.margin.app.ui.glass

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import androidx.annotation.RequiresApi

/**
 * The refraction half of Liquid Glass, written in AGSL and run on the GPU.
 *
 * Liquid Glass is not frosted glass. Its defining trait is lensing: near the rim the material
 * bends light, so the content behind appears to curve toward the edge, the way a drop of water
 * magnifies what it sits on. This shader reproduces that.
 *
 * For every pixel it measures the distance to the edge of the glass shape with a signed
 * distance field, and inside a narrow rim band it samples the backdrop from further inward
 * along the surface normal. The displacement follows a circular lens profile, flat in the
 * middle and steep at the rim. A small per-channel offset gives the faint chromatic fringe
 * real glass shows at its edge.
 *
 * The input is the backdrop after blur and saturation, so refraction bends the frosted image.
 */
@RequiresApi(33)
internal object GlassShader {

    private const val SOURCE = """
        uniform shader content;
        uniform float2 origin;
        uniform float2 size;
        uniform float radius;
        uniform float band;
        uniform float strength;
        uniform float dispersion;

        float sdRoundRect(float2 p, float2 halfSize, float r) {
            float2 q = abs(p) - halfSize + float2(r, r);
            return length(max(q, float2(0.0, 0.0))) + min(max(q.x, q.y), 0.0) - r;
        }

        half4 main(float2 coord) {
            float2 halfSize = size * 0.5;
            float2 p = coord - origin - halfSize;
            float r = min(radius, min(halfSize.x, halfSize.y));
            float depth = -sdRoundRect(p, halfSize, r);
            if (depth <= 0.0 || depth >= band) {
                return content.eval(coord);
            }

            float t = 1.0 - depth / band;
            float bend = strength * (1.0 - sqrt(max(1.0 - t * t, 0.0)));

            float e = 0.75;
            float2 grad = float2(
                sdRoundRect(p + float2(e, 0.0), halfSize, r) - sdRoundRect(p - float2(e, 0.0), halfSize, r),
                sdRoundRect(p + float2(0.0, e), halfSize, r) - sdRoundRect(p - float2(0.0, e), halfSize, r));
            float len = length(grad);
            float2 normal = len > 0.0001 ? grad / len : float2(0.0, 0.0);
            float2 offset = normal * bend;

            half4 base = content.eval(coord - offset);
            half red = content.eval(coord - offset * (1.0 + dispersion)).r;
            half blue = content.eval(coord - offset * (1.0 - dispersion)).b;
            return half4(red, base.g, blue, base.a);
        }
    """

    /**
     * Wraps [input] with refraction for a glass shape of [width] x [height] whose top-left sits
     * at ([padding], [padding]) inside the effect layer. Returns null if the shader cannot be
     * compiled on this device, in which case the caller keeps the plain frosted look.
     */
    fun wrap(
        input: RenderEffect,
        padding: Float,
        width: Float,
        height: Float,
        cornerRadius: Float,
        band: Float,
        strength: Float,
        dispersion: Float,
    ): RenderEffect? = runCatching {
        val shader = RuntimeShader(SOURCE).apply {
            setFloatUniform("origin", padding, padding)
            setFloatUniform("size", width, height)
            setFloatUniform("radius", cornerRadius)
            setFloatUniform("band", band)
            setFloatUniform("strength", strength)
            setFloatUniform("dispersion", dispersion)
        }
        val refract = RenderEffect.createRuntimeShaderEffect(shader, "content")
        RenderEffect.createChainEffect(refract, input)
    }.getOrNull()
}
