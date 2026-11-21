package app.shilex.android.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.shilex.android.memory.MemoryRules
import app.shilex.android.memory.MemoryStore
import app.shilex.android.memory.Note
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

private class Particle(val x: Float, val phase: Float, val loops: Int, val size: Float)

private val STAMP = DateTimeFormatter.ofPattern("d MMM · HH:mm")

/**
 * The memory core. Type "mom's name" → "Heba" and JARVIS knows it for good; JARVIS also saves
 * things on its own while you talk and they show up here live. Tap a node to edit it.
 */
@Composable
fun MemoryScreen() {
    val context = LocalContext.current
    val store = remember { MemoryStore(context) }
    var notes by remember { mutableStateOf(store.notes().sortedByDescending { it.atMillis }) }
    val openedAt = remember { System.currentTimeMillis() }
    DisposableEffect(Unit) {
        val stop = store.observe { notes = store.notes().sortedByDescending { it.atMillis } }
        onDispose { stop() }
    }

    var label by remember { mutableStateOf("") }
    var value by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<Note?>(null) }
    var stored by remember { mutableIntStateOf(0) }
    val flash = remember { Animatable(0f) }
    LaunchedEffect(stored) {
        if (stored > 0) {
            flash.snapTo(1f)
            flash.animateTo(0f, tween(1100))
        }
    }

    fun save() {
        val l = label.trim()
        val v = value.trim()
        if (v.isEmpty()) {
            error = "Type what to remember."
            return
        }
        val text = (if (l.isEmpty()) v else "$l${MemoryRules.ARROW}$v").take(MemoryRules.MAX_LENGTH)
        if (MemoryRules.looksSecret(text)) {
            error = "Not stored: that looks like a password, PIN, code or card number."
            return
        }
        editing?.let { store.remove(it) }
        store.addNote(text)
        label = ""
        value = ""
        error = null
        editing = null
        stored++
    }

    val accent = Accent
    val particles = remember { List(40) { Particle(Random.nextFloat(), Random.nextFloat(), 1 + Random.nextInt(3), 1f + Random.nextFloat() * 2f) } }
    val transition = rememberInfiniteTransition(label = "core")
    val scan by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(7000, easing = LinearEasing), RepeatMode.Restart), label = "scan")
    val drift by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(24000, easing = LinearEasing), RepeatMode.Restart), label = "drift")
    val blink by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "blink")

    Column(Modifier.fillMaxSize().hudFrame(accent)) {
        androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
            // Data motes rising and a scan line sweeping down, behind everything.
            Canvas(Modifier.fillMaxSize()) {
                particles.forEach { p ->
                    val f = (drift * p.loops + p.phase) % 1f
                    val y = size.height * (1f - f)
                    val x = p.x * size.width + sin(f * 2f * PI.toFloat() * 2f) * 8f * density
                    drawCircle(accent.copy(alpha = sin(f * PI.toFloat()) * 0.45f), p.size * density, Offset(x, y))
                }
                val sy = size.height * scan
                val band = 70f * density
                drawRect(
                    Brush.verticalGradient(listOf(Color.Transparent, accent.copy(alpha = 0.16f), Color.Transparent), startY = sy - band, endY = sy + band),
                    topLeft = Offset(0f, sy - band),
                    size = Size(size.width, band * 2),
                )
                drawLine(accent.copy(alpha = 0.45f), Offset(0f, sy), Offset(size.width, sy), 1.dp.toPx())
            }

            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("REMEMBER", style = MaterialTheme.typography.titleLarge, color = accent, modifier = Modifier.weight(1f))
                            Pill("${notes.size} nodes", accent)
                        }
                        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("> MEMORY CORE ONLINE", style = MaterialTheme.typography.labelMedium, color = Muted)
                            Text("█", style = MaterialTheme.typography.labelMedium, color = accent.copy(alpha = blink))
                        }
                        Text(
                            "Write it here, or just say it: \"Hey Jarvis, remember my house key is in the drawer\". " +
                                "${app.shilex.android.Branding.ASSISTANT_NAME} also saves lasting facts by itself while you talk.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Muted,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }

                item {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(accent.copy(alpha = 0.05f + 0.10f * flash.value), MaterialTheme.shapes.medium)
                            .border(1.dp, accent.copy(alpha = 0.3f + 0.7f * flash.value), MaterialTheme.shapes.medium)
                            .padding(14.dp),
                    ) {
                        Text(if (editing == null) "NEW MEMORY NODE" else "EDITING NODE", style = MaterialTheme.typography.titleSmall, color = accent)
                        val fields = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = accent,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedLabelColor = accent,
                            cursorColor = accent,
                        )
                        OutlinedTextField(
                            value = label,
                            onValueChange = { label = it.take(80); error = null },
                            label = { Text("What  (اسم أمي · my mom's name)") },
                            singleLine = true,
                            shape = MaterialTheme.shapes.small,
                            colors = fields,
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        )
                        Text("↓", color = accent, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(
                            value = value,
                            onValueChange = { value = it.take(MemoryRules.MAX_LENGTH); error = null },
                            label = { Text("Remember as  (هبه · Heba)") },
                            shape = MaterialTheme.shapes.small,
                            colors = fields,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Warning, modifier = Modifier.padding(top = 6.dp)) }
                        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Button(
                                onClick = { save() },
                                shape = CutCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = MaterialTheme.colorScheme.onPrimary),
                            ) { Text(if (editing == null) "STORE" else "UPDATE", style = MaterialTheme.typography.labelLarge) }
                            if (editing != null) {
                                TextButton(onClick = { editing = null; label = ""; value = ""; error = null }) { Text("Cancel") }
                            }
                        }
                    }
                }

                if (notes.isEmpty()) {
                    item {
                        Text(
                            "No memories yet. Add one above, or tell ${app.shilex.android.Branding.ASSISTANT_NAME} something about your life.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Muted,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                } else {
                    item { Text("STORED NODES", style = MaterialTheme.typography.titleSmall, color = Muted, modifier = Modifier.padding(top = 6.dp)) }
                }
                itemsIndexed(notes, key = { _, n -> "${n.atMillis}:${n.text}" }) { index, note ->
                    NodeCard(
                        note = note,
                        index = index,
                        fresh = note.atMillis >= openedAt,
                        onEdit = {
                            editing = note
                            val parts = MemoryRules.split(note.text)
                            label = parts?.first.orEmpty()
                            value = parts?.second ?: note.text
                            error = null
                        },
                        onDelete = { store.remove(note) },
                    )
                }
            }
        }
    }
}

