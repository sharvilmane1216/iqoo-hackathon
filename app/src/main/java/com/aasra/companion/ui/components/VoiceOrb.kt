package com.aasra.companion.ui.components

import android.graphics.RuntimeShader
import android.os.Build
import android.provider.Settings.Global
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.aasra.companion.pipeline.VoiceState
import kotlinx.coroutines.isActive
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/**
 * GPU orb ported from the JARVIS/OGL plasma shader
 * (gist KaushikShresth07: simplex noise, purple–cyan body, orbiting light).
 * Rim term is the classic 1/|r-R| glow used by Siri-style rings.
 * API 33+ uses AGSL; older devices use an additive canvas stand-in.
 */
private const val ORB_AGSL = """
uniform float2 iResolution;
uniform float iTime;
uniform float energy;
uniform float rot;
uniform float hue;

float3 rgb2yiq(float3 c) {
    return float3(dot(c, float3(0.299, 0.587, 0.114)),
        dot(c, float3(0.596, -0.274, -0.322)),
        dot(c, float3(0.211, -0.523, 0.312)));
}
float3 yiq2rgb(float3 c) {
    return float3(c.x + 0.956 * c.y + 0.621 * c.z,
        c.x - 0.272 * c.y - 0.647 * c.z,
        c.x - 1.106 * c.y + 1.703 * c.z);
}
float3 adjustHue(float3 color, float hueDeg) {
    float h = hueDeg * 0.017453292;
    float3 yiq = rgb2yiq(color);
    float ca = cos(h);
    float sa = sin(h);
    float i2 = yiq.y * ca - yiq.z * sa;
    float q2 = yiq.y * sa + yiq.z * ca;
    yiq.y = i2;
    yiq.z = q2;
    return yiq2rgb(yiq);
}
float3 hash33(float3 p3) {
    p3 = fract(p3 * float3(0.1031, 0.11369, 0.13787));
    p3 += dot(p3, p3.yxz + 19.19);
    return -1.0 + 2.0 * fract(float3(p3.x + p3.y, p3.x + p3.z, p3.y + p3.z) * p3.zyx);
}
float snoise3(float3 p) {
    float K1 = 0.333333333;
    float K2 = 0.166666667;
    float3 i = floor(p + (p.x + p.y + p.z) * K1);
    float3 d0 = p - (i - (i.x + i.y + i.z) * K2);
    float3 e = step(float3(0.0), d0 - d0.yzx);
    float3 i1 = e * (1.0 - e.zxy);
    float3 i2 = 1.0 - e.zxy * (1.0 - e);
    float3 d1 = d0 - (i1 - K2);
    float3 d2 = d0 - (i2 - K1);
    float3 d3 = d0 - 0.5;
    float4 h = max(0.6 - float4(dot(d0, d0), dot(d1, d1), dot(d2, d2), dot(d3, d3)), 0.0);
    float4 n = h * h * h * h * float4(
        dot(d0, hash33(i)),
        dot(d1, hash33(i + i1)),
        dot(d2, hash33(i + i2)),
        dot(d3, hash33(i + 1.0))
    );
    return dot(float4(31.316), n);
}
float light1(float i, float a, float d) { return i / (1.0 + d * a); }
float light2(float i, float a, float d) { return i / (1.0 + d * d * a); }

half4 main(float2 fragCoord) {
    float2 center = iResolution * 0.5;
    float sz = min(iResolution.x, iResolution.y);
    float2 uv = (fragCoord - center) / sz * 2.0;
    float s2 = sin(rot);
    float c2 = cos(rot);
    uv = float2(c2 * uv.x - s2 * uv.y, s2 * uv.x + c2 * uv.y);
    float wave = 0.04 + energy * 0.06;
    uv.x += wave * 0.03 * sin(uv.y * 2.2 + iTime);
    uv.y += wave * 0.03 * sin(uv.x * 2.2 + iTime);

    float3 c1 = adjustHue(float3(0.611765, 0.262745, 0.996078), hue);
    float3 c2c = adjustHue(float3(0.298039, 0.760784, 0.913725), hue);
    float3 c3 = adjustHue(float3(0.062745, 0.078431, 0.600000), hue);

    float ang = atan(uv.y, uv.x);
    float len = length(uv);
    float invLen = len > 0.0 ? 1.0 / len : 0.0;
    float inner = 0.62;
    float n0 = snoise3(float3(uv * 0.9, iTime * 0.4)) * 0.5 + 0.5;
    float r0 = 0.74 + energy * 0.03 + (n0 - 0.5) * 0.018;
    float d0 = distance(uv, (r0 * invLen) * uv);
    float v0 = light1(1.0, 10.0, d0);
    v0 *= smoothstep(r0 * 1.08, r0, len);
    v0 *= smoothstep(r0 * 0.86, r0 * 0.97, len);
    float cl = cos(ang + iTime * 1.7) * 0.5 + 0.5;
    float a2 = iTime * -1.15;
    float2 pos = float2(cos(a2), sin(a2)) * r0;
    float v1 = light2(0.85 + energy * 0.35, 9.0, distance(uv, pos));
    v1 *= light1(1.0, 50.0, d0);
    float a3 = iTime * 0.83 + 2.1;
    float2 pos2 = float2(cos(a3), sin(a3)) * r0 * 0.92;
    float v1b = light2(0.55 + energy * 0.25, 11.0, distance(uv, pos2));
    float v2 = smoothstep(1.02, r0, len);
    float v3 = smoothstep(inner, r0 * 0.92, len);
    float3 colBase = mix(c1, c2c, cl);
    float3 darkCol = mix(c3, colBase, v0);
    darkCol = (darkCol + v1 + v1b * 0.65) * v2 * v3;
    float rim = 0.018 / (abs(len - r0) + 0.006);
    darkCol += c2c * rim * (0.5 + energy * 0.65);
    darkCol = clamp(darkCol, 0.0, 1.0);
    float a = max(max(darkCol.r, darkCol.g), darkCol.b);
    return half4(darkCol * a, a);
}
"""

