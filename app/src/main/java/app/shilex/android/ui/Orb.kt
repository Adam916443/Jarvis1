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

    Canvas(modifier) {
        val c = center
        val px = density
        val base = size.minDimension / 2 * 0.50f
        val tau = 2f * PI.toFloat()
        val v = voice.coerceIn(0f, 1f)
        val spin = slow * 360f
        val power = 0.55f + 0.45f * energy

        // Soft glow behind everything.
        drawCircle(
            Brush.radialGradient(listOf(accent.copy(alpha = (0.16f + 0.22f * v + 0.10f * energy).coerceAtMost(0.6f)), Color.Transparent), c, base * 2.1f),
            radius = base * 2.1f,
            center = c,
        )

        // Outer ring with a turning scale of tick marks.
        val outer = base * 1.62f
        drawCircle(accent.copy(alpha = 0.28f), outer, c, style = Stroke(1f * px))
        rotate(spin, c) {
            for (i in 0 until TICKS) {
                val a = i * tau / TICKS
                val major = i % 6 == 0
                val from = outer - (if (major) 9f else 4f) * px
                drawLine(
                    accent.copy(alpha = if (major) 0.85f else 0.35f),
                    Offset(c.x + from * cos(a), c.y + from * sin(a)),
                    Offset(c.x + outer * cos(a), c.y + outer * sin(a)),
                    strokeWidth = (if (major) 1.6f else 1f) * px,
                )
            }
        }

        // Three bright arcs turning the other way.
        val seg = base * 1.36f
        rotate(-spin * 2, c) {
            for (k in 0 until 3) {
                drawArc(
                    accent.copy(alpha = power),
                    startAngle = k * 120f + 8f,
                    sweepAngle = 84f,
                    useCenter = false,
                    topLeft = Offset(c.x - seg, c.y - seg),
                    size = Size(seg * 2, seg * 2),
                    style = Stroke(3.5f * px, cap = StrokeCap.Round),
                )
            }
        }

        // A dashed ring.
        val dash = base * 1.14f
        rotate(spin * 3, c) {
            drawCircle(
                accent.copy(alpha = 0.6f),
                dash,
                c,
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
        drawCircle(
            Brush.radialGradient(
                listOf(Ice.copy(alpha = 0.95f * power), accent.copy(alpha = 0.6f * power), Color.Transparent),
                c,
                core * 1.7f,
            ),
            radius = core * 1.7f,
            center = c,
        )
        drawCircle(Ice.copy(alpha = 0.9f * power), core, c, style = Stroke(2f * px))
        triangle.reset()
        for (i in 0 until 3) {
            val a = -PI.toFloat() / 2 + i * tau / 3
            val x = c.x + core * 0.82f * cos(a)
            val y = c.y + core * 0.82f * sin(a)
            if (i == 0) triangle.moveTo(x, y) else triangle.lineTo(x, y)
        }
        triangle.close()
        rotate(-spin, c) { drawPath(triangle, accent.copy(alpha = 0.9f * power), style = Stroke(2f * px)) }

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