@Composable
private fun NodeCard(note: Note, index: Int, fresh: Boolean, onEdit: () -> Unit, onDelete: () -> Unit) {
    val accent = Accent
    val parts = MemoryRules.split(note.text)
    val glow = remember { Animatable(if (fresh) 1f else 0f) }
    LaunchedEffect(Unit) { if (fresh) glow.animateTo(0f, tween(2000)) }
    val enter = remember { MutableTransitionState(false).apply { targetState = true } }
    val delay = index.coerceAtMost(8) * 70
    val outline = MaterialTheme.colorScheme.outlineVariant

    AnimatedVisibility(
        visibleState = enter,
        enter = fadeIn(tween(450, delayMillis = delay)) +
            slideInHorizontally(tween(450, delayMillis = delay)) { -it / 6 } +
            expandVertically(tween(350, delayMillis = delay)),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .background(lerp(Surface.copy(alpha = 0.85f), accent.copy(alpha = 0.25f), glow.value), MaterialTheme.shapes.small)
                .border(1.dp, lerp(outline, accent, glow.value), MaterialTheme.shapes.small)
                .clickable(onClick = onEdit),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.width(3.dp).fillMaxHeight().background(accent.copy(alpha = 0.45f + 0.55f * glow.value))) {}
            Column(Modifier.weight(1f).padding(horizontal = 14.dp, vertical = 10.dp)) {
                if (parts != null) {
                    Text("› ${parts.first}", style = MaterialTheme.typography.labelMedium, color = accent)
                    Text(parts.second, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 2.dp))
                } else {
                    Text(note.text, style = MaterialTheme.typography.titleMedium)
                }
                Text(
                    STAMP.format(Instant.ofEpochMilli(note.atMillis).atZone(ZoneId.systemDefault())).uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = Muted,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = Muted)
            }
        }
    }
}