@Composable
fun VoiceOrb(state: VoiceState, audioLevel: Float, description: String) {
    val context = LocalContext.current
    val still = remember {
        Global.getFloat(context.contentResolver, Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    val shader = remember {
        if (Build.VERSION.SDK_INT >= 33) runCatching { RuntimeShader(ORB_AGSL) }.getOrNull() else null
    }
    val live = rememberUpdatedState(audioLevel.coerceIn(0f, 1f) to state)
    var t by remember { mutableFloatStateOf(0f) }
    var smooth by remember { mutableFloatStateOf(0f) }
    var rot by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(still) {
        if (still) return@LaunchedEffect
        var last = 0L
        while (isActive) {
            withFrameNanos { now ->
                if (last != 0L) {
                    val dt = ((now - last) / 1_000_000_000f).coerceIn(0f, 0.05f)
                    t += dt
                    val (raw, phase) = live.value
                    val target = if (phase == VoiceState.SPEAKING || phase == VoiceState.LISTENING) raw else 0f
                    val k = if (target > smooth) 16f else 5.5f
                    smooth += (target - smooth) * (1f - exp(-k * dt))
                    val spin = when (phase) {
                        VoiceState.THINKING -> 0.85f
                        VoiceState.SPEAKING -> 0.35f + smooth * 0.4f
                        VoiceState.LISTENING -> 0.22f
                        VoiceState.IDLE -> 0.08f
                    }
                    rot += dt * spin
                }
                last = now
            }
        }
    }
    val voice = smooth
    val energy = when (state) {
        VoiceState.IDLE -> 0.12f + sin(t * 0.7f) * 0.04f
        VoiceState.LISTENING -> 0.28f + voice * 0.72f
        VoiceState.THINKING -> 0.42f
        VoiceState.SPEAKING -> 0.22f + voice * 0.85f
    }
    val hue = when (state) {
        VoiceState.LISTENING -> -8f
        VoiceState.SPEAKING -> 12f
        VoiceState.THINKING -> 28f
        VoiceState.IDLE -> 0f
    }
    Canvas(Modifier.fillMaxWidth().height(320.dp).semantics { contentDescription = description }) {
        val gpu = shader
        if (gpu != null) {
            gpu.setFloatUniform("iResolution", size.width, size.height)
            gpu.setFloatUniform("iTime", t)
            gpu.setFloatUniform("energy", energy)
            gpu.setFloatUniform("rot", rot)
            gpu.setFloatUniform("hue", hue)
            drawRect(ShaderBrush(gpu))
        } else {
            drawFallback(t, energy, voice)
        }
    }
}

private fun DrawScope.drawFallback(t: Float, energy: Float, voice: Float) {
    val c = Offset(size.width / 2, size.height / 2)
    val ring = size.minDimension * 0.28f * (1f + voice * 0.08f)
    val cyan = Color(0xFF4CC2E9)
    val purple = Color(0xFF9C43FE)
    drawCircle(Brush.radialGradient(listOf(purple.copy(0.18f * energy), Color.Transparent), c, ring * 2.1f), ring * 2.1f, c)
    for (i in 0..5) {
        val r = ring * (0.78f + i * 0.07f + sin(t * (0.8f + i * 0.13f) + i) * 0.03f * energy)
        val w = 2.2f + energy * 4f
        drawCircle(cyan.copy(alpha = 0.08f), r, c, blendMode = BlendMode.Plus, style = androidx.compose.ui.graphics.drawscope.Stroke(w * 8f))
        drawCircle(if (i % 2 == 0) cyan else purple, r, c, blendMode = BlendMode.Plus,
            style = androidx.compose.ui.graphics.drawscope.Stroke(w))
    }
    val a = t * 1.15f
    val spot = Offset(c.x + cos(a) * ring, c.y + sin(a) * ring)
    drawCircle(Color(0xFFABF8FF).copy(0.55f), 10f + voice * 16f, spot, blendMode = BlendMode.Plus)
}
