package app.shilex.android.vision

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.Matrix
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.shilex.android.Branding
import app.shilex.android.assistant.Phase
import app.shilex.android.assistant.VoiceSession
import app.shilex.android.ui.Accent
import app.shilex.android.ui.Muted
import app.shilex.android.ui.ShilexTheme
import app.shilex.android.ui.Surface
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Vision: the back camera behind a HUD. About one picture a second goes to the live Gemini
 * session (the user's own key) while this screen is open, and the assistant says what it sees
 * and answers questions about it. Nothing is stored; leaving the screen turns the camera off.
 */
class VisionActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
        )
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent { ShilexTheme { VisionScreen(onClose = ::finish) } }
    }
}

private fun has(context: Context, permission: String) =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

@Composable
private fun VisionScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val session = remember { VoiceSession.get(context) }
    val state by session.state.collectAsStateWithLifecycle()
    var granted by remember { mutableStateOf(has(context, Manifest.permission.CAMERA) && has(context, Manifest.permission.RECORD_AUDIO)) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        granted = result.values.all { it }
    }
    LaunchedEffect(Unit) {
        if (!granted) ask.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
    }
    var sawActive by remember { mutableStateOf(false) }
    LaunchedEffect(granted) {
        if (granted) session.start(firstText = "I just turned the camera on. Look and tell me in one short sentence what you see.")
    }
    LaunchedEffect(state.phase) {
        if (state.phase != Phase.Off) sawActive = true
        if (sawActive && state.phase == Phase.Off) onClose() // "Jarvis stop" or the idle timeout ends Vision too
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (granted) CameraLayer(onFrame = session::sendFrame)
        Reticle()
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("◉ VISION", style = MaterialTheme.typography.titleLarge, color = Accent, modifier = Modifier.weight(1f))
                TextButton(onClick = onClose) { Text("CLOSE", style = MaterialTheme.typography.labelLarge, color = Accent) }
            }
            Box(Modifier.weight(1f))
            val caption = state.lines.lastOrNull { !it.fromUser }?.text
            val status = when {
                !granted -> "Allow the camera and microphone to use Vision."
                !state.hasKey -> "Add your Gemini key in ${Branding.ASSISTANT_NAME} first."
                state.problem != null -> state.problem!!
                state.phase == Phase.Connecting -> "CONNECTING…"
                state.phase == Phase.Speaking -> "SPEAKING"
                else -> "WATCHING · ASK ME ABOUT WHAT YOU SEE"
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Surface.copy(alpha = 0.78f), CutCornerShape(12.dp))
                    .border(1.dp, Accent.copy(alpha = 0.6f), CutCornerShape(12.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(status, style = MaterialTheme.typography.labelMedium, color = Accent)
                if (caption != null) Text(caption, style = MaterialTheme.typography.bodyMedium)
                else Text("Say \"what is this?\", \"read this\" or \"what colour is it?\"", style = MaterialTheme.typography.bodySmall, color = Muted)
            }
        }
    }
}

@Composable
private fun CameraLayer(onFrame: (ByteArray) -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) { onDispose { executor.shutdown() } }
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            val view = PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
            val future = ProcessCameraProvider.getInstance(ctx)
            future.addListener({
                val provider = future.get()
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(view.surfaceProvider) }
                @Suppress("DEPRECATION")
                val analysis = ImageAnalysis.Builder()
                    .setTargetResolution(android.util.Size(640, 480))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                var last = 0L
                analysis.setAnalyzer(executor) { image ->
                    val now = SystemClock.elapsedRealtime()
                    if (now - last >= FRAME_MS) {
                        last = now
                        image.jpeg()?.let(onFrame)
                    }
                    image.close()
                }
                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            }, ContextCompat.getMainExecutor(ctx))
            view
        },
    )
}

/** A small, upright JPEG of what the camera sees: at most 768 px on the long side. */
private fun ImageProxy.jpeg(): ByteArray? = runCatching {
    val bitmap = toBitmap()
    val scale = minOf(1f, 768f / maxOf(bitmap.width, bitmap.height))
    val matrix = Matrix().apply {
        postRotate(imageInfo.rotationDegrees.toFloat())
        if (scale < 1f) postScale(scale, scale)
    }
    val upright = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    ByteArrayOutputStream().also { upright.compress(Bitmap.CompressFormat.JPEG, 65, it) }.toByteArray()
}.getOrNull()

/** Corner brackets, a turning ring in the middle and a scan line over the picture. */
@Composable
private fun Reticle() {
    val transition = rememberInfiniteTransition(label = "vision")
    val scan by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(3200, easing = LinearEasing), RepeatMode.Restart), label = "scan")
    val spin by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(14000, easing = LinearEasing), RepeatMode.Restart), label = "spin")
    val accent = Accent
    Canvas(Modifier.fillMaxSize()) {
        val px = density
        val m = 18f * px
        val len = 34f * px
        val w = 3f * px
        val bracket = accent.copy(alpha = 0.9f)
        fun corner(x: Float, y: Float, dx: Float, dy: Float) {
            drawLine(bracket, Offset(x, y), Offset(x + dx * len, y), w)
            drawLine(bracket, Offset(x, y), Offset(x, y + dy * len), w)
        }
        corner(m, m * 3, 1f, 1f)
        corner(size.width - m, m * 3, -1f, 1f)
        corner(m, size.height - m * 3, 1f, -1f)
        corner(size.width - m, size.height - m * 3, -1f, -1f)

        val c = center
        val r = size.minDimension * 0.17f
        drawCircle(accent.copy(alpha = 0.5f), r, c, style = Stroke(1.5f * px))
        rotate(spin, c) {
            for (i in 0 until 24) {
                val a = i * 2f * PI.toFloat() / 24
                val inner = r + 4f * px
                val outer = r + (if (i % 6 == 0) 16f else 9f) * px
                drawLine(accent.copy(alpha = 0.8f), Offset(c.x + inner * cos(a), c.y + inner * sin(a)), Offset(c.x + outer * cos(a), c.y + outer * sin(a)), 1.5f * px)
            }
        }
        drawLine(accent.copy(alpha = 0.7f), Offset(c.x - 8f * px, c.y), Offset(c.x + 8f * px, c.y), 1.5f * px)
        drawLine(accent.copy(alpha = 0.7f), Offset(c.x, c.y - 8f * px), Offset(c.x, c.y + 8f * px), 1.5f * px)

        val y = size.height * scan
        val band = 60f * px
        drawRect(
            Brush.verticalGradient(listOf(Color.Transparent, accent.copy(alpha = 0.22f), Color.Transparent), startY = y - band, endY = y + band),
            topLeft = Offset(0f, y - band),
            size = androidx.compose.ui.geometry.Size(size.width, band * 2),
        )
        drawLine(accent.copy(alpha = 0.6f), Offset(0f, y), Offset(size.width, y), 1.5f * px)
    }
}

private const val FRAME_MS = 1_000L
