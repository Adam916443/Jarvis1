package app.shilex.android.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.shilex.android.Branding
import app.shilex.android.assistant.Line
import app.shilex.android.assistant.Phase
import app.shilex.android.study.StudyLock
import app.shilex.android.assistant.UiState
import app.shilex.android.learn.HabitKeys
import app.shilex.android.learn.Habits
import app.shilex.android.vision.VisionActivity
import androidx.lifecycle.compose.LifecycleResumeEffect
import android.content.Intent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.TextButton
import app.shilex.android.data.Settings as AppSettings
import app.shilex.android.local.ActivityLog
import app.shilex.android.local.LocalCommand
import app.shilex.android.local.LocalPhase
import app.shilex.android.local.MediaKey
import app.shilex.android.task.Task
import app.shilex.android.wake.WakeWordModel

/** What the assistant is doing, in words and a colour, from the session and the local turn. */
private data class Status(val title: String, val hint: String, val badge: String, val orb: Phase, val live: Boolean)

private fun status(state: UiState, local: LocalPhase, wakeOn: Boolean): Status = when {
    local == LocalPhase.Listening -> Status("Listening", "Say a command. Simple ones run on this phone.", "On phone", Phase.Listening, true)
    local == LocalPhase.Doing -> Status("Working", "Doing it on this phone.", "On phone", Phase.Connecting, true)
    local == LocalPhase.Speaking -> Status("Speaking", "Answered on this phone.", "On phone", Phase.Speaking, true)
    state.phase == Phase.Connecting -> Status("Thinking", "Connecting to Gemini…", "Gemini", Phase.Connecting, true)
    state.phase == Phase.Listening -> Status("Listening", "Talk any time, in English, Hindi or Hinglish.", "Gemini", Phase.Listening, true)
    state.phase == Phase.Speaking -> Status("Speaking", "Tap the orb to cut in.", "Gemini", Phase.Speaking, true)
    else -> Status(
        "Standby",
        state.problem ?: if (wakeOn) "Say \"${WakeWordModel.PHRASE}\", or tap the mic." else "Tap the mic to talk.",
        if (wakeOn) "Wake word on" else "Wake word off",
        Phase.Off,
        false,
    )
}

@Composable
fun HomeScreen(
    state: UiState,
    localPhase: LocalPhase,
    level: Float,
    task: Task?,
    activity: List<ActivityLog.Entry>,
    wakeOn: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onInterrupt: () -> Unit,
    onMicDenied: () -> Unit,
    onPermissionAnswered: (Boolean) -> Unit,
    onQuick: (LocalCommand) -> String,
    onSeeActivity: () -> Unit,
    favourites: List<LocalCommand> = emptyList(),
) {
    val context = LocalContext.current
    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) onStart() else onMicDenied()
    }
    // A tool needs Contacts or notifications: show Android's own Allow dialog.
    val askForTool = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission(), onPermissionAnswered)
    LaunchedEffect(state.permissionNeeded) {
        state.permissionNeeded?.let(askForTool::launch)
    }
    val startListening = {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (granted) onStart() else askMic.launch(Manifest.permission.RECORD_AUDIO)
    }
    val now = status(state, localPhase, wakeOn)
    val problem = state.problem != null && !now.live

    Column(Modifier.fillMaxSize().hudFrame(Accent).padding(horizontal = 24.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                Branding.ASSISTANT_NAME.toCharArray().joinToString("."),
                style = MaterialTheme.typography.titleLarge,
                color = Accent,
                modifier = Modifier.weight(1f),
            )
            val studyOn by StudyLock.on.collectAsStateWithLifecycle()
            if (studyOn) {
                Pill("Study lock", Warning)
                androidx.compose.foundation.layout.Spacer(Modifier.width(8.dp))
            }
            Pill(now.badge, if (now.live) Accent else Muted)
        }

        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            val orbSize = minOf(maxWidth, maxHeight, 320.dp)
            Orb(
                phase = now.orb,
                level = level,
                modifier = Modifier
                    .size(orbSize)
                    .semantics { contentDescription = "${Branding.ASSISTANT_NAME}: ${now.title}" }
                    .clickable(remember { MutableInteractionSource() }, indication = null) {
                        when (state.phase) {
                            Phase.Off -> if (localPhase == LocalPhase.Idle) startListening()
                            Phase.Speaking -> onInterrupt()
                            else -> Unit
                        }
                    },
            )
        }

        AnimatedContent(now.title, label = "status") { title ->
            Text(
                title.uppercase(),
                style = MaterialTheme.typography.displaySmall,
                color = Accent,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Text(
            now.hint,
            style = MaterialTheme.typography.bodyMedium,
            color = if (problem) Warning else Muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 16.dp),
        )

        // The task in progress, or one that just finished (2 minutes).
        val recentTask = task?.takeIf { now.live || System.currentTimeMillis() - it.at < TASK_SHOWN_MS }
        recentTask?.let { TaskCard(it, Modifier.padding(bottom = 12.dp)) }

        if (state.phase != Phase.Off && state.lines.isNotEmpty()) {
            Conversation(state.lines, Modifier.fillMaxWidth().weight(1f))
        } else {
            VisionButton()
            Spacer(Modifier.height(10.dp))
            Suggestion(onQuick)
            QuickActions(onQuick, favourites)
            Spacer(Modifier.height(16.dp))
            Recent(activity.take(2), onSeeActivity)
        }

        Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
            val live = now.live
            FilledIconButton(
                onClick = { if (live) onStop() else startListening() },
                modifier = Modifier.size(72.dp).border(1.5.dp, Accent, CircleShape),
                colors = if (live) {
                    IconButtonDefaults.filledIconButtonColors(containerColor = Accent.copy(alpha = 0.12f), contentColor = Accent)
                } else {
                    IconButtonDefaults.filledIconButtonColors(containerColor = Accent.copy(alpha = 0.22f), contentColor = Accent)
                },
            ) {
                Icon(if (live) Icons.Filled.Close else MicIcon, contentDescription = if (live) "Stop" else "Talk", modifier = Modifier.size(30.dp))
            }
        }
    }
}

