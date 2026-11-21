package app.shilex.android.study

import android.app.KeyguardManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.Process
import android.speech.tts.TextToSpeech
import android.view.inputmethod.InputMethodManager
import app.shilex.android.Diag
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Student lock: JARVIS says one line, then stays completely silent, even to questions, until
 * the user says "Hey Jarvis" and then "talk" (or taps the mic). While it is on, JARVIS watches
 * which app is in front (needs Usage access) and, if the user leaves the app they were in when
 * they turned it on, says "Go back to <app>" until they return.
 */
object StudyLock {
    private val _on = MutableStateFlow(false)
    val on: StateFlow<Boolean> = _on.asStateFlow()
    val active: Boolean get() = _on.value

    private val main = Handler(Looper.getMainLooper())
    @Volatile private var app: Context? = null
    private var guarded: String? = null
    private var lastFront: String? = null
    private var since = 0L
    private var awaySince = 0L
    private var lastNag = 0L
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    private val UNLOCK = setOf("talk", "unlock", "speak", "wake")

    /** Brings the lock back after the process was restarted. */
    fun restore(context: Context) {
        app = context.applicationContext
        val prefs = prefs(context)
        if (!prefs.getBoolean(ON, false)) return
        guarded = prefs.getString(PKG, null)
        lastFront = guarded
        since = System.currentTimeMillis()
        _on.value = true
        watch()
    }

    /** Turns the lock on and returns the one line to say. */
    fun start(context: Context): String {
        val ctx = context.applicationContext
        app = ctx
        val allowed = usageAllowed(ctx)
        guarded = if (allowed) frontNow(ctx)?.takeUnless { it == launcher(ctx) } else null
        lastFront = guarded
        since = System.currentTimeMillis()
        awaySince = 0
        lastNag = 0
        prefs(ctx).edit().putBoolean(ON, true).putString(PKG, guarded).apply()
        _on.value = true
        watch()
        Diag.i("study: lock on (guarding ${guarded ?: "nothing"}, usage access=$allowed)")
        return if (allowed) {
            "Student lock on. I'll stay quiet. Say Hey Jarvis, then talk, when you need me."
        } else {
            "Student lock on. I'll stay quiet. To keep you in your app, allow usage access in my settings."
        }
    }

    fun stop(context: Context) {
        if (_on.value) Diag.i("study: lock off")
        _on.value = false
        prefs(context).edit().putBoolean(ON, false).remove(PKG).apply()
        main.removeCallbacks(tick)
        tts?.shutdown()
        tts = null
        ttsReady = false
        guarded = null
        awaySince = 0
    }

    /** True if what was heard is the user asking JARVIS to talk again. */
    fun isUnlock(texts: List<String>): Boolean = texts.any { t -> t.lowercase().split(Regex("[^a-z]+")).any { it in UNLOCK } }

    fun usageAllowed(context: Context): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java) ?: return false
        return ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) ==
            AppOpsManager.MODE_ALLOWED
    }

    private fun prefs(context: Context) = context.getSharedPreferences("study_lock", Context.MODE_PRIVATE)

    private fun watch() {
        main.removeCallbacks(tick)
        main.postDelayed(tick, POLL_MS)
    }

    private val tick = object : Runnable {
        override fun run() {
            if (!_on.value) return
            runCatching { check() }.onFailure { Diag.w("study: check failed (${it.javaClass.simpleName})") }
            main.postDelayed(this, POLL_MS)
        }
    }

    private fun check() {
        val ctx = app ?: return
        val target = guarded ?: return
        if (!usageAllowed(ctx)) return
        val now = System.currentTimeMillis()
        val usage = ctx.getSystemService(UsageStatsManager::class.java) ?: return
        lastFront = replay(usage, since - 1_000, now, lastFront)
        since = now
        val awake = ctx.getSystemService(PowerManager::class.java).isInteractive &&
            ctx.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked != true
        val front = lastFront
        val away = awake && front != null && front != target && front !in ignored(ctx, target)
        if (!away) {
            awaySince = 0
            lastNag = 0
            return
        }
        if (awaySince == 0L) awaySince = now
        if (now - awaySince >= GRACE_MS && now - lastNag >= NAG_MS) {
            lastNag = now
            Diag.i("study: user left the app, reminding")
            say(ctx, "Go back to ${label(ctx, target)}.")
        }
    }

    /** The app in front now, from the usage events of the last few hours. */
    private fun frontNow(ctx: Context): String? {
        val usage = ctx.getSystemService(UsageStatsManager::class.java) ?: return null
        val now = System.currentTimeMillis()
        return replay(usage, now - 3 * 3_600_000L, now, null)
    }

    /** Applies the usage events in the range on top of [start]: an app coming to the front replaces it, going away clears it. */
    private fun replay(usage: UsageStatsManager, from: Long, to: Long, start: String?): String? {
        var front = start
        val events = usage.queryEvents(from, to)
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            when (event.eventType) {
                RESUMED -> front = event.packageName
                PAUSED -> if (event.packageName == front) front = null
            }
        }
        return front
    }

    private fun ignored(ctx: Context, target: String): Set<String> {
        val keyboards = runCatching {
            ctx.getSystemService(InputMethodManager::class.java).enabledInputMethodList.map { it.packageName }
        }.getOrDefault(emptyList())
        return buildSet {
            add("com.android.systemui")
            if (target != ctx.packageName) add(ctx.packageName)
            addAll(keyboards)
        }
    }

    private fun launcher(ctx: Context): String? =
        ctx.packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)?.activityInfo?.packageName

    private fun label(ctx: Context, pkg: String): String = runCatching {
        ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault("your app")

    private fun say(ctx: Context, line: String) {
        val voice = tts
        if (voice != null) {
            if (ttsReady) voice.speak(line, TextToSpeech.QUEUE_FLUSH, null, "study")
            return
        }
        lateinit var created: TextToSpeech
        created = TextToSpeech(ctx) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (!ttsReady) return@TextToSpeech
            created.language = Locale("en", "IN")
            created.setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build(),
            )
            created.speak(line, TextToSpeech.QUEUE_FLUSH, null, "study")
        }
        tts = created
    }

    private const val ON = "on"
    private const val PKG = "pkg"
    private const val RESUMED = 1 // UsageEvents.Event.ACTIVITY_RESUMED / MOVE_TO_FOREGROUND
    private const val PAUSED = 2 // ACTIVITY_PAUSED / MOVE_TO_BACKGROUND
    private const val POLL_MS = 2_000L
    private const val GRACE_MS = 4_000L // a quick glance at another app is not leaving
    private const val NAG_MS = 20_000L
}
