package app.shilex.android.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import app.shilex.android.assistant.Phase
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val Ice = Color(0xFFE6FBFF)

/**
 * The assistant's presence, drawn like JARVIS's HUD: an arc-reactor core inside turning rings
 * and a ring of bars that dance with the voice [level] (0..1). Standby is one still frame (no
 * battery cost); listening turns slowly, thinking adds a chasing arc, speaking moves the most.
 */
@Composable
fun Orb(phase: Phase, modifier: Modifier = Modifier, level: Float = 0f) {
    val accent = Accent
    val energy by animateFloatAsState(
        when (phase) {
            Phase.Off -> 0f
            Phase.Connecting -> 0.35f
            Phase.Listening -> 0.5f
            Phase.Speaking -> 0.9f
        },
        tween(700),
        label = "energy",
    )
    val voice by animateFloatAsState(level, spring(stiffness = Spring.StiffnessLow), label = "voice")
    val slow: Float
    val fast: Float
    if (phase == Phase.Off) {
        slow = 0f
        fast = 0f
    } else {
        val transition = rememberInfiniteTransition(label = "hud")
        slow = transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(CYCLE_MS, easing = LinearEasing), RepeatMode.Restart),
            label = "slow",
        ).value
        fast = transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(PULSE_MS, easing = LinearEasing), RepeatMode.Restart),
            label = "fast",
        ).value
    }
    val triangle = remember { Path() }
    val tilt = LocalTilt.current

    Canvas(
        modifier.graphicsLayer {
            val t = tilt.value
            rotationX = -t.y * 16f
            rotationY = t.x * 16f
            cameraDistance = 14f * density
        },
    ) {
        val t = tilt.value
        val px = density
        val c0 = center
        // Layers sit at different depths: tilt the phone and they slide against each other.
        fun at(depth: Float) = Offset(c0.x - t.x * depth * px, c0.y - t.y * depth * px)
        val c = at(0f)
        val base = size.minDimension / 2 * 0.50f
        val tau = 2f * PI.toFloat()
        val v = voice.coerceIn(0f, 1f)
        val spin = slow * 360f
        val power = 0.55f + 0.45f * energy
        val flicker = 1f - 0.05f * (0.5f + 0.5f * sin(fast * tau * 11f))

        // Soft glow behind everything.
        drawCircle(
            Brush.radialGradient(listOf(accent.copy(alpha = ((0.16f + 0.22f * v + 0.10f * energy) * flicker).coerceAtMost(0.6f)), Color.Transparent), c, base * 2.1f),
            radius = base * 2.1f,
            center = c,
        )

        // Outer ring with a turning scale of tick marks.
        val outer = base * 1.62f
        val co = at(-10f)
        drawCircle(accent.copy(alpha = 0.28f), outer, co, style = Stroke(1f * px))
        rotate(spin, co) {
            for (i in 0 until TICKS) {
                val a = i * tau / TICKS
                val major = i % 6 == 0
                val from = outer - (if (major) 9f else 4f) * px
                drawLine(
                    accent.copy(alpha = if (major) 0.85f else 0.35f),
                    Offset(co.x + from * cos(a), co.y + from * sin(a)),
                    Offset(co.x + outer * cos(a), co.y + outer * sin(a)),
                    strokeWidth = (if (major) 1.6f else 1f) * px,
                )
            }
        }

        // Three bright arcs turning the other way.
        val seg = base * 1.36f
        val cs = at(-6f)
        rotate(-spin * 2, cs) {
            for (k in 0 until 3) {
                drawArc(
                    accent.copy(alpha = power),
                    startAngle = k * 120f + 8f,
                    sweepAngle = 84f,
                    useCenter = false,
                    topLeft = Offset(cs.x - seg, cs.y - seg),
                    size = Size(seg * 2, seg * 2),
                    style = Stroke(3.5f * px, cap = StrokeCap.Round),
                )
            }
        }

        // A dashed ring.
        val dash = base * 1.14f
        val cd = at(-3f)
        rotate(spin * 3, cd) {
            drawCircle(
                accent.copy(alpha = 0.6f),
                dash,
                cd,
                style = Stroke(1.5f * px, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f * px, 7f * px))),
            )
        }

        // Voice bars: short and still in standby, tall and moving with the voice.
        val inner = base * 0.80f
        for (i in 0 until BARS) {
            val a = i * tau / BARS
            val wave = 0.5f + 0.5f * sin(a * 5 + fast * tau * 2)
            val len = 2f * px + base * (0.03f + 0.34f * v + 0.06f * energy) * (0.35f + 0.65f * wave)
            drawLine(
                Ice.copy(alpha = (0.35f + 0.55f * v).coerceIn(0f, 1f)),
                Offset(c.x + inner * cos(a), c.y + inner * sin(a)),
                Offset(c.x + (inner + len) * cos(a), c.y + (inner + len) * sin(a)),
                strokeWidth = 2f * px,
                cap = StrokeCap.Round,
            )
        }

        // The reactor core.
        val core = base * 0.46f * (1 + 0.10f * v)
        val cc = at(5f)
        drawCircle(
            Brush.radialGradient(
                listOf(Ice.copy(alpha = 0.95f * power), accent.copy(alpha = 0.6f * power), Color.Transparent),
                cc,
                core * 1.7f,
            ),
            radius = core * 1.7f,
            center = cc,
        )
        drawCircle(Ice.copy(alpha = 0.9f * power), core, cc, style = Stroke(2f * px))
        triangle.reset()
        for (i in 0 until 3) {
            val a = -PI.toFloat() / 2 + i * tau / 3
            val x = cc.x + core * 0.82f * cos(a)
            val y = cc.y + core * 0.82f * sin(a)
            if (i == 0) triangle.moveTo(x, y) else triangle.lineTo(x, y)
        }
        triangle.close()
        rotate(-spin, cc) { drawPath(triangle, accent.copy(alpha = 0.9f * power), style = Stroke(2f * px)) }


        // Hologram: a projector disc under the orb with a faint beam, and scan lines through it.
        val floorY = c0.y + base * 1.78f
        for (i in 0..2) {
            val r = base * (0.9f + 0.4f * i)
            drawOval(
                accent.copy(alpha = (0.42f - 0.12f * i) * flicker),
                topLeft = Offset(c0.x - r, floorY - r * 0.2f),
                size = Size(r * 2, r * 0.4f),
                style = Stroke(1.5f * px),
            )
        }
        val beam = Path().apply {
            moveTo(c0.x - base * 0.9f, floorY)
            lineTo(c0.x - base * 0.45f, c0.y)
            lineTo(c0.x + base * 0.45f, c0.y)
            lineTo(c0.x + base * 0.9f, floorY)
            close()
        }
        drawPath(beam, Brush.verticalGradient(listOf(Color.Transparent, accent.copy(alpha = 0.10f * flicker)), startY = c0.y, endY = floorY))
        var ly = c0.y - outer
        val lineStep = 4f * px
        while (ly < c0.y + outer) {
            val dy = ly - c0.y
            val half = kotlin.math.sqrt((outer * outer - dy * dy).coerceAtLeast(0f))
            drawLine(Ice.copy(alpha = 0.05f), Offset(c0.x - half, ly), Offset(c0.x + half, ly), 1f)
            ly += lineStep
        }
        val sweepY = c0.y - outer + fast * 2f * outer
        val sweepHalf = kotlin.math.sqrt((outer * outer - (sweepY - c0.y) * (sweepY - c0.y)).coerceAtLeast(0f))
        drawLine(Ice.copy(alpha = 0.30f * flicker), Offset(c0.x - sweepHalf, sweepY), Offset(c0.x + sweepHalf, sweepY), 2f * px)

        // Thinking: an arc chasing round the outside.
        if (phase == Phase.Connecting) {
            val ring = base * 1.5f
            rotate(spin * 8, c) {
                drawArc(
                    Brush.sweepGradient(listOf(Color.Transparent, Ice, Color.Transparent), c),
                    startAngle = 0f,
                    sweepAngle = 140f,
                    useCenter = false,
                    topLeft = Offset(c.x - ring, c.y - ring),
                    size = Size(ring * 2, ring * 2),
                    style = Stroke(3 * px),
                )
            }
        }
    }
}

private const val CYCLE_MS = 12_000
private const val PULSE_MS = 2_400
private const val TICKS = 72
private const val BARS = 56