/** Opens the camera screen where the assistant looks at what you point at. */
@Composable
private fun VisionButton() {
    val context = LocalContext.current
    Box(
        Modifier
            .fillMaxWidth()
            .holoTilt(3f)
            .background(Accent.copy(alpha = 0.10f), MaterialTheme.shapes.small)
            .border(1.dp, Accent.copy(alpha = 0.7f), MaterialTheme.shapes.small)
            .clickable { context.startActivity(Intent(context, VisionActivity::class.java)) }
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text("◉  VISION · POINT THE CAMERA", style = MaterialTheme.typography.labelLarge, color = Accent)
    }
}

/** Something learned from your habits: what you usually do around now. */
@Composable
private fun Suggestion(onQuick: (LocalCommand) -> String) {
    val context = LocalContext.current
    var suggestion by remember { mutableStateOf<LocalCommand?>(null) }
    var answer by remember { mutableStateOf<String?>(null) }
    LifecycleResumeEffect(Unit) {
        suggestion = if (AppSettings(context).learning) Habits.get(context).suggest(java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)) else null
        onPauseOrDispose { }
    }
    val command = suggestion ?: return
    val words = HabitKeys.key(command)?.let { HabitKeys.describe(it) } ?: return
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .holoTilt(3f)
            .background(Accent.copy(alpha = 0.05f), MaterialTheme.shapes.small)
            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
            .padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("YOU USUALLY DO THIS NOW", style = MaterialTheme.typography.labelMedium, color = Muted)
            Text(answer ?: words.replaceFirstChar(Char::uppercase), style = MaterialTheme.typography.titleMedium)
        }
        if (answer == null) {
            TextButton(onClick = { answer = onQuick(command) }) { Text("DO IT", style = MaterialTheme.typography.labelLarge) }
        }
        TextButton(onClick = {
            Habits.get(context).dismiss(command)
            suggestion = null
        }) { Text("✕", color = Muted) }
    }
}

/** One-tap phone actions; they run here, with no network. */
@Composable
private fun QuickActions(onQuick: (LocalCommand) -> String, favourites: List<LocalCommand>) {
    var torchOn by remember { mutableStateOf(false) }
    var answer by remember { mutableStateOf<String?>(null) }
    // Learned favourites first, topped up with the defaults.
    val learned = favourites.mapNotNull { command -> HabitKeys.key(command)?.let { HabitKeys.describe(it).replaceFirstChar(Char::uppercase) to { command } } }
    val defaults = listOf(
        (if (torchOn) "Torch off" else "Torch") to {
            torchOn = !torchOn
            LocalCommand.Torch(torchOn)
        },
        "Battery" to { LocalCommand.Battery },
        "Time" to { LocalCommand.Time },
        "Pause" to { LocalCommand.Media(MediaKey.Pause) },
    )
    val actions = (learned + defaults.filter { d -> learned.none { it.first.startsWith(d.first, ignoreCase = true) } }).take(4)
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            actions.forEach { (label, command) ->
                Box(
                    Modifier
                        .weight(1f)
                        .background(Accent.copy(alpha = 0.06f), MaterialTheme.shapes.small)
                        .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
                        .clickable { answer = onQuick(command()) }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label.uppercase(), style = MaterialTheme.typography.labelMedium, color = Accent, maxLines = 1)
                }
            }
        }
        answer?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Muted, modifier = Modifier.padding(top = 8.dp, start = 4.dp)) }
    }
}

@Composable
private fun Recent(entries: List<ActivityLog.Entry>, onSeeAll: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("RECENT", style = MaterialTheme.typography.titleSmall, color = Muted, modifier = Modifier.weight(1f).padding(start = 4.dp))
        TextButton(onClick = onSeeAll) { Text("See all") }
    }
    if (entries.isEmpty()) {
        Text("Nothing yet. What you ask shows up here.", style = MaterialTheme.typography.bodySmall, color = Muted, modifier = Modifier.padding(start = 4.dp))
    }
    entries.forEach { ActivityRow(it) }
}

@Composable
private fun Conversation(lines: List<Line>, modifier: Modifier) {
    val list = rememberLazyListState()
    LaunchedEffect(lines.size, lines.lastOrNull()?.text) {
        if (lines.isNotEmpty()) list.animateScrollToItem(lines.lastIndex)
    }
    LazyColumn(modifier, state = list, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(lines, key = { it.id }) { line ->
            Box(Modifier.fillMaxWidth(), contentAlignment = if (line.fromUser) Alignment.CenterEnd else Alignment.CenterStart) {
                Text(
                    line.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (line.fromUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .background(if (line.fromUser) Accent else Surface, CutCornerShape(12.dp))
                        .border(1.dp, if (line.fromUser) Accent else MaterialTheme.colorScheme.outlineVariant, CutCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                )
            }
        }
    }
}

private const val TASK_SHOWN_MS = 2 * 60_000L
